import {Component, ChangeDetectionStrategy, ChangeDetectorRef, ViewChild, OnInit, ElementRef} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
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
    IonItemOption,
    IonItemOptions,
    IonItemSliding,
    IonLabel,
    IonList,
    IonNote,
    IonSearchbar,
    IonTitle,
    IonToggle,
    IonToolbar,
    AlertController,
    ToastController,
} from '@ionic/angular/standalone';
import {ZXingScannerModule, ZXingScannerComponent} from "@zxing/ngx-scanner";
import {BarcodeFormat} from "@zxing/library";
import {Haptics, ImpactStyle} from '@capacitor/haptics';
import {Browser} from '@capacitor/browser';
import {addIcons} from 'ionicons';
import {
    calendarOutline,
    cameraReverseOutline,
    chatbubbleOutline,
    checkmarkCircle,
    checkmarkCircleOutline,
    chevronForwardOutline,
    closeCircleOutline,
    closeOutline,
    copyOutline,
    createOutline,
    downloadOutline,
    eyeOffOutline,
    eyeOutline,
    flash,
    flashOutline,
    imageOutline,
    keyOutline,
    linkOutline,
    lockClosedOutline,
    locationOutline,
    mailOutline,
    callOutline,
    openOutline,
    personOutline,
    qrCodeOutline,
    refreshOutline,
    scanOutline,
    settingsOutline,
    shareOutline,
    textOutline,
    trashOutline,
    wifiOutline,
    repeatOutline,
    rocketOutline,
    shieldCheckmarkOutline,
    colorWandOutline,
} from 'ionicons/icons';
import QRCode from 'qrcode';

import {ScanResultType, WifiData, DEBOUNCE_MS, RESULT_TRUNCATE_LENGTH} from './models/scan.model';
import {TypeDetectorService} from './services/type-detector.service';
import {HistoryService} from './services/history.service';
import {SettingsService} from './services/settings.service';
import {I18nService} from './services/i18n.service';

type AppView = 'scanner' | 'generator' | 'settings' | 'onboarding';

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DatePipe, FormsModule,
        IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle,
        IonChip, IonContent, IonHeader, IonTitle, IonToolbar,
        IonIcon, IonButton, IonList, IonItem, IonLabel, IonNote,
        IonItemSliding, IonItemOptions, IonItemOption,
        IonSearchbar, IonToggle,
        ZXingScannerModule,
    ],
})
export class AppComponent implements OnInit {
    @ViewChild(ZXingScannerComponent) scanner!: ZXingScannerComponent;
    @ViewChild('qrCanvas') qrCanvas!: ElementRef<HTMLCanvasElement>;

    // ── Template-accessible constants ─────────────────────
    readonly truncateLength = RESULT_TRUNCATE_LENGTH;

    // ── View state ────────────────────────────────────────
    currentView: AppView = 'scanner';
    onboardingSlide = 0;

    // ── Scanner state ─────────────────────────────────────
    scannerEnabled = false;
    torchEnabled = false;
    scannerPaused = false;
    scannedResult: string | null = null;
    latestScanTime: Date | null = null;

    // ── Cached result data ────────────────────────────────
    resultType: ScanResultType = 'text';
    wifiData: WifiData | null = null;
    vcardName: string | null = null;
    eventSummary: string | null = null;
    showWifiPassword = false;

    // ── Permission & camera ───────────────────────────────
    cameraPermissionDenied = false;
    insecureContext = false;
    hasCamera = true;
    availableCameras: MediaDeviceInfo[] = [];
    private activeCameraIndex = 0;
    private cameraReady = false;

    // ── History search ────────────────────────────────────
    historySearchQuery = '';

    // ── QR Generator ──────────────────────────────────────
    generatorText = '';
    generatorQrDataUrl: string | null = null;

    // ── Formats ───────────────────────────────────────────
    readonly scanFormats = [
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
    ];

    // ── Internal ──────────────────────────────────────────
    private lastScanTime = 0;

    constructor(
        readonly typeDetector: TypeDetectorService,
        readonly history: HistoryService,
        readonly settings: SettingsService,
        readonly i18n: I18nService,
        private toastController: ToastController,
        private alertController: AlertController,
        private cdr: ChangeDetectorRef,
    ) {
        addIcons({
            calendarOutline,
            cameraReverseOutline,
            chatbubbleOutline,
            checkmarkCircle,
            checkmarkCircleOutline,
            chevronForwardOutline,
            closeCircleOutline,
            closeOutline,
            copyOutline,
            createOutline,
            downloadOutline,
            eyeOffOutline,
            eyeOutline,
            flash,
            flashOutline,
            imageOutline,
            keyOutline,
            linkOutline,
            lockClosedOutline,
            locationOutline,
            mailOutline,
            callOutline,
            openOutline,
            personOutline,
            qrCodeOutline,
            refreshOutline,
            scanOutline,
            settingsOutline,
            shareOutline,
            textOutline,
            trashOutline,
            wifiOutline,
            repeatOutline,
            rocketOutline,
            shieldCheckmarkOutline,
            colorWandOutline,
        });
    }

    ngOnInit() {
        // Show onboarding on first launch
        if (!this.settings.onboardingComplete) {
            this.currentView = 'onboarding';
        }
        this.waitForCameraPermission();
    }

    // ── Camera permission (native bridge) ─────────────────

    private waitForCameraPermission() {
        const win = window as any;

        if (win.nativeCameraGranted === true) {
            this.enableScanner();
            return;
        }

        if (win.nativeCameraGranted === false) {
            this.cameraPermissionDenied = true;
            this.cdr.markForCheck();
            return;
        }

        window.addEventListener('nativeCameraPermission', ((event: CustomEvent) => {
            if (event.detail?.granted) {
                this.enableScanner();
            } else {
                this.cameraPermissionDenied = true;
                this.cdr.markForCheck();
            }
        }) as EventListener, {once: true});

        setTimeout(() => {
            if (!this.scannerEnabled && !this.cameraPermissionDenied) {
                this.webFallbackCheck();
            }
        }, 5000);
    }

    private enableScanner() {
        this.cameraPermissionDenied = false;
        this.scannerEnabled = true;
        this.cdr.markForCheck();
    }

    private async webFallbackCheck() {
        if (!navigator.mediaDevices?.getUserMedia) {
            this.insecureContext = true;
            this.cameraPermissionDenied = true;
            this.cdr.markForCheck();
            return;
        }
        try {
            const stream = await navigator.mediaDevices.getUserMedia({video: true});
            stream.getTracks().forEach(t => t.stop());
            this.enableScanner();
        } catch {
            this.cameraPermissionDenied = true;
            this.cdr.markForCheck();
        }
    }

    // ── View navigation ───────────────────────────────────

    openGenerator() {
        this.currentView = 'generator';
        this.generatorText = '';
        this.generatorQrDataUrl = null;
        this.cdr.markForCheck();
    }

    openSettings() {
        this.currentView = 'settings';
        this.cdr.markForCheck();
    }

    closeOverlay() {
        this.currentView = 'scanner';
        this.cdr.markForCheck();
    }

    // ── Onboarding ────────────────────────────────────────

    nextOnboardingSlide() {
        if (this.onboardingSlide < 2) {
            this.onboardingSlide++;
        } else {
            this.completeOnboarding();
        }
        this.cdr.markForCheck();
    }

    completeOnboarding() {
        this.settings.completeOnboarding();
        this.currentView = 'scanner';
        this.cdr.markForCheck();
    }

    // ── zxing-scanner events ──────────────────────────────

    onCamerasFound(cameras: MediaDeviceInfo[]) {
        this.availableCameras = cameras;
        this.hasCamera = cameras.length > 0;
        this.cameraReady = cameras.length > 0;
        this.cameraPermissionDenied = false;

        if (cameras.length > 0) {
            const backIdx = cameras.findIndex(c => /back|rear|environment/i.test(c.label));
            this.activeCameraIndex = backIdx >= 0 ? backIdx : 0;

            if (this.scanner) {
                this.scanner.device = cameras[this.activeCameraIndex];
            }
        }
        this.cdr.markForCheck();
    }

    onPermissionResponse(granted: boolean) {
        if (granted) {
            this.cameraPermissionDenied = false;
            this.cdr.markForCheck();
        }
    }

    retryPermission() {
        this.cameraPermissionDenied = false;
        this.scannerEnabled = true;
        this.cdr.markForCheck();
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
        this.resultType = this.typeDetector.detect(result);

        this.wifiData = this.resultType === 'wifi' ? this.typeDetector.parseWifi(result) : null;
        this.vcardName = this.resultType === 'vcard' ? this.typeDetector.parseVCardName(result) : null;
        this.eventSummary = this.resultType === 'calendar' ? this.typeDetector.parseEventSummary(result) : null;
        this.showWifiPassword = false;

        // Pause scanner
        this.scannerEnabled = false;
        this.scannerPaused = true;

        this.history.add(result, this.resultType);
        this.cdr.markForCheck();

        // Haptic feedback (if enabled)
        if (this.settings.hapticEnabled) {
            try {
                await Haptics.impact({style: ImpactStyle.Medium});
            } catch {
                // Haptics unavailable — non-critical
            }
        }

        // Continuous/batch mode: auto-resume after 3 seconds
        if (this.settings.continuousMode) {
            setTimeout(() => {
                this.scannedResult = null;
                this.scannerPaused = false;
                this.scannerEnabled = true;
                this.cdr.markForCheck();
            }, 3000);
        }
    }

    // ── Actions ───────────────────────────────────────────

    async copyToClipboard(text: string) {
        try {
            await navigator.clipboard.writeText(text);
            await this.toast(this.i18n.t('action.copied'), 'success', 'checkmark-circle-outline');
        } catch (err) {
            console.warn('[Clipboard] Copy failed:', err);
            await this.toast(this.i18n.t('action.copyFailed'), 'danger', 'close-circle-outline');
        }
    }

    async openActionable(text: string, type: ScanResultType) {
        const url = this.typeDetector.getActionUrl(text, type);
        try {
            await Browser.open({url});
        } catch (err) {
            console.warn('[Browser] Capacitor Browser failed:', err);
            window.open(url, '_blank');
        }
    }

    // ── Scanner control ───────────────────────────────────

    scanAgain() {
        this.scannedResult = null;
        this.latestScanTime = null;
        this.resultType = 'text';
        this.wifiData = null;
        this.vcardName = null;
        this.eventSummary = null;
        this.showWifiPassword = false;
        this.scannerEnabled = true;
        this.scannerPaused = false;
        this.cdr.markForCheck();
    }

    // ── Gallery scan ──────────────────────────────────────

    async scanFromGallery() {
        try {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = 'image/*';
            input.onchange = async () => {
                const file = input.files?.[0];
                if (!file) return;
                await this.decodeImageFile(file);
            };
            input.click();
        } catch (err) {
            console.warn('[Gallery] Failed to open gallery:', err);
            await this.toast('Failed to open gallery', 'danger', 'close-circle-outline');
        }
    }

    private async decodeImageFile(file: File) {
        const {BrowserMultiFormatReader} = await import('@zxing/library');
        const reader = new BrowserMultiFormatReader();

        const img = document.createElement('img');
        const url = URL.createObjectURL(file);
        img.src = url;

        await new Promise<void>((resolve) => {
            img.onload = () => resolve();
        });

        try {
            const result = await reader.decodeFromImageElement(img);
            URL.revokeObjectURL(url);
            if (result) {
                await this.onCodeResult(result.getText());
            }
        } catch {
            URL.revokeObjectURL(url);
            await this.toast('No QR code found in image', 'warning', 'close-circle-outline');
        }
    }

    // ── History ───────────────────────────────────────────

    get filteredHistory() {
        return this.history.search(this.historySearchQuery);
    }

    selectFromHistory(item: { text: string; date: Date; type: ScanResultType }) {
        this.scannedResult = item.text;
        this.latestScanTime = item.date;
        this.resultType = item.type;
        this.wifiData = item.type === 'wifi' ? this.typeDetector.parseWifi(item.text) : null;
        this.vcardName = item.type === 'vcard' ? this.typeDetector.parseVCardName(item.text) : null;
        this.eventSummary = item.type === 'calendar' ? this.typeDetector.parseEventSummary(item.text) : null;
        this.showWifiPassword = false;
        this.cdr.markForCheck();
    }

    removeHistoryItem(id: string) {
        this.history.remove(id);
        this.cdr.markForCheck();
    }

    async confirmClearHistory() {
        const alert = await this.alertController.create({
            header: this.i18n.t('history.clearConfirm.title'),
            message: this.i18n.t('history.clearConfirm.message'),
            cssClass: 'confirm-alert',
            buttons: [
                {text: this.i18n.t('history.clearConfirm.cancel'), role: 'cancel'},
                {
                    text: this.i18n.t('history.clearConfirm.confirm'),
                    role: 'destructive',
                    handler: () => {
                        this.history.clear();
                        this.cdr.markForCheck();
                    },
                },
            ],
        });
        await alert.present();
    }

    // ── History export ────────────────────────────────────

    async exportHistory(format: 'json' | 'csv') {
        const data = format === 'json' ? this.history.exportJSON() : this.history.exportCSV();
        const filename = `quickqr_history.${format}`;
        const mimeType = format === 'json' ? 'application/json' : 'text/csv';

        try {
            // Try native Share API first
            const blob = new Blob([data], {type: mimeType});
            const file = new File([blob], filename, {type: mimeType});

            if (navigator.share && navigator.canShare?.({files: [file]})) {
                await navigator.share({files: [file], title: 'QuickQR History'});
            } else {
                // Fallback: download
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                a.click();
                URL.revokeObjectURL(url);
            }
            await this.toast(this.i18n.t('history.exported'), 'success', 'checkmark-circle-outline');
        } catch (err) {
            console.warn('[Export] Failed:', err);
        }
    }

    async showExportOptions() {
        const alert = await this.alertController.create({
            header: this.i18n.t('history.export.title'),
            buttons: [
                {
                    text: this.i18n.t('history.export.json'),
                    handler: () => this.exportHistory('json'),
                },
                {
                    text: this.i18n.t('history.export.csv'),
                    handler: () => this.exportHistory('csv'),
                },
                {text: this.i18n.t('history.clearConfirm.cancel'), role: 'cancel'},
            ],
        });
        await alert.present();
    }

    // ── QR Generator ──────────────────────────────────────

    async generateQR() {
        if (!this.generatorText.trim()) return;
        try {
            this.generatorQrDataUrl = await QRCode.toDataURL(this.generatorText, {
                width: 300,
                margin: 2,
                color: {dark: '#000000', light: '#ffffff'},
                errorCorrectionLevel: 'H',
            });
            this.cdr.markForCheck();
        } catch (err) {
            console.warn('[Generator] Failed to generate QR:', err);
            await this.toast('Failed to generate QR code', 'danger', 'close-circle-outline');
        }
    }

    async shareQR() {
        if (!this.generatorQrDataUrl) return;
        try {
            // Convert data URL to blob
            const res = await fetch(this.generatorQrDataUrl);
            const blob = await res.blob();
            const file = new File([blob], 'quickqr.png', {type: 'image/png'});

            if (navigator.share && navigator.canShare?.({files: [file]})) {
                await navigator.share({files: [file], title: 'QR Code'});
            } else {
                // Fallback: download
                const a = document.createElement('a');
                a.href = this.generatorQrDataUrl;
                a.download = 'quickqr.png';
                a.click();
            }
        } catch (err) {
            console.warn('[Generator] Share failed:', err);
        }
    }

    async saveQR() {
        if (!this.generatorQrDataUrl) return;
        const a = document.createElement('a');
        a.href = this.generatorQrDataUrl;
        a.download = 'quickqr.png';
        a.click();
        await this.toast('QR code saved ✓', 'success', 'checkmark-circle-outline');
    }

    // ── Settings ──────────────────────────────────────────

    toggleHaptic() {
        this.settings.toggleHaptic();
        this.cdr.markForCheck();
    }

    toggleContinuousMode() {
        this.settings.toggleContinuousMode();
        this.cdr.markForCheck();
    }

    setLanguage(lang: 'en' | 'tr') {
        this.settings.setLanguage(lang);
        this.cdr.markForCheck();
    }

    // ── Helpers ───────────────────────────────────────────

    private async toast(message: string, color: string, icon: string) {
        const t = await this.toastController.create({
            message,
            color,
            icon,
            duration: 2000,
            position: 'bottom',
            cssClass: 'copy-toast',
        });
        await t.present();
    }
}
