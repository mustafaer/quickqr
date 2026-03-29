import {Component, ChangeDetectionStrategy, ChangeDetectorRef, ViewChild, OnDestroy} from '@angular/core';
import {DatePipe} from '@angular/common';
import {
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonChip,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonList,
    IonNote,
    IonTitle,
    IonToolbar,
    ToastController,
} from '@ionic/angular/standalone';
import {ZXingScannerModule, ZXingScannerComponent} from "@zxing/ngx-scanner";
import {BarcodeFormat} from "@zxing/library";
import {Haptics, ImpactStyle} from '@capacitor/haptics';
import {Browser} from '@capacitor/browser';
import {addIcons} from 'ionicons';
import {
    cameraReverseOutline,
    checkmarkCircle,
    checkmarkCircleOutline,
    chevronForwardOutline,
    closeCircleOutline,
    copyOutline,
    flash,
    flashOutline,
    linkOutline,
    lockClosedOutline,
    locationOutline,
    mailOutline,
    callOutline,
    openOutline,
    qrCodeOutline,
    refreshOutline,
    scanOutline,
    textOutline,
    wifiOutline,
} from 'ionicons/icons';

// ── Types ──
interface ScanHistoryItem {
    id: string;
    text: string;
    date: Date;
    type: ScanResultType;
}

type ScanResultType = 'url' | 'wifi' | 'email' | 'phone' | 'geo' | 'text';

const MAX_HISTORY = 20;
const HISTORY_KEY = 'quickqr_scan_history';
const DEBOUNCE_MS = 1500;
const PAUSE_MS = 2000;

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DatePipe,
        IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle,
        IonChip, IonContent, IonHeader, IonTitle, IonToolbar,
        IonIcon, IonButton, IonList, IonItem, IonLabel, IonNote,
        ZXingScannerModule,
    ],
})
export class AppComponent implements OnDestroy {
    @ViewChild(ZXingScannerComponent) scanner!: ZXingScannerComponent;

    // Scanner
    scannerEnabled = true;
    torchEnabled = false;
    scannerPaused = false;
    scannedResult: string | null = null;
    latestScanTime: Date | null = null;

    // Cached result type
    resultType: ScanResultType = 'text';
    resultIsUrl = false;

    // Permission — no blocking state on startup
    cameraPermissionDenied = false;
    hasCamera = true;

    // Formats
    readonly scanFormats = [
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
    ];

    // Camera
    availableCameras: MediaDeviceInfo[] = [];
    private activeCameraIndex = 0;

    // History
    scanHistory: ScanHistoryItem[] = [];

    // Internal
    private lastScanTime = 0;
    private resumeTimer: ReturnType<typeof setTimeout> | null = null;

    constructor(
        private toastController: ToastController,
        private cdr: ChangeDetectorRef,
    ) {
        addIcons({
            cameraReverseOutline,
            checkmarkCircle,
            checkmarkCircleOutline,
            chevronForwardOutline,
            closeCircleOutline,
            copyOutline,
            flash,
            flashOutline,
            linkOutline,
            lockClosedOutline,
            locationOutline,
            mailOutline,
            callOutline,
            openOutline,
            qrCodeOutline,
            refreshOutline,
            scanOutline,
            textOutline,
            wifiOutline,
        });
        this.loadHistory();
    }

    ngOnDestroy() {
        if (this.resumeTimer) clearTimeout(this.resumeTimer);
    }

    // ── zxing-scanner events ──────────────────────────────

    /** Fires once when cameras are enumerated (permission was granted). */
    onCamerasFound(cameras: MediaDeviceInfo[]) {
        this.availableCameras = cameras;
        this.hasCamera = cameras.length > 0;
        this.cameraPermissionDenied = false;

        if (cameras.length > 0) {
            const backIdx = cameras.findIndex(c => /back|rear|environment/i.test(c.label));
            this.activeCameraIndex = backIdx >= 0 ? backIdx : 0;

            // Programmatically select back camera
            if (this.scanner) {
                this.scanner.device = cameras[this.activeCameraIndex];
            }
        }
        this.cdr.markForCheck();
    }

    /** Fires when user grants/denies camera via the native OS dialog. */
    onPermissionResponse(granted: boolean) {
        if (!granted) {
            this.cameraPermissionDenied = true;
            this.scannerEnabled = false;
        } else {
            this.cameraPermissionDenied = false;
        }
        this.cdr.markForCheck();
    }

    /** User taps "Try Again" after denying. */
    async retryPermission() {
        this.cameraPermissionDenied = false;
        this.scannerEnabled = true;
        this.cdr.markForCheck();

        // Force a fresh getUserMedia to re-trigger the OS prompt
        try {
            const stream = await navigator.mediaDevices.getUserMedia({video: true});
            stream.getTracks().forEach(t => t.stop());
            // Give 300ms for the camera to release before scanner picks it up
            await new Promise(r => setTimeout(r, 300));
            this.scannerEnabled = true;
            this.cdr.markForCheck();
        } catch {
            this.cameraPermissionDenied = true;
            this.scannerEnabled = false;
            this.cdr.markForCheck();
        }
    }

    switchCamera() {
        if (this.availableCameras.length < 2) return;
        this.activeCameraIndex = (this.activeCameraIndex + 1) % this.availableCameras.length;
        if (this.scanner) {
            this.scanner.device = this.availableCameras[this.activeCameraIndex];
        }
        this.cdr.markForCheck();
    }

    toggleTorch() {
        this.torchEnabled = !this.torchEnabled;
        this.cdr.markForCheck();
    }

    // ── Scan result ───────────────────────────────────────

    async onCodeResult(result: string) {
        const now = Date.now();
        if (now - this.lastScanTime < DEBOUNCE_MS) return;
        if (result === this.scannedResult) return;

        this.lastScanTime = now;
        this.scannedResult = result;
        this.latestScanTime = new Date();
        this.resultType = this.detectType(result);
        this.resultIsUrl = this.resultType === 'url';

        this.pauseScanner();

        // History
        if (!this.scanHistory.length || this.scanHistory[0].text !== result) {
            this.scanHistory.unshift({
                id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                text: result,
                date: new Date(),
                type: this.resultType,
            });
            if (this.scanHistory.length > MAX_HISTORY) this.scanHistory.length = MAX_HISTORY;
            this.saveHistory();
        }

        this.cdr.markForCheck();

        try { await Haptics.impact({style: ImpactStyle.Medium}); } catch {}
    }

    // ── Actions ───────────────────────────────────────────

    async copyToClipboard(text: string) {
        try {
            await navigator.clipboard.writeText(text);
            await this.toast('Copied to clipboard ✓', 'success', 'checkmark-circle-outline');
        } catch {
            await this.toast('Failed to copy', 'danger', 'close-circle-outline');
        }
    }

    async openUrl(url: string) {
        try { await Browser.open({url}); } catch { window.open(url, '_blank'); }
    }

    async openActionable(text: string, type: ScanResultType) {
        let url = text;
        if (type === 'email' && !text.startsWith('mailto:')) url = `mailto:${text}`;
        if (type === 'phone' && !text.startsWith('tel:')) url = `tel:${text}`;
        try { await Browser.open({url}); } catch { window.open(url, '_blank'); }
    }

    // ── Type helpers (called once per scan, cached) ───────

    detectType(text: string): ScanResultType {
        const t = text.trim();
        if (/^https?:\/\//i.test(t)) return 'url';
        if (/^WIFI:/i.test(t)) return 'wifi';
        if (/^mailto:/i.test(t) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t)) return 'email';
        if (/^tel:/i.test(t) || /^\+?\d[\d\s\-()]{6,}$/.test(t)) return 'phone';
        if (/^geo:/i.test(t)) return 'geo';
        return 'text';
    }

    getTypeLabel(t: ScanResultType) {
        return {url: 'URL', wifi: 'Wi-Fi', email: 'Email', phone: 'Phone', geo: 'Location', text: 'Text'}[t];
    }
    getTypeIcon(t: ScanResultType) {
        return {url: 'link-outline', wifi: 'wifi-outline', email: 'mail-outline', phone: 'call-outline', geo: 'location-outline', text: 'text-outline'}[t];
    }
    getTypeColor(t: ScanResultType) {
        return {url: 'secondary', wifi: 'warning', email: 'success', phone: 'primary', geo: 'danger', text: 'primary'}[t];
    }
    isActionable(t: ScanResultType) {
        return t === 'url' || t === 'email' || t === 'phone' || t === 'geo';
    }

    // ── Scanner control ───────────────────────────────────

    clearResult() {
        this.scannedResult = null;
        this.latestScanTime = null;
        this.resultType = 'text';
        this.resultIsUrl = false;
        this.resumeScanner();
    }

    private pauseScanner() {
        this.scannerEnabled = false;
        this.scannerPaused = true;
        if (this.resumeTimer) clearTimeout(this.resumeTimer);
        this.resumeTimer = setTimeout(() => {
            this.scannerEnabled = true;
            this.scannerPaused = false;
            this.cdr.markForCheck();
        }, PAUSE_MS);
    }

    private resumeScanner() {
        if (this.resumeTimer) clearTimeout(this.resumeTimer);
        this.scannerEnabled = true;
        this.scannerPaused = false;
        this.cdr.markForCheck();
    }

    // ── History ───────────────────────────────────────────

    selectFromHistory(item: ScanHistoryItem) {
        this.scannedResult = item.text;
        this.latestScanTime = item.date;
        this.resultType = item.type;
        this.resultIsUrl = item.type === 'url';
        this.cdr.markForCheck();
    }

    clearHistory() {
        this.scanHistory = [];
        this.saveHistory();
        this.cdr.markForCheck();
    }

    private saveHistory() {
        try { localStorage.setItem(HISTORY_KEY, JSON.stringify(this.scanHistory)); } catch {}
    }

    private loadHistory() {
        try {
            const raw = localStorage.getItem(HISTORY_KEY);
            if (raw) {
                const arr = JSON.parse(raw);
                this.scanHistory = Array.isArray(arr) ? arr.map((i: any) => ({
                    id: i.id || `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                    text: i.text,
                    date: new Date(i.date),
                    type: i.type || this.detectType(i.text),
                })) : [];
            }
        } catch { this.scanHistory = []; }
    }

    private async toast(message: string, color: string, icon: string) {
        const t = await this.toastController.create({message, color, icon, duration: 2000, position: 'bottom', cssClass: 'copy-toast'});
        await t.present();
    }
}
