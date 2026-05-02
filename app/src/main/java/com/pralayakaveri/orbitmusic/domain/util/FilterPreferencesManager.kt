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

private val Context.filterDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_filter_prefs")

data class FilterPreferences(
    val minDurationEnabled: Boolean,
    val minDurationMs: Long,
    val minSizeEnabled: Boolean,
    val minSizeBytes: Long
)

@Singleton
class FilterPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MIN_DURATION_ENABLED = booleanPreferencesKey("min_duration_enabled")
        val MIN_DURATION_MS = longPreferencesKey("min_duration_ms")
        val MIN_SIZE_ENABLED = booleanPreferencesKey("min_size_enabled")
        val MIN_SIZE_BYTES = longPreferencesKey("min_size_bytes")
    }

    val filterFlow: Flow<FilterPreferences> = context.filterDataStore.data.map { prefs ->
        FilterPreferences(
            minDurationEnabled = prefs[Keys.MIN_DURATION_ENABLED] ?: true,
            minDurationMs = prefs[Keys.MIN_DURATION_MS] ?: 30000L,
            minSizeEnabled = prefs[Keys.MIN_SIZE_ENABLED] ?: true,
            minSizeBytes = prefs[Keys.MIN_SIZE_BYTES] ?: 102400L
        )
    }

    suspend fun updateMinDurationEnabled(enabled: Boolean) {
        context.filterDataStore.edit { it[Keys.MIN_DURATION_ENABLED] = enabled }
    }

    suspend fun updateMinSizeEnabled(enabled: Boolean) {
        context.filterDataStore.edit { it[Keys.MIN_SIZE_ENABLED] = enabled }
    }
    
    suspend fun updateThresholds(durationMs: Long, sizeBytes: Long) {
        context.filterDataStore.edit { 
            it[Keys.MIN_DURATION_MS] = durationMs
            it[Keys.MIN_SIZE_BYTES] = sizeBytes
        }
    }
}
