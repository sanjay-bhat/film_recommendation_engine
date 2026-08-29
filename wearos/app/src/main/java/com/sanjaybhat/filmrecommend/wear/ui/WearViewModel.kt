package com.sanjaybhat.filmrecommend.wear.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanjaybhat.filmrecommend.wear.model.Recommendation
import com.sanjaybhat.filmrecommend.wear.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class WearViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentMovie = MutableStateFlow<Recommendation?>(null)
    val currentMovie: StateFlow<Recommendation?> = _currentMovie

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _otdMovie = MutableStateFlow<SupabaseClient.OtdMovie?>(null)
    val otdMovie: StateFlow<SupabaseClient.OtdMovie?> = _otdMovie

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var allTitles: List<String> = emptyList()

    // Track last 5 genres shown to promote diversity across surprise picks
    private val recentGenres = mutableListOf<String>()

    // Pre-fetched batch of recommendations to allow swiping without network waits
    private var batch = mutableListOf<Recommendation>()
    private var batchIndex = 0

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val titles = SupabaseClient.fetchAllTitles()
                if (titles.isNotEmpty()) {
                    allTitles = titles
                    _isLoading.value = false
                    loadOnThisDay()
                }
            } catch (e: Exception) {
                Log.w("WearVM", "Supabase load failed: ${e.message}")
                _isLoading.value = false
                _error.value = "Failed to connect"
            }
        }
    }

    private fun loadOnThisDay() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val movies = SupabaseClient.moviesOnThisDay(
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                if (movies.isNotEmpty()) {
                    // Pick one random OTD movie to show on the small screen
                    _otdMovie.value = movies.random()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Pick a random title, fetch its recommendations, and populate the batch.
     * Avoids titles whose language matches the last 5 genres shown for diversity.
     */
    fun surpriseMe() {
        if (allTitles.isEmpty()) return
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pick a random seed title
                val seedTitle = allTitles.random()
                val recs = SupabaseClient.getRecommendations(seedTitle)

                if (recs.isEmpty()) {
                    // Retry once with a different title if no results
                    val fallback = allTitles.random()
                    val fallbackRecs = SupabaseClient.getRecommendations(fallback)
                    if (fallbackRecs.isEmpty()) {
                        _isLoading.value = false
                        _error.value = "No recommendations found"
                        return@launch
                    }
                    populateBatch(fallbackRecs)
                } else {
                    populateBatch(recs)
                }

                _isLoading.value = false
            } catch (e: Exception) {
                Log.w("WearVM", "Surprise Me failed: ${e.message}")
                _isLoading.value = false
                _error.value = "Network error"
            }
        }
    }

    private fun populateBatch(recs: List<Recommendation>) {
        // Filter out movies whose language was recently shown to keep things diverse
        val diverse = recs.filter { it.originalLanguage !in recentGenres }
        val finalList = if (diverse.isNotEmpty()) diverse else recs

        batch.clear()
        batch.addAll(finalList.take(20))
        batchIndex = 0

        if (batch.isNotEmpty()) {
            _currentMovie.value = batch[0]
            trackGenre(batch[0].originalLanguage)
        }
    }

    /** Advance to the next movie in the pre-fetched batch. */
    fun nextMovie() {
        if (batch.isEmpty()) return

        batchIndex++
        if (batchIndex >= batch.size) {
            // Batch exhausted — fetch a new surprise batch
            surpriseMe()
            return
        }

        _currentMovie.value = batch[batchIndex]
        trackGenre(batch[batchIndex].originalLanguage)
    }

    /** Go back to the previous movie in the batch. */
    fun previousMovie() {
        if (batch.isEmpty() || batchIndex <= 0) return

        batchIndex--
        _currentMovie.value = batch[batchIndex]
    }

    private fun trackGenre(language: String) {
        recentGenres.add(language)
        if (recentGenres.size > 5) {
            recentGenres.removeAt(0)
        }
    }
}
