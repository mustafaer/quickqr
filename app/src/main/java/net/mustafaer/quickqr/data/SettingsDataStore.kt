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

/** User settings, as the ViewModels see them. */
interface SettingsRepository {
    val hapticEnabled: Flow<Boolean>
    val continuousMode: Flow<Boolean>
    val privacyNoticeSeen: Flow<Boolean>
    val themeMode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>

    suspend fun setHapticEnabled(enabled: Boolean)
    suspend fun setContinuousMode(enabled: Boolean)
    suspend fun setPrivacyNoticeSeen()
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quickqr_settings")

class SettingsDataStore(private val context: Context) : SettingsRepository {

    private companion object {
        val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val KEY_CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")

        /**
         * Stored under the key the old onboarding carousel used: anyone who went
         * through it has already read the privacy promise the notice repeats.
         */
        val KEY_PRIVACY_NOTICE_SEEN = booleanPreferencesKey("onboarding_complete")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
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

    override val hapticEnabled: Flow<Boolean> = read { it[KEY_HAPTIC_ENABLED] ?: true }

    override val continuousMode: Flow<Boolean> = read { it[KEY_CONTINUOUS_MODE] ?: false }

    override val privacyNoticeSeen: Flow<Boolean> = read { it[KEY_PRIVACY_NOTICE_SEEN] ?: false }

    override val themeMode: Flow<ThemeMode> = read { ThemeMode.fromStorageKey(it[KEY_THEME_MODE]) }

    override val dynamicColor: Flow<Boolean> = read { it[KEY_DYNAMIC_COLOR] ?: false }

    override suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    override suspend fun setContinuousMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CONTINUOUS_MODE] = enabled }
    }

    override suspend fun setPrivacyNoticeSeen() {
        context.dataStore.edit { it[KEY_PRIVACY_NOTICE_SEEN] = true }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.storageKey }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }
}
