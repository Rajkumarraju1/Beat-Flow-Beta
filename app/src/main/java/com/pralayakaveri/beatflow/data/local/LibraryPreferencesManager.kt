package com.pralayakaveri.beatflow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pralayakaveri.beatflow.domain.model.SortOrder
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
}
