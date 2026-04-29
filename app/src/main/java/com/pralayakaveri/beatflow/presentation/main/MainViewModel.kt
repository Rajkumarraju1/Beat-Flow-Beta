package com.pralayakaveri.beatflow.presentation.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.beatflow.data.local.LibraryPreferencesManager
import com.pralayakaveri.beatflow.domain.model.LyricLine
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import com.pralayakaveri.beatflow.domain.util.LyricsParser
import com.pralayakaveri.beatflow.service.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val musicController: MusicController,
    private val musicRepository: MusicRepository,
    private val themeManager: com.pralayakaveri.beatflow.presentation.theme.ThemeManager,
    private val syncManager: com.pralayakaveri.beatflow.domain.util.LyricsSyncManager,
    private val libraryPreferencesManager: LibraryPreferencesManager
) : ViewModel() {

    private val visualizerHelper = com.pralayakaveri.beatflow.domain.util.AudioVisualizerHelper()
    private var positionUpdateJob: Job? = null

    // Gated UI visibility to save battery
    private val _isUiVisible = MutableStateFlow(false)
    val isUiVisible: StateFlow<Boolean> = _isUiVisible.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _lyricsMap = MutableStateFlow<Map<Long, List<LyricLine>>>(emptyMap())
    val lyricsMap: StateFlow<Map<Long, List<LyricLine>>> = _lyricsMap.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    val isPlaying = musicController.isPlaying
    val currentSong = musicController.currentSong

    // Core Collections
    private val _selectedCollectionSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedCollectionSongs: StateFlow<List<Song>> = _selectedCollectionSongs.asStateFlow()

    private val _selectedCollectionTitle = MutableStateFlow("")
    val selectedCollectionTitle: StateFlow<String> = _selectedCollectionTitle.asStateFlow()

    private val _selectedCollectionType = MutableStateFlow("")
    val selectedCollectionType: StateFlow<String> = _selectedCollectionType.asStateFlow()

    // Preferences
    val currentTheme = themeManager.themeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.pralayakaveri.beatflow.presentation.theme.ThemeType.DYNAMIC
    )

    // Lyrics & Offsets
    private val _userOffsetMs = MutableStateFlow(0L)
    val userOffsetMs: StateFlow<Long> = _userOffsetMs.asStateFlow()

    private val _autoOffsetMs = MutableStateFlow(0L)
    val autoOffsetMs: StateFlow<Long> = _autoOffsetMs.asStateFlow()

    private val _runningOffsetMs = MutableStateFlow(0L)
    val runningOffsetMs: StateFlow<Long> = _runningOffsetMs.asStateFlow()

    private val _isVocalPresent = MutableStateFlow(false)
    val isVocalPresent: StateFlow<Boolean> = _isVocalPresent.asStateFlow()

    val lyrics: StateFlow<List<LyricLine>> = combine(currentSong, _lyricsMap) { song, map ->
        song?.let { map[it.id] } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Data Flows from Repository
    val favoriteIds: StateFlow<Set<Long>> = musicRepository.getFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favorites: StateFlow<List<Song>> = musicRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistImages: StateFlow<Map<String, String>> = musicRepository.getArtistImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val customArtworks: StateFlow<Map<Long, String>> = musicRepository.getAllCustomArtworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val playlists: StateFlow<List<com.pralayakaveri.beatflow.domain.model.Playlist>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Passthroughs from MusicController
    val shuffleModeEnabled = musicController.shuffleModeEnabled
    val repeatMode = musicController.repeatMode
    val sleepTimerTimeRemaining: StateFlow<Long?> = musicController.sleepTimerTimeRemaining

    init {
        viewModelScope.launch {
            currentSong.collect { song ->
                handleSongChange(song)
            }
        }
    }

    private suspend fun handleSongChange(song: Song?) {
        if (song == null) {
            _lyricsMap.value = emptyMap()
            resetOffsets()
            return
        }

        resetOffsets()
        syncManager.getOffset(song.id).firstOrNull()?.let {
            _userOffsetMs.value = it
        }
        fetchLyrics(song)
    }

    private fun resetOffsets() {
        _userOffsetMs.value = 0L
        _autoOffsetMs.value = 0L
        _runningOffsetMs.value = 0L
        _isVocalPresent.value = false
    }

    fun setUiVisibility(isVisible: Boolean) {
        if (_isUiVisible.value == isVisible) return
        _isUiVisible.value = isVisible
        if (isVisible) startPositionUpdates() else stopPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                if (musicController.isPlaying.value && _isUiVisible.value) {
                    _currentPosition.value = musicController.getCurrentPosition()
                }
                delay(16L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun startAutoCalibration() {
        val sessionId = musicController.getAudioSessionId()
        if (sessionId == 0) return
        
        var firstCalibrationDone = false
        var lastVocalTimestamp = 0L

        visualizerHelper.startMonitoring(
            audioSessionId = sessionId, 
            continuous = true,
            onVocalOnset = {
                if (!_isUiVisible.value) return@startMonitoring
                val lyricsList = lyrics.value
                val detectedPos = musicController.getCurrentPosition()
                
                if (!firstCalibrationDone) {
                    val firstLyricTime = lyricsList.firstOrNull { it.startTimeMs > 1000 }?.startTimeMs 
                        ?: lyricsList.firstOrNull()?.startTimeMs ?: 0L
                    val offset = detectedPos - firstLyricTime
                    _autoOffsetMs.value = offset.coerceIn(-5000L, 5000L)
                    firstCalibrationDone = true
                } else {
                    val closestLyric = lyricsList.minByOrNull { Math.abs(it.startTimeMs - detectedPos) }
                    if (closestLyric != null && Math.abs(closestLyric.startTimeMs - detectedPos) < 500) {
                        val error = closestLyric.startTimeMs - detectedPos
                        val adjustment = (error.toFloat() * 0.1f).toLong().coerceIn(-100L, 100L)
                        if (Math.abs(adjustment) > 5) _runningOffsetMs.value += adjustment
                    }
                }
            },
            onFrequencyData = { midBandEnergy ->
                if (!_isUiVisible.value) {
                    _isVocalPresent.value = false
                    return@startMonitoring
                }
                if (midBandEnergy > 5.0f) {
                    lastVocalTimestamp = System.currentTimeMillis()
                    _isVocalPresent.value = true
                } else if (System.currentTimeMillis() - lastVocalTimestamp > 250) {
                    _isVocalPresent.value = false
                }
            }
        )
    }

    private fun fetchLyrics(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localContent = getLocalLyrics(song)
                val parsedLyrics = if (localContent != null) {
                    if (com.pralayakaveri.beatflow.domain.util.LrcParser.isLrcFormat(localContent)) {
                        com.pralayakaveri.beatflow.domain.util.LrcParser.parseLrcContent(localContent)
                    } else {
                        com.pralayakaveri.beatflow.domain.util.LrcParser.parsePlainText(localContent, song.duration)
                    }
                } else {
                    LyricsParser.getLyricsForSong(song.uri)
                }
                _lyricsMap.value = _lyricsMap.value + (song.id to parsedLyrics)
                if (parsedLyrics.any { it.startTimeMs > 0 }) {
                    viewModelScope.launch { startAutoCalibration() }
                }
            } catch (e: Exception) {
                _lyricsMap.value = _lyricsMap.value + (song.id to emptyList())
            }
        }
    }

    private fun getLocalLyrics(song: Song): String? {
        val fileById = getLyricsFile(song.id.toString())
        if (fileById.exists()) return fileById.readText()
        val fileByMetadata = getLyricsFile(getMetadataHash(song))
        if (fileByMetadata.exists()) return fileByMetadata.readText()
        return null
    }

    private fun getLyricsFile(key: String): File {
        val dir = File(themeManager.context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.lrc")
    }

    private fun getMetadataHash(song: Song): String {
        val input = "${song.title}_${song.artist}".lowercase()
        return MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        visualizerHelper.stopMonitoring()
        stopPositionUpdates()
    }

    // Actions & Methods
    fun selectCollection(title: String, songs: List<Song>, type: String) {
        _selectedCollectionTitle.value = title
        _selectedCollectionSongs.value = songs
        _selectedCollectionType.value = type
    }
    fun clearSelectedCollection() {
        _selectedCollectionTitle.value = ""
        _selectedCollectionSongs.value = emptyList()
        _selectedCollectionType.value = ""
    }
    fun setTheme(themeType: com.pralayakaveri.beatflow.presentation.theme.ThemeType) = viewModelScope.launch { themeManager.setTheme(themeType) }
    fun toggleFavorite(targetSongId: Long) = viewModelScope.launch { musicRepository.toggleFavorite(targetSongId) }
    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun playSongs(songs: List<Song>, startIndex: Int = 0) = musicController.playSongs(songs, startIndex)
    fun togglePlayPause() = musicController.togglePlayPause()
    fun skipToNext() = musicController.skipToNext()
    fun skipToPrevious() = musicController.skipToPrevious()
    fun seekTo(position: Long) = musicController.seekTo(position)
    fun toggleShuffle() = musicController.toggleShuffle()
    fun toggleRepeat() = musicController.toggleRepeat()
    fun setSleepTimer(minutes: Int) = musicController.setSleepTimer(minutes)
    fun cancelSleepTimer() = musicController.cancelSleepTimer()
    fun setUserOffset(offset: Long) {
        _userOffsetMs.value = offset
        currentSong.value?.let { viewModelScope.launch { syncManager.saveOffset(it.id, offset) } }
    }
    fun tapToSync(tappedLyricTime: Long) = setUserOffset(tappedLyricTime - musicController.getCurrentPosition())
    fun saveArtistImage(artistName: String, uri: String) = viewModelScope.launch { musicRepository.saveArtistImage(artistName, uri) }
    fun removeArtistImage(artistName: String) = viewModelScope.launch { musicRepository.removeArtistImage(artistName) }
    fun saveCustomArtwork(songId: Long, uri: String) = viewModelScope.launch(Dispatchers.IO) { musicRepository.saveCustomArtwork(songId, uri) }
    fun removeCustomArtwork(songId: Long) = viewModelScope.launch(Dispatchers.IO) { musicRepository.removeCustomArtwork(songId) }
    fun createPlaylist(name: String) = viewModelScope.launch(Dispatchers.IO) { musicRepository.createPlaylist(name) }
    fun addSongToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch(Dispatchers.IO) { musicRepository.addSongToPlaylist(playlistId, songId) }
    fun removeSongFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch(Dispatchers.IO) { musicRepository.removeSongFromPlaylist(playlistId, songId) }
    fun deletePlaylist(playlistId: Long) = viewModelScope.launch(Dispatchers.IO) { musicRepository.deletePlaylist(playlistId) }
    fun invalidateSongCache() = musicRepository.invalidateSongCache()
    fun startInitialSync() = viewModelScope.launch { musicRepository.startSync(com.pralayakaveri.beatflow.domain.engine.TriggerReason.INITIAL_SCAN) }

    fun saveLyrics(song: Song, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.saveLyrics(song.id, content)
            fetchLyrics(song) // Refresh cache
        }
    }

    fun deleteLyrics(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.deleteLyrics(song.id)
            fetchLyrics(song) // Refresh cache
        }
    }
}
