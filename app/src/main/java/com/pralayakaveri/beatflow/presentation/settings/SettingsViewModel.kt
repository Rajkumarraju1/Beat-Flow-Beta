package com.pralayakaveri.beatflow.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import com.pralayakaveri.beatflow.domain.util.FilterPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val filterPreferences: StateFlow<FilterPreferences?> = musicRepository.getFilterPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateMinDurationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            musicRepository.updateMinDurationEnabled(enabled)
        }
    }

    fun updateMinSizeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            musicRepository.updateMinSizeEnabled(enabled)
        }
    }

    fun forceRescan() {
        viewModelScope.launch {
            musicRepository.forceRescan()
        }
    }
}
