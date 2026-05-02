package com.pralayakaveri.orbitmusic.domain.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session_prefs")

data class PlaybackSession(
    val lastSongId: Long?,
    val lastPosition: Long,
    val lastQueueIds: List<Long>,
    val lastQueueIndex: Int
)

@Singleton
class PlaybackSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_SONG_ID = longPreferencesKey("last_song_id")
        val LAST_POSITION = longPreferencesKey("last_position")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
    }

    val sessionFlow: Flow<PlaybackSession> = context.sessionDataStore.data.map { prefs ->
        val queueIdsString = prefs[Keys.LAST_QUEUE_IDS] ?: ""
        val queueIds = if (queueIdsString.isNotEmpty()) {
            queueIdsString.split(",").mapNotNull { it.toLongOrNull() }
        } else emptyList()

        PlaybackSession(
            lastSongId = prefs[Keys.LAST_SONG_ID],
            lastPosition = prefs[Keys.LAST_POSITION] ?: 0L,
            lastQueueIds = queueIds,
            lastQueueIndex = prefs[Keys.LAST_QUEUE_INDEX] ?: -1
        )
    }

    suspend fun saveSession(
        songId: Long?,
        position: Long,
        queueIds: List<Long> = emptyList(),
        queueIndex: Int = -1
    ) {
        context.sessionDataStore.edit { prefs ->
            if (songId != null) {
                prefs[Keys.LAST_SONG_ID] = songId
            }
            prefs[Keys.LAST_POSITION] = position
            if (queueIds.isNotEmpty()) {
                prefs[Keys.LAST_QUEUE_IDS] = queueIds.joinToString(",")
            }
            if (queueIndex != -1) {
                prefs[Keys.LAST_QUEUE_INDEX] = queueIndex
            }
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
