package com.pralayakaveri.orbitmusic.presentation.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeType {
    DYNAMIC, AMOLED_DARK, NEON, MINIMAL
}

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val THEME_KEY = stringPreferencesKey("app_theme")

    val themeFlow: Flow<ThemeType> = context.themeDataStore.data.map { prefs ->
        val themeName = prefs[THEME_KEY] ?: ThemeType.DYNAMIC.name
        try {
            ThemeType.valueOf(themeName)
        } catch (e: Exception) {
            ThemeType.DYNAMIC
        }
    }

    suspend fun setTheme(themeType: ThemeType) {
        context.themeDataStore.edit { prefs ->
            prefs[THEME_KEY] = themeType.name
        }
    }
}
