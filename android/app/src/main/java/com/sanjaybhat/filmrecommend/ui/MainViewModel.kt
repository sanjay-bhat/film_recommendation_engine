package com.sanjaybhat.filmrecommend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanjaybhat.filmrecommend.RecommendationEngine
import com.sanjaybhat.filmrecommend.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val query: String = "",
    val suggestions: List<Pair<Int, String>> = emptyList(),
    val selectedMovie: String = "",
    val recommendations: List<Recommendation> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = RecommendationEngine(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.load()
                _state.value = _state.value.copy(loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Failed to load dataset: ${e.message}")
            }
        }
    }

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)
        if (query.length >= 2) {
            viewModelScope.launch(Dispatchers.IO) {
                val results = engine.searchTitles(query)
                _state.value = _state.value.copy(suggestions = results)
            }
        } else {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    fun onMovieSelected(index: Int, title: String) {
        _state.value = _state.value.copy(
            query = title,
            selectedMovie = title,
            suggestions = emptyList(),
            recommendations = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recs = engine.recommend(index)
                _state.value = _state.value.copy(recommendations = recs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Recommendation failed: ${e.message}")
            }
        }
    }

    fun clearSelection() {
        _state.value = UiState(loading = false)
    }
}
