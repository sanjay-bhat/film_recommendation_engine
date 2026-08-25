package com.sanjaybhat.filmrecommend.tv.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanjaybhat.filmrecommend.tv.model.Recommendation
import com.sanjaybhat.filmrecommend.tv.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class OtdItem(val title: String, val year: String, val posterPath: String?)

data class SubLevel(
    val fromTitle: String,
    val allRecs: List<Recommendation>,
    val filteredRecs: List<Recommendation>,
    val industries: List<String>,
    val selectedIndustries: Set<String>
)

data class UiState(
    val loading: Boolean = true,
    val query: String = "",
    val suggestions: List<Pair<Int, String>> = emptyList(),
    val selectedMovie: String = "",
    val recommendations: List<Recommendation> = emptyList(),
    val filteredRecommendations: List<Recommendation> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val industries: List<String> = emptyList(),
    val selectedIndustries: Set<String> = emptySet(),
    val subLevels: List<SubLevel> = emptyList(),
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

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var allTitles: List<String> = emptyList()
    private var allRecs: List<Recommendation> = emptyList()
    private var searchJob: Job? = null
    private val _searchHistory = mutableListOf<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val titles = SupabaseClient.fetchAllTitles()
                if (titles.isNotEmpty()) {
                    allTitles = titles
                    _state.value = _state.value.copy(loading = false)
                    loadOnThisDay()
                }
            } catch (e: Exception) {
                Log.w("TVVM", "Supabase load failed: ${e.message}")
                _state.value = _state.value.copy(loading = false, error = "Failed to connect")
            }
        }
    }

    private fun loadOnThisDay() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val movies = SupabaseClient.moviesOnThisDay(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                _state.value = _state.value.copy(otdMovies = movies.map { OtdItem(it.title, it.year, it.posterPath) })
            } catch (_: Exception) {}
        }
    }

    fun dismissOtd() { _state.value = _state.value.copy(otdDismissed = true) }

    fun onQueryChanged(query: String) {
        val clean = query.replace(Regex("[\\x00-\\x1f\\x7f]"), "").take(100)
        _state.value = _state.value.copy(query = clean)
        searchJob?.cancel()
        if (clean.length >= 2) {
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300)
                val lower = clean.lowercase()
                val results = allTitles.filter { it.lowercase().contains(lower) }.take(20).map { -1 to it }
                _state.value = _state.value.copy(suggestions = results)
            }
        } else {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    fun surpriseMe() {
        if (allTitles.isEmpty()) return
        onMovieSelected(-1, allTitles.random())
    }

    fun onMovieSelected(index: Int, title: String) {
        _searchHistory.remove(title)
        _searchHistory.add(0, title)
        if (_searchHistory.size > 8) _searchHistory.removeAt(_searchHistory.lastIndex)

        _state.value = _state.value.copy(
            query = title, selectedMovie = title, suggestions = emptyList(),
            recommendations = emptyList(), filteredRecommendations = emptyList(),
            searchHistory = _searchHistory.toList(), industries = emptyList(),
            selectedIndustries = emptySet(), subLevels = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recs = SupabaseClient.getRecommendations(title)
                allRecs = recs
                val industries = recs.map { getIndustryName(it.originalLanguage) }.distinct()
                _state.value = _state.value.copy(
                    recommendations = recs, filteredRecommendations = recs,
                    industries = industries, selectedIndustries = industries.toSet()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Recommendation failed: ${e.message}")
            }
        }
    }

    fun toggleIndustry(industry: String) {
        val current = _state.value.selectedIndustries.toMutableSet()
        if (current.contains(industry)) current.remove(industry) else current.add(industry)
        if (current.isEmpty()) return
        val filtered = allRecs.filter { current.contains(getIndustryName(it.originalLanguage)) }
        _state.value = _state.value.copy(selectedIndustries = current, filteredRecommendations = filtered.ifEmpty { allRecs })
    }

    fun selectAllIndustries() {
        _state.value = _state.value.copy(selectedIndustries = _state.value.industries.toSet(), filteredRecommendations = allRecs)
    }

    fun drillInto(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recs = SupabaseClient.getRecommendations(title)
                if (recs.isEmpty()) return@launch
                val industries = recs.map { getIndustryName(it.originalLanguage) }.distinct()
                val level = SubLevel(title, recs, recs, industries, industries.toSet())
                _state.value = _state.value.copy(subLevels = _state.value.subLevels + level)
            } catch (_: Exception) {}
        }
    }

    fun exitSubLevels() { _state.value = _state.value.copy(subLevels = emptyList()) }

    fun clearSelection() {
        _state.value = UiState(
            loading = false, searchHistory = _searchHistory.toList(),
            otdMovies = _state.value.otdMovies, otdDismissed = _state.value.otdDismissed
        )
    }
}
