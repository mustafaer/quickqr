package net.mustafaer.quickqr.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.QuickQrApp
import net.mustafaer.quickqr.data.AppLanguage
import net.mustafaer.quickqr.data.SettingsRepository
import net.mustafaer.quickqr.data.ThemeMode

data class SettingsState(
    val hapticEnabled: Boolean,
    val continuousMode: Boolean,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean
)

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel((this[APPLICATION_KEY] as QuickQrApp).container.settings)
            }
        }
    }

    /** Null while loading — the splash screen stays up until it resolves. */
    val state: StateFlow<SettingsState?> = combine(
        settings.hapticEnabled,
        settings.continuousMode,
        settings.themeMode,
        settings.dynamicColor,
        ::SettingsState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticEnabled(enabled) }
    }

    fun setContinuousMode(enabled: Boolean) {
        viewModelScope.launch { settings.setContinuousMode(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /**
     * Applied immediately and persisted for the next cold start, where
     * [QuickQrApp] applies it before the first Activity exists.
     */
    fun setThemeMode(mode: ThemeMode) {
        if (AppCompatDelegate.getDefaultNightMode() != mode.nightMode) {
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /**
     * Writes the choice straight to AppCompat, which persists it and is the same
     * store the system's per-app language picker uses. Keeping a second copy in
     * DataStore meant a language chosen in Android Settings was silently reverted
     * on the next launch.
     */
    fun setLanguage(language: AppLanguage) {
        val target = LocaleListCompat.forLanguageTags(language.code)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }
}
