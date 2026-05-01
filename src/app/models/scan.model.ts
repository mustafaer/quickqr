// ── Scan Result Types ──────────────────────────────────────────
export type ScanResultType =
    | 'url' | 'wifi' | 'email' | 'phone'
    | 'geo' | 'sms' | 'vcard' | 'calendar' | 'text';

// ── Data Models ────────────────────────────────────────────────
export interface ScanHistoryItem {
    id: string;
    text: string;
    date: Date;
    type: ScanResultType;
}

export interface WifiData {
    ssid: string;
    security: string;
    password: string;
    hidden: boolean;
}

export interface TypeConfig {
    label: string;
    icon: string;
    color: string;
    actionable: boolean;
}

// ── Constants ──────────────────────────────────────────────────
export const MAX_HISTORY = 50;
export const DEBOUNCE_MS = 1500;
export const HISTORY_KEY = 'quickqr_scan_history';
export const RESULT_TRUNCATE_LENGTH = 60;

/** Delay (ms) for camera release before re-acquiring */
export const CAMERA_RELEASE_DELAY_MS = 300;
