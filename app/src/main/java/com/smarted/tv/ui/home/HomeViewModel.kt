package com.smarted.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.VodItem
import com.smarted.tv.core.model.VodKind
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: ContentRepository) : ViewModel() {
    data class Ui(
        val loaded: Boolean = false,
        val featured: List<VodItem> = emptyList(),
        val channelCount: Int = 0,
        val movieCount: Int = 0,
        val seriesCount: Int = 0,
        val top10: List<VodItem> = emptyList(),
        val newMovies: List<VodItem> = emptyList(),
        val newSeries: List<VodItem> = emptyList(),
        val topSeries: List<VodItem> = emptyList(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    val favoriteCount = repo.favoriteIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        reload()
        viewModelScope.launch { repo.revision.collect { reload() } }
    }

    private fun reload() = viewModelScope.launch {
        runCatching {
            val movies = repo.vod(VodKind.MOVIE)
            val series = repo.vod(VodKind.SERIES)
            val channels = repo.channels()
            val byRating = compareByDescending<VodItem> { it.rating ?: 0.0 }
            _ui.value = Ui(
                loaded = true,
                featured = movies.sortedWith(byRating).take(3) + series.sortedWith(byRating).take(3),
                channelCount = channels.size, movieCount = movies.size, seriesCount = series.size,
                top10 = movies.sortedWith(byRating).take(10),
                newMovies = movies.sortedByDescending { it.addedAtMs }.take(20),
                newSeries = series.sortedByDescending { it.addedAtMs }.take(20),
                topSeries = series.sortedWith(byRating).take(20),
            )
        }
    }
}
