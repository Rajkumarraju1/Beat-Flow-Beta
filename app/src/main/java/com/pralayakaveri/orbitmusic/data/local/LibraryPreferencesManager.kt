package com.pralayakaveri.orbitmusic.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pralayakaveri.orbitmusic.domain.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_prefs")

@Singleton
class LibraryPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val IS_ONBOARDING_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("is_onboarding_completed")
        val USE_REDUCED_MOTION = androidx.datastore.preferences.core.booleanPreferencesKey("use_reduced_motion")
    }

    val sortOrderFlow: Flow<SortOrder> = context.libraryPrefsDataStore.data.map { prefs ->
        val sortName = prefs[Keys.SORT_ORDER] ?: SortOrder.TITLE.name
        try {
            SortOrder.valueOf(sortName)
        } catch (e: Exception) {
            SortOrder.TITLE
        }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.libraryPrefsDataStore.edit { prefs ->
            prefs[Keys.SORT_ORDER] = sortOrder.name
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = context.libraryPrefsDataStore.data.map { prefs ->
        prefs[Keys.IS_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.libraryPrefsDataStore.edit { prefs ->
            prefs[Keys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    val useReducedMotion: Flow<Boolean> = context.libraryPrefsDataStore.data.map { prefs ->
        prefs[Keys.USE_REDUCED_MOTION] ?: false
    }

    suspend fun setUseReducedMotion(enabled: Boolean) {
        context.libraryPrefsDataStore.edit { prefs ->
            prefs[Keys.USE_REDUCED_MOTION] = enabled
        }
    }
}
