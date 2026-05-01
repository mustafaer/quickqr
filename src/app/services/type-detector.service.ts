import {Injectable} from '@angular/core';
import {ScanResultType, TypeConfig, WifiData} from '../models/scan.model';

/** Configuration map for all supported QR content types */
const TYPE_MAP: Record<ScanResultType, TypeConfig> = {
    url:      {label: 'URL',      icon: 'link-outline',       color: 'secondary', actionable: true},
    wifi:     {label: 'Wi-Fi',    icon: 'wifi-outline',       color: 'warning',   actionable: false},
    email:    {label: 'Email',    icon: 'mail-outline',       color: 'success',   actionable: true},
    phone:    {label: 'Phone',    icon: 'call-outline',       color: 'primary',   actionable: true},
    geo:      {label: 'Location', icon: 'location-outline',   color: 'danger',    actionable: true},
    sms:      {label: 'SMS',      icon: 'chatbubble-outline', color: 'primary',   actionable: true},
    vcard:    {label: 'Contact',  icon: 'person-outline',     color: 'secondary', actionable: false},
    calendar: {label: 'Event',    icon: 'calendar-outline',   color: 'warning',   actionable: false},
    text:     {label: 'Text',     icon: 'text-outline',       color: 'medium',    actionable: false},
};

@Injectable({providedIn: 'root'})
export class TypeDetectorService {

    /** Detect the QR content type from raw text */
    detect(text: string): ScanResultType {
        const t = text.trim();
        if (/^https?:\/\//i.test(t) || /^ftp:\/\//i.test(t)) return 'url';
        if (/^www\./i.test(t)) return 'url';
        if (/^WIFI:/i.test(t)) return 'wifi';
        if (/^MATMSG:/i.test(t)) return 'email';
        if (/^mailto:/i.test(t) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t)) return 'email';
        if (/^smsto?:/i.test(t)) return 'sms';
        if (/^tel:/i.test(t) || /^\+?\d[\d\s\-()]{6,}$/.test(t)) return 'phone';
        if (/^geo:/i.test(t)) return 'geo';
        if (/^BEGIN:VCARD/i.test(t)) return 'vcard';
        if (/^BEGIN:VCALENDAR/i.test(t) || /^BEGIN:VEVENT/i.test(t)) return 'calendar';
        return 'text';
    }

    getLabel(type: ScanResultType): string {
        return TYPE_MAP[type].label;
    }

    getIcon(type: ScanResultType): string {
        return TYPE_MAP[type].icon;
    }

    getColor(type: ScanResultType): string {
        return TYPE_MAP[type].color;
    }

    isActionable(type: ScanResultType): boolean {
        return TYPE_MAP[type].actionable;
    }

    /** Build the URL to open for actionable types */
    getActionUrl(text: string, type: ScanResultType): string {
        switch (type) {
            case 'url':
                return /^(https?|ftp):\/\//i.test(text) ? text : `https://${text}`;
            case 'email':
                if (/^mailto:/i.test(text)) return text;
                if (/^MATMSG:/i.test(text)) {
                    const to = text.match(/TO:([^;]*)/i)?.[1] || '';
                    const sub = text.match(/SUB:([^;]*)/i)?.[1] || '';
                    const body = text.match(/BODY:([^;]*)/i)?.[1] || '';
                    return `mailto:${to}?subject=${encodeURIComponent(sub)}&body=${encodeURIComponent(body)}`;
                }
                return `mailto:${text}`;
            case 'phone':
                return text.startsWith('tel:') ? text : `tel:${text}`;
            case 'sms':
                return /^smsto?:/i.test(text) ? text.replace(/^smsto:/i, 'sms:') : `sms:${text}`;
            case 'geo':
                return text;
            default:
                return text;
        }
    }

    /** Parse WIFI: QR format into structured data */
    parseWifi(text: string): WifiData | null {
        if (!/^WIFI:/i.test(text)) return null;
        const getValue = (key: string): string => {
            const match = text.match(new RegExp(`${key}:([^;]*)`, 'i'));
            return match?.[1] || '';
        };
        return {
            ssid: getValue('S'),
            security: getValue('T') || 'Open',
            password: getValue('P'),
            hidden: getValue('H').toLowerCase() === 'true',
        };
    }

    /** Extract a human-readable summary for vCard */
    parseVCardName(text: string): string | null {
        const fn = text.match(/FN:(.+)/i)?.[1]?.trim();
        if (fn) return fn;
        const n = text.match(/N:([^;]*);?([^;]*)/i);
        if (n) return `${n[2]} ${n[1]}`.trim();
        return null;
    }

    /** Extract a human-readable summary for calendar events */
    parseEventSummary(text: string): string | null {
        return text.match(/SUMMARY:(.+)/i)?.[1]?.trim() || null;
    }
}
