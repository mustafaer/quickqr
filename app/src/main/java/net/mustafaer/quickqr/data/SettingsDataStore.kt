package net.mustafaer.quickqr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quickqr_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val KEY_CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    /**
     * A corrupt or unreadable preferences file should leave the app on its
     * defaults, not crash it — but anything that is not an I/O problem is a real
     * bug and still propagates.
     */
    private fun <T> read(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map(transform)

    val hapticEnabledFlow: Flow<Boolean> = read { it[KEY_HAPTIC_ENABLED] ?: true }

    val continuousModeFlow: Flow<Boolean> = read { it[KEY_CONTINUOUS_MODE] ?: false }

    val onboardingCompleteFlow: Flow<Boolean> = read { it[KEY_ONBOARDING_COMPLETE] ?: false }

    /** Null until the user picks a language explicitly; the device locale wins until then. */
    val languageFlow: Flow<String?> = read { it[KEY_LANGUAGE] }

    val themeModeFlow: Flow<ThemeMode> = read { ThemeMode.fromStorageKey(it[KEY_THEME_MODE]) }

    val dynamicColorFlow: Flow<Boolean> = read { it[KEY_DYNAMIC_COLOR] ?: false }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    suspend fun setContinuousMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CONTINUOUS_MODE] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.storageKey }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }
}
