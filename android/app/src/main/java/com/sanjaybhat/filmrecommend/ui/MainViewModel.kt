package com.sanjaybhat.filmrecommend.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanjaybhat.filmrecommend.RecommendationEngine
import com.sanjaybhat.filmrecommend.model.Recommendation
import com.sanjaybhat.filmrecommend.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val loadingMessage: String = "Connecting...",
    val query: String = "",
    val suggestions: List<Pair<Int, String>> = emptyList(),
    val selectedMovie: String = "",
    val recommendations: List<Recommendation> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = RecommendationEngine(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Whether we successfully loaded titles from Supabase and should use it for recommendations. */
    private var useSupabase = false

    /** All movie titles, sourced from either Supabase or the on-device engine. */
    private var allTitles: List<String> = emptyList()

    private var searchJob: Job? = null
    private val _searchHistory = mutableListOf<String>()

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Try Supabase first
            try {
                _state.value = _state.value.copy(loadingMessage = "Connecting...")
                val titles = SupabaseClient.fetchAllTitles()
                if (titles.isNotEmpty()) {
                    allTitles = titles
                    useSupabase = true
                    Log.i(TAG, "Loaded ${titles.size} titles from Supabase")
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Supabase load failed, falling back to offline: ${e.message}")
            }

            // Fall back to on-device engine
            try {
                _state.value = _state.value.copy(loadingMessage = "Loading 4,803 movies...")
                engine.load()
                allTitles = engine.allTitles()
                useSupabase = false
                _state.value = _state.value.copy(loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Failed to load dataset: ${e.message}"
                )
            }
        }
    }

    private fun sanitizeQuery(input: String): String {
        return input.replace(Regex("[\\x00-\\x1f\\x7f]"), "").take(100)
    }

    fun onQueryChanged(query: String) {
        val clean = sanitizeQuery(query)
        _state.value = _state.value.copy(query = clean)
        searchJob?.cancel()
        if (clean.length >= 2) {
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300)
                val lower = clean.lowercase()
                val results = allTitles
                    .filter { it.lowercase().contains(lower) }
                    .take(20)
                    .map { title ->
                        val index = if (useSupabase) -1
                            else allTitles.indexOf(title)
                        index to title
                    }
                _state.value = _state.value.copy(suggestions = results)
            }
        } else {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    fun surpriseMe() {
        if (allTitles.isEmpty()) return
        val pick = allTitles.random()
        val index = if (useSupabase) -1 else allTitles.indexOf(pick)
        onMovieSelected(index, pick)
    }

    fun onMovieSelected(index: Int, title: String) {
        _searchHistory.remove(title)
        _searchHistory.add(0, title)
        if (_searchHistory.size > 8) _searchHistory.removeAt(_searchHistory.lastIndex)

        _state.value = _state.value.copy(
            query = title,
            selectedMovie = title,
            suggestions = emptyList(),
            recommendations = emptyList(),
            searchHistory = _searchHistory.toList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recs = if (useSupabase) {
                    getSupabaseRecommendations(title) ?: getOfflineRecommendations(index, title)
                } else {
                    getOfflineRecommendations(index, title)
                }
                _state.value = _state.value.copy(recommendations = recs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Recommendation failed: ${e.message}")
            }
        }
    }

    /**
     * Try to get recommendations from Supabase. Returns null if the call fails or returns empty,
     * so the caller can fall back to the on-device engine.
     */
    private fun getSupabaseRecommendations(title: String): List<Recommendation>? {
        return try {
            val recs = SupabaseClient.getRecommendations(title)
            if (recs.isNotEmpty()) recs else null
        } catch (e: Exception) {
            Log.w(TAG, "Supabase recommendations failed for '$title': ${e.message}")
            null
        }
    }

    /**
     * Get recommendations from the on-device KNN engine. Loads the engine if not already loaded.
     */
    private fun getOfflineRecommendations(index: Int, title: String): List<Recommendation> {
        if (!engine.isLoaded()) {
            engine.load()
        }
        // If the index is valid (came from the engine's own list), use it directly.
        // Otherwise (Supabase title with index -1), look up by title.
        return if (index >= 0) {
            engine.recommend(index)
        } else {
            engine.recommendByTitle(title)
        }
    }

    fun clearSelection() {
        _state.value = UiState(loading = false)
    }
}
