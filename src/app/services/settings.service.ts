import {Injectable} from '@angular/core';

const SETTINGS_KEY = 'quickqr_settings';

export interface AppSettings {
    hapticEnabled: boolean;
    continuousMode: boolean;
    language: 'en' | 'tr';
    onboardingComplete: boolean;
}

const DEFAULTS: AppSettings = {
    hapticEnabled: true,
    continuousMode: false,
    language: 'en',
    onboardingComplete: false,
};

@Injectable({providedIn: 'root'})
export class SettingsService {

    private _settings: AppSettings;

    get settings(): AppSettings {
        return this._settings;
    }

    get hapticEnabled(): boolean { return this._settings.hapticEnabled; }
    get continuousMode(): boolean { return this._settings.continuousMode; }
    get language(): 'en' | 'tr' { return this._settings.language; }
    get onboardingComplete(): boolean { return this._settings.onboardingComplete; }

    constructor() {
        this._settings = this.load();
    }

    update(partial: Partial<AppSettings>): void {
        this._settings = {...this._settings, ...partial};
        this.save();
    }

    toggleHaptic(): void {
        this.update({hapticEnabled: !this._settings.hapticEnabled});
    }

    toggleContinuousMode(): void {
        this.update({continuousMode: !this._settings.continuousMode});
    }

    setLanguage(lang: 'en' | 'tr'): void {
        this.update({language: lang});
    }

    completeOnboarding(): void {
        this.update({onboardingComplete: true});
    }

    private save(): void {
        try {
            localStorage.setItem(SETTINGS_KEY, JSON.stringify(this._settings));
        } catch (err) {
            console.warn('[Settings] Failed to save:', err);
        }
    }

    private load(): AppSettings {
        try {
            const raw = localStorage.getItem(SETTINGS_KEY);
            if (!raw) return {...DEFAULTS};
            return {...DEFAULTS, ...JSON.parse(raw)};
        } catch {
            return {...DEFAULTS};
        }
    }
}
