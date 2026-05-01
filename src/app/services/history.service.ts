import {Injectable} from '@angular/core';
import {ScanHistoryItem, ScanResultType, MAX_HISTORY, HISTORY_KEY} from '../models/scan.model';
import {TypeDetectorService} from './type-detector.service';

@Injectable({providedIn: 'root'})
export class HistoryService {

    private _items: ScanHistoryItem[] = [];

    get items(): ScanHistoryItem[] {
        return this._items;
    }

    constructor(private typeDetector: TypeDetectorService) {
        this.load();
    }

    /** Add a scan result to history. Returns the new item, or null if duplicate of last. */
    add(text: string, type: ScanResultType): ScanHistoryItem | null {
        if (this._items.length > 0 && this._items[0].text === text) {
            return null; // Skip exact duplicate of most recent
        }

        const item: ScanHistoryItem = {
            id: crypto.randomUUID(),
            text,
            date: new Date(),
            type,
        };

        this._items.unshift(item);
        if (this._items.length > MAX_HISTORY) {
            this._items.length = MAX_HISTORY;
        }
        this.save();
        return item;
    }

    /** Remove a single item by ID */
    remove(id: string): void {
        this._items = this._items.filter(i => i.id !== id);
        this.save();
    }

    /** Clear entire history */
    clear(): void {
        this._items = [];
        this.save();
    }

    /** Search/filter history items */
    search(query: string): ScanHistoryItem[] {
        if (!query.trim()) return this._items;
        const q = query.toLowerCase();
        return this._items.filter(i =>
            i.text.toLowerCase().includes(q) ||
            i.type.toLowerCase().includes(q)
        );
    }

    /** Export history as JSON string */
    exportJSON(): string {
        return JSON.stringify(this._items.map(i => ({
            text: i.text,
            type: i.type,
            date: i.date instanceof Date ? i.date.toISOString() : i.date,
        })), null, 2);
    }

    /** Export history as CSV string */
    exportCSV(): string {
        const escape = (s: string) => `"${s.replace(/"/g, '""')}"`;
        const rows = this._items.map(i =>
            `${escape(i.text)},${i.type},${i.date instanceof Date ? i.date.toISOString() : i.date}`
        );
        return ['Text,Type,Date', ...rows].join('\n');
    }

    private save(): void {
        try {
            localStorage.setItem(HISTORY_KEY, JSON.stringify(this._items));
        } catch (err) {
            console.warn('[HistoryService] Failed to save history:', err);
        }
    }

    private load(): void {
        try {
            const raw = localStorage.getItem(HISTORY_KEY);
            if (!raw) return;

            const arr = JSON.parse(raw);
            if (!Array.isArray(arr)) return;

            this._items = arr.map((i: Record<string, unknown>) => ({
                id: (i['id'] as string) || crypto.randomUUID(),
                text: i['text'] as string,
                date: new Date(i['date'] as string),
                type: (i['type'] as ScanResultType) || this.typeDetector.detect(i['text'] as string),
            }));
        } catch (err) {
            console.warn('[HistoryService] Failed to load history:', err);
            this._items = [];
        }
    }
}
