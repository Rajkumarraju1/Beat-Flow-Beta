package com.pralayakaveri.beatflow.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.service.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import com.pralayakaveri.beatflow.domain.util.LyricsParser
import android.net.Uri
import com.pralayakaveri.beatflow.domain.model.LyricLine
import java.io.File
import java.security.MessageDigest

@HiltViewModel
class MainViewModel @Inject constructor(
    val musicController: MusicController,
    private val musicRepository: MusicRepository,
    private val themeManager: com.pralayakaveri.beatflow.presentation.theme.ThemeManager,
    private val syncManager: com.pralayakaveri.beatflow.domain.util.LyricsSyncManager
) : ViewModel() {

    val currentTheme = themeManager.themeFlow.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        com.pralayakaveri.beatflow.presentation.theme.ThemeType.DYNAMIC
    )


    fun setTheme(themeType: com.pralayakaveri.beatflow.presentation.theme.ThemeType) {
        viewModelScope.launch {
            themeManager.setTheme(themeType)
        }
    }

    val isPlaying = musicController.isPlaying
    val currentSong = musicController.currentSong

    private val _lyricsMap = MutableStateFlow<Map<Long, List<com.pralayakaveri.beatflow.domain.model.LyricLine>>>(emptyMap())
    val lyricsMap: StateFlow<Map<Long, List<com.pralayakaveri.beatflow.domain.model.LyricLine>>> = _lyricsMap.asStateFlow()

    private val _selectedCollectionSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedCollectionSongs: StateFlow<List<Song>> = _selectedCollectionSongs.asStateFlow()

    private val _selectedCollectionTitle = MutableStateFlow("")
    val selectedCollectionTitle: StateFlow<String> = _selectedCollectionTitle.asStateFlow()

    private val _selectedCollectionType = MutableStateFlow("") // "Artist", "Album", or "Folder"
    val selectedCollectionType: StateFlow<String> = _selectedCollectionType.asStateFlow()

    fun selectCollection(title: String, collectionSongs: List<Song>, type: String) {
        _selectedCollectionTitle.value = title
        _selectedCollectionSongs.value = collectionSongs
        _selectedCollectionType.value = type
    }

    fun clearSelectedCollection() {
        _selectedCollectionTitle.value = ""
        _selectedCollectionSongs.value = emptyList()
        _selectedCollectionType.value = ""
    }

    val artistImages: StateFlow<Map<String, String>> = musicRepository.getArtistImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun saveArtistImage(artistName: String, uri: String) {
        viewModelScope.launch {
            musicRepository.saveArtistImage(artistName, uri)
        }
    }

    fun removeArtistImage(artistName: String) {
        viewModelScope.launch {
            musicRepository.removeArtistImage(artistName)
        }
    }

    val lyrics: StateFlow<List<LyricLine>> = combine(currentSong, _lyricsMap) { song, map ->
        song?.let { map[it.id] } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shuffleModeEnabled = musicController.shuffleModeEnabled
    val repeatMode = musicController.repeatMode
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _userOffsetMs = MutableStateFlow(0L)
    val userOffsetMs: StateFlow<Long> = _userOffsetMs.asStateFlow()

    private val _autoOffsetMs = MutableStateFlow(0L)
    val autoOffsetMs: StateFlow<Long> = _autoOffsetMs.asStateFlow()

    private val _runningOffsetMs = MutableStateFlow(0L)
    val runningOffsetMs: StateFlow<Long> = _runningOffsetMs.asStateFlow()

    private val _isVocalPresent = MutableStateFlow(false)
    val isVocalPresent: StateFlow<Boolean> = _isVocalPresent.asStateFlow()

    private val visualizerHelper = com.pralayakaveri.beatflow.domain.util.AudioVisualizerHelper()

    fun setUserOffset(offset: Long) {
        _userOffsetMs.value = offset
        // Persist manual offset
        currentSong.value?.let { song ->
            viewModelScope.launch {
                syncManager.saveOffset(song.id, offset)
            }
        }
    }

    fun tapToSync(tappedLyricTime: Long) {
        val currentPos = musicController.getCurrentPosition()
        // If user taps the 8s line now (at 10s), it means lyrics are 2s ahead.
        // We want adjustedTime to match tappedLyricTime.
        // adjustedProgress = progress + userOffset + ...
        // tappedTime = progress + newOffset
        // newOffset = tappedTime - progress
        val newOffset = tappedLyricTime - currentPos
        setUserOffset(newOffset)
        android.util.Log.d("MainViewModel", "Tap-to-Sync: tapped=$tappedLyricTime, current=$currentPos, newOffset=$newOffset")
    }


    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }


    init {
        updatePosition()
        
        // Auto-fetch lyrics when the song changes
        viewModelScope.launch {
            currentSong.collect { song ->
                android.util.Log.d("MainViewModel", "Song changed: ${song?.title}")
                song?.let { 
                    _autoOffsetMs.value = 0L // Reset for new song
                    _runningOffsetMs.value = 0L
                    
                    // Load persistent manual offset
                    viewModelScope.launch {
                        syncManager.getOffset(it.id).collect { offset ->
                            _userOffsetMs.value = offset
                        }
                    }
                    
                    fetchLyrics(it) 
                } ?: run {
                    _autoOffsetMs.value = 0L
                    _runningOffsetMs.value = 0L
                    _userOffsetMs.value = 0L
                    visualizerHelper.stopMonitoring()
                }
            }
        }
    }

    private fun fetchLyrics(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("MainViewModel", "Fetching lyrics for: ${song.title} (${song.id})")
                
                // 1. Check for locally saved lyrics (User added)
                val localContent = getLocalLyrics(song)
                if (localContent != null) {
                    android.util.Log.d("MainViewModel", "Found local lyrics file")
                    val parsedLyrics = if (com.pralayakaveri.beatflow.domain.util.LrcParser.isLrcFormat(localContent)) {
                        com.pralayakaveri.beatflow.domain.util.LrcParser.parseLrcContent(localContent)
                    } else {
                        com.pralayakaveri.beatflow.domain.util.LrcParser.parsePlainText(localContent, song.duration)
                    }
                    
                    _lyricsMap.value = _lyricsMap.value + (song.id to parsedLyrics)
                    
                    // Start auto-calibration if lyrics have timestamps
                    if (parsedLyrics.any { it.startTimeMs > 0 }) {
                        viewModelScope.launch { startAutoCalibration() }
                    }
                    return@launch
                }

                // 2. Fallback to existing parser (Embedded or Sibling files)
                // FIX: Use song.uri instead of albumArtUri
                val songUri = song.uri
                android.util.Log.d("MainViewModel", "Looking for embedded/sibling lyrics: $songUri")
                val parsed = LyricsParser.getLyricsForSong(songUri)
                
                _lyricsMap.value = _lyricsMap.value + (song.id to parsed)
                if (parsed.isNotEmpty() && parsed.any { it.startTimeMs > 0 }) {
                    viewModelScope.launch { startAutoCalibration() }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error fetching lyrics", e)
                _lyricsMap.value = _lyricsMap.value + (song.id to emptyList())
            }
        }
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
                val lyricsList = lyrics.value
                if (lyricsList.isEmpty()) return@startMonitoring

                val detectedPos = musicController.getCurrentPosition()
                
                if (!firstCalibrationDone) {
                    // Initial calibration: First vocal onset
                    val firstLyricTime = lyricsList.firstOrNull { it.startTimeMs > 1000 }?.startTimeMs 
                        ?: lyricsList.firstOrNull()?.startTimeMs ?: 0L
                    
                    val offset = detectedPos - firstLyricTime
                    _autoOffsetMs.value = offset.coerceIn(-5000L, 5000L)
                    firstCalibrationDone = true
                    android.util.Log.d("MainViewModel", "Initial auto-calibration: ${_autoOffsetMs.value}ms")
                } else {
                    // Progressive Drift Correction: Detect subsequent peaks
                    val closestLyric = lyricsList.minByOrNull { Math.abs(it.startTimeMs - detectedPos) }
                    if (closestLyric != null && Math.abs(closestLyric.startTimeMs - detectedPos) < 500) {
                        val error = closestLyric.startTimeMs - detectedPos
                        val adjustment = (error.toFloat() * 0.1f).toLong().coerceIn(-100L, 100L)
                        
                        if (Math.abs(adjustment) > 5) {
                            _runningOffsetMs.value += adjustment
                            android.util.Log.d("MainViewModel", "Drift correction: error=${error}ms, adjustment=${adjustment}ms")
                        }
                    }
                }
            },
            onFrequencyData = { midBandEnergy ->
                // Threshold for vocal presence
                val isVocal = midBandEnergy > 5.0f
                if (isVocal) {
                    lastVocalTimestamp = System.currentTimeMillis()
                    _isVocalPresent.value = true
                } else {
                    // Decay: Only set to false if no vocal energy detected for 250ms
                    if (System.currentTimeMillis() - lastVocalTimestamp > 250) {
                        _isVocalPresent.value = false
                    }
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        visualizerHelper.stopMonitoring()
    }

    private fun getLocalLyrics(song: Song): String? {
        // Try by ID first
        val fileById = getLyricsFile(song.id.toString())
        if (fileById.exists()) return fileById.readText()

        // Fallback to title_artist hash
        val fileByMetadata = getLyricsFile(getMetadataHash(song))
        if (fileByMetadata.exists()) return fileByMetadata.readText()

        return null
    }

    private fun getLyricsFile(key: String): File {
        val context = themeManager.context // Need context for filesDir
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.lrc")
    }

    private fun getMetadataHash(song: Song): String {
        val input = "${song.title}_${song.artist}".lowercase()
        return MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun saveLyrics(song: Song, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("MainViewModel", "Saving lyrics for: ${song.title}. Content length: ${content.length}")
                // Save both ID and Hash for reliability
                getLyricsFile(song.id.toString()).writeText(content)
                getLyricsFile(getMetadataHash(song)).writeText(content)
                android.util.Log.d("MainViewModel", "Lyrics file saved successfully")
                fetchLyrics(song) // Refresh
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to save lyrics", e)
            }
        }
    }

    fun deleteLyrics(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getLyricsFile(song.id.toString()).delete()
                getLyricsFile(getMetadataHash(song)).delete()
                fetchLyrics(song) // Refresh (will fallback or clear)
            } catch (e: Exception) {
                // Handle
            }
        }
    }

    private fun updatePosition() {
        viewModelScope.launch {
            while (true) {
                if (isPlaying.value) {
                    _currentPosition.value = musicController.getCurrentPosition()
                }
                delay(16L)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        musicController.playSongs(songs, startIndex)
    }

    fun togglePlayPause() {
        musicController.togglePlayPause()
    }

    fun skipToNext() {
        musicController.skipToNext()
    }

    fun skipToPrevious() {
        musicController.skipToPrevious()
    }

    fun seekTo(position: Long) {
        musicController.seekTo(position)
    }
    
    fun toggleShuffle() {
        musicController.toggleShuffle()
    }

    fun toggleRepeat() {
        musicController.toggleRepeat()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(song.id)
        }
    }

    fun isFavorite(songId: Long): StateFlow<Boolean> {
        return musicRepository.isFavorite(songId).stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    }

    val sleepTimerTimeRemaining: StateFlow<Long?> = musicController.sleepTimerTimeRemaining

    fun setSleepTimer(minutes: Int) {
        musicController.setSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        musicController.cancelSleepTimer()
    }

    val customArtworks: StateFlow<Map<Long, String>> = musicRepository.getAllCustomArtworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun saveCustomArtwork(songId: Long, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.saveCustomArtwork(songId, uri)
        }
    }

    fun removeCustomArtwork(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.removeCustomArtwork(songId)
        }
    }

    val playlists: StateFlow<List<com.pralayakaveri.beatflow.domain.model.Playlist>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.createPlaylist(name)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun invalidateSongCache() {
        musicRepository.invalidateSongCache()
    }

    fun startInitialSync() {
        viewModelScope.launch {
            musicRepository.startSync(com.pralayakaveri.beatflow.domain.engine.TriggerReason.INITIAL_SCAN)
        }
    }
}
