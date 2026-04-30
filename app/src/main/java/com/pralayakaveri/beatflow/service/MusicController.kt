package com.pralayakaveri.beatflow.service

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pralayakaveri.beatflow.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

class MusicController(
    private val context: Context,
    private val musicRepository: com.pralayakaveri.beatflow.domain.repository.MusicRepository,
    private val sessionManager: com.pralayakaveri.beatflow.domain.util.PlaybackSessionManager
) {
    private val instanceId = java.util.UUID.randomUUID().toString().substring(0, 8)
    
    private val controllerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var mediaBrowser: MediaBrowser? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    
    // Store current playlist for quick lookup
    private var currentPlaylist: List<Song> = emptyList()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private var lastPlayedIndex = -1

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                val index = mediaBrowser?.currentMediaItemIndex ?: -1
                if (index != -1 && index != lastPlayedIndex && index in currentPlaylist.indices) {
                    val songId = currentPlaylist[index].id
                    controllerScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        musicRepository.incrementPlayCount(songId)
                    }
                    lastPlayedIndex = index
                }
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            val index = mediaBrowser?.currentMediaItemIndex ?: -1
            _currentSongIndex.value = index
            _currentSong.value = if (index in currentPlaylist.indices) currentPlaylist[index] else null
            
            // Reset tracker if user skipped before audio actively 'played'
            if (mediaBrowser?.isPlaying == false) {
                lastPlayedIndex = -1
            }
        }
    }

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepTimerTimeRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerTimeRemaining: StateFlow<Long?> = _sleepTimerTimeRemaining.asStateFlow()

    private var sleepTimer: android.os.CountDownTimer? = null

    init {
        android.util.Log.d("MusicController", "Initializing MusicController instance: $instanceId")
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        android.util.Log.d("MusicController", "Building MediaBrowser for instance: $instanceId")
        browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture?.addListener({
            try {
                mediaBrowser = browserFuture?.get()
                android.util.Log.d("MusicController", "MediaBrowser CONNECTED for instance: $instanceId")
                mediaBrowser?.addListener(playerListener)
                
                mediaBrowser?.let {
                    _shuffleModeEnabled.value = it.shuffleModeEnabled
                    _repeatMode.value = it.repeatMode
                    
                    // CRITICAL GUARD: Only restore session if nothing is currently playing/loaded
                    if (it.mediaItemCount == 0) {
                        android.util.Log.d("MusicController", "Restoring last session for instance: $instanceId")
                        restoreLastSession()
                    } else {
                        android.util.Log.d("MusicController", "Session already active, skipping restore for instance: $instanceId")
                        // Synchronize local state with existing session
                        _currentSong.value = it.currentMediaItem?.let { item -> 
                            currentPlaylist.find { s -> s.id.toString() == item.mediaId }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicController", "MediaBrowser connection failed for instance: $instanceId", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun restoreLastSession() {
        controllerScope.launch {
            val session: com.pralayakaveri.beatflow.domain.util.PlaybackSession = sessionManager.sessionFlow.first()
            val lastId: Long? = session.lastSongId
            if (lastId != null && session.lastQueueIds.isNotEmpty()) {
                val allSongs: List<com.pralayakaveri.beatflow.domain.model.Song> = musicRepository.getSongs()
                val songMap: Map<Long, com.pralayakaveri.beatflow.domain.model.Song> = allSongs.associateBy { it.id }
                val restoredQueue: List<com.pralayakaveri.beatflow.domain.model.Song> = session.lastQueueIds.mapNotNull { qId -> songMap[qId] }
                
                if (restoredQueue.isNotEmpty()) {
                    currentPlaylist = restoredQueue
                    mediaBrowser?.let { browser ->
                        val mediaItems = restoredQueue.map { s ->
                            MediaItem.Builder()
                                .setUri(s.uri)
                                .setMediaId(s.id.toString())
                                .build()
                        }
                        browser.setMediaItems(mediaItems)
                        
                        var restoreIndex = 0
                        if (session.lastQueueIndex in restoredQueue.indices) {
                            restoreIndex = session.lastQueueIndex
                        } else {
                            for (i in restoredQueue.indices) {
                                val songInQueue = restoredQueue[i]
                                if (songInQueue.id == lastId) {
                                    restoreIndex = i
                                    break
                                }
                            }
                        }
                        
                        browser.seekTo(restoreIndex, session.lastPosition)
                        browser.prepare()
                        
                        _currentSongIndex.value = restoreIndex
                        _currentSong.value = restoredQueue[restoreIndex]
                    }
                }
            }
        }
    }

    fun toggleShuffle() {
        mediaBrowser?.let {
            val isEnabled = !it.shuffleModeEnabled
            it.shuffleModeEnabled = isEnabled
            _shuffleModeEnabled.value = isEnabled
        }
    }

    fun toggleRepeat() {
        mediaBrowser?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        currentPlaylist = songs
        mediaBrowser?.let { browser ->
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.id.toString())
                    .build()
            }
            browser.setMediaItems(mediaItems)
            browser.seekToDefaultPosition(startIndex)
            browser.prepare()
            browser.play()
        }
    }
    
    fun togglePlayPause() {
        mediaBrowser?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }
    
    fun skipToNext() {
        mediaBrowser?.seekToNext()
    }
    
    fun skipToPrevious() {
        mediaBrowser?.seekToPrevious()
    }
    
    fun seekTo(position: Long) {
        mediaBrowser?.seekTo(position)
    }
    
    fun getCurrentPosition(): Long = mediaBrowser?.currentPosition ?: 0L
    fun getDuration(): Long = mediaBrowser?.duration ?: 0L

    /**
     * Fallback for visualizer. Primary audioSessionId management is now handled
     * via [VisualizerHelper] in the [MusicPlaybackService].
     */
    fun getAudioSessionId(): Int {
        return mediaBrowser?.sessionExtras?.getInt("audio_session_id") ?: 0
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer() // cancel existing
        if (minutes <= 0) return

        val durationMs = minutes * 60 * 1000L
        sleepTimer = object : android.os.CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _sleepTimerTimeRemaining.value = millisUntilFinished
            }

            override fun onFinish() {
                _sleepTimerTimeRemaining.value = null
                mediaBrowser?.pause()
            }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        _sleepTimerTimeRemaining.value = null
    }

    fun destroy() {
        android.util.Log.d("MusicController", "Destroying MusicController instance: $instanceId")
        cancelSleepTimer()
        browserFuture?.let {
            MediaBrowser.releaseFuture(it)
        }
    }
}
