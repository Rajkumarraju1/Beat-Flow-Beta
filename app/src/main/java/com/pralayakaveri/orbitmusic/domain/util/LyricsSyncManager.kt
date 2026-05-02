package com.pralayakaveri.orbitmusic.domain.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_sync_prefs")

@Singleton
class LyricsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getOffset(songId: Long): Flow<Long> {
        val key = longPreferencesKey("sync_offset_$songId")
        return context.syncDataStore.data.map { prefs ->
            prefs[key] ?: 0L
        }
    }

    suspend fun saveOffset(songId: Long, offset: Long) {
        val key = longPreferencesKey("sync_offset_$songId")
        context.syncDataStore.edit { prefs ->
            prefs[key] = offset
        }
    }
}
