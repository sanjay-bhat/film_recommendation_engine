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
import java.util.Calendar

data class OtdItem(val title: String, val year: String, val posterPath: String?)

data class UiState(
    val loading: Boolean = true,
    val loadingMessage: String = "Connecting...",
    val query: String = "",
    val suggestions: List<Pair<Int, String>> = emptyList(),
    val selectedMovie: String = "",
    val recommendations: List<Recommendation> = emptyList(),
    val filteredRecommendations: List<Recommendation> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val industries: List<String> = emptyList(),
    val selectedIndustries: Set<String> = emptySet(),
    val otdMovies: List<OtdItem> = emptyList(),
    val otdDismissed: Boolean = false,
    val error: String? = null
)

private val INDUSTRY_MAP = mapOf(
    "en" to "Hollywood", "hi" to "Bollywood", "ta" to "Kollywood", "te" to "Tollywood",
    "ja" to "Japanese", "ko" to "Korean", "fr" to "French", "de" to "German",
    "es" to "Spanish", "zh" to "Chinese", "it" to "Italian", "pt" to "Portuguese",
    "ml" to "Malayalam", "bn" to "Bengali", "ru" to "Russian", "th" to "Thai",
    "tr" to "Turkish", "pl" to "Polish", "nl" to "Dutch", "sv" to "Swedish",
    "da" to "Danish", "no" to "Norwegian", "fi" to "Finnish", "id" to "Indonesian",
    "ar" to "Arabic", "he" to "Hebrew", "uk" to "Ukrainian", "cs" to "Czech",
    "ro" to "Romanian", "hu" to "Hungarian", "cn" to "Chinese", "is" to "Icelandic",
    "af" to "Afrikaans", "ca" to "Catalan", "el" to "Greek"
)

fun getIndustryName(lang: String): String {
    return INDUSTRY_MAP[lang] ?: lang.replaceFirstChar { it.uppercase() }.ifEmpty { "Other" }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = RecommendationEngine(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var useSupabase = false
    private var allTitles: List<String> = emptyList()
    private var allRecs: List<Recommendation> = emptyList()

    private var searchJob: Job? = null
    private val _searchHistory = mutableListOf<String>()

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(loadingMessage = "Connecting...")
                val titles = SupabaseClient.fetchAllTitles()
                if (titles.isNotEmpty()) {
                    allTitles = titles
                    useSupabase = true
                    Log.i(TAG, "Loaded ${titles.size} titles from Supabase")
                    _state.value = _state.value.copy(loading = false)
                    loadOnThisDay()
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Supabase load failed, falling back to offline: ${e.message}")
            }

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

    private fun loadOnThisDay() {
        if (!useSupabase) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val movies = SupabaseClient.moviesOnThisDay(month, day)
                _state.value = _state.value.copy(
                    otdMovies = movies.map { OtdItem(it.title, it.year, it.posterPath) }
                )
            } catch (e: Exception) {
                Log.w(TAG, "On This Day fetch failed: ${e.message}")
            }
        }
    }

    fun dismissOtd() {
        _state.value = _state.value.copy(otdDismissed = true)
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
            filteredRecommendations = emptyList(),
            searchHistory = _searchHistory.toList(),
            industries = emptyList(),
            selectedIndustries = emptySet()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recs = if (useSupabase) {
                    getSupabaseRecommendations(title) ?: getOfflineRecommendations(index, title)
                } else {
                    getOfflineRecommendations(index, title)
                }
                allRecs = recs
                val industries = recs.map { getIndustryName(it.originalLanguage) }
                    .distinct()
                    .sortedByDescending { name -> recs.count { getIndustryName(it.originalLanguage) == name } }
                val allIndustrySet = industries.toSet()

                _state.value = _state.value.copy(
                    recommendations = recs,
                    filteredRecommendations = recs,
                    industries = industries,
                    selectedIndustries = allIndustrySet
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Recommendation failed: ${e.message}")
            }
        }
    }

    fun toggleIndustry(industry: String) {
        val current = _state.value.selectedIndustries.toMutableSet()
        if (current.contains(industry)) {
            current.remove(industry)
        } else {
            current.add(industry)
        }
        if (current.isEmpty()) return
        val filtered = allRecs.filter { current.contains(getIndustryName(it.originalLanguage)) }
        _state.value = _state.value.copy(
            selectedIndustries = current,
            filteredRecommendations = filtered.ifEmpty { allRecs }
        )
    }

    fun selectAllIndustries() {
        val allSet = _state.value.industries.toSet()
        _state.value = _state.value.copy(
            selectedIndustries = allSet,
            filteredRecommendations = allRecs
        )
    }

    private fun getSupabaseRecommendations(title: String): List<Recommendation>? {
        return try {
            val recs = SupabaseClient.getRecommendations(title)
            if (recs.isNotEmpty()) recs else null
        } catch (e: Exception) {
            Log.w(TAG, "Supabase recommendations failed for '$title': ${e.message}")
            null
        }
    }

    private fun getOfflineRecommendations(index: Int, title: String): List<Recommendation> {
        if (!engine.isLoaded()) {
            engine.load()
        }
        return if (index >= 0) {
            engine.recommend(index)
        } else {
            engine.recommendByTitle(title)
        }
    }

    fun clearSelection() {
        _state.value = UiState(
            loading = false,
            searchHistory = _searchHistory.toList(),
            otdMovies = _state.value.otdMovies,
            otdDismissed = _state.value.otdDismissed
        )
    }
}
