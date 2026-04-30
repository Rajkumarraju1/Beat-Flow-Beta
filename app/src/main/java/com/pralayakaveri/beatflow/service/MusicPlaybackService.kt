package com.pralayakaveri.beatflow.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    @javax.inject.Inject
    lateinit var sessionManager: com.pralayakaveri.beatflow.domain.util.PlaybackSessionManager

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override fun onCreate() {
        android.util.Log.d("MusicPlaybackService", "Service onCreate - Initializing ExoPlayer")
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                val extras = android.os.Bundle().apply {
                    putInt("audio_session_id", audioSessionId)
                }
                mediaLibrarySession.setSessionExtras(extras)
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    saveCurrentSession()
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                saveCurrentSession()
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) {
                    saveCurrentSession()
                }
            }
        })
            
        val sessionCallback = object : MediaLibrarySession.Callback {}

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, sessionCallback)
            .build()
    }

    private fun saveCurrentSession() {
        val currentItem = exoPlayer.currentMediaItem ?: return
        val songId = currentItem.mediaId.toLongOrNull() ?: return
        val position = exoPlayer.currentPosition
        val index = exoPlayer.currentMediaItemIndex
        
        val queueIds = mutableListOf<Long>()
        for (i in 0 until exoPlayer.mediaItemCount) {
            exoPlayer.getMediaItemAt(i).mediaId.toLongOrNull()?.let { queueIds.add(it) }
        }

        serviceScope.launch {
            sessionManager.saveSession(
                songId = songId,
                position = position,
                queueIds = queueIds,
                queueIndex = index
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        android.util.Log.d("MusicPlaybackService", "onGetSession from: ${controllerInfo.packageName}")
        return mediaLibrarySession
    }

    override fun onDestroy() {
        android.util.Log.d("MusicPlaybackService", "Service onDestroy - Releasing Resources")
        saveCurrentSession()
        serviceScope.cancel()
        mediaLibrarySession.run {
            exoPlayer.release()
            release()
        }
        super.onDestroy()
    }
}
