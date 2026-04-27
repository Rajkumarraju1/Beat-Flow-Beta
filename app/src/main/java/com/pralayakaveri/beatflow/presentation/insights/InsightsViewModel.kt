package com.pralayakaveri.beatflow.presentation.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsState(
    val topGenres: Map<String, Int> = emptyMap(),
    val topArtists: Map<String, Int> = emptyMap(),
    val forgottenSongs: List<Song> = emptyList(),
    val hiddenGems: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    init {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            val allSongs = musicRepository.getSongs()
            val topPlayed = musicRepository.getTopPlayedSongs(100)
            
            val genreCounts = topPlayed.groupBy { it.genre ?: "Unknown" }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .toMap()

            val artistCounts = topPlayed.groupBy { it.artist }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .toMap()

            val forgotten = musicRepository.getForgottenSongs(10)
            val gems = musicRepository.getHiddenGems(10)

            _state.value = InsightsState(
                topGenres = genreCounts,
                topArtists = artistCounts,
                forgottenSongs = forgotten,
                hiddenGems = gems,
                isLoading = false
            )
        }
    }
}
