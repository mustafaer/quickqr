package net.mustafaer.quickqr.data

import androidx.appcompat.app.AppCompatDelegate

/**
 * How QuickQR picks between the light and dark colour schemes.
 *
 * The choice is applied through [AppCompatDelegate.setDefaultNightMode] rather
 * than a Compose-only flag, so it also drives resource qualifiers: the `-night`
 * window background follows the user's choice and the app no longer flashes the
 * wrong ground colour before the first Compose frame.
 */
enum class ThemeMode(val storageKey: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        val DEFAULT = SYSTEM

        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
