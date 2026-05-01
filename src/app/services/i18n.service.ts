import {Injectable} from '@angular/core';
import {SettingsService} from './settings.service';

type TranslationKey = keyof typeof EN;

const EN = {
    // Header
    'app.title': 'QuickQR',
    'header.switchCamera': 'Switch camera',
    'header.torch.on': 'Turn off flashlight',
    'header.torch.off': 'Turn on flashlight',
    'header.scanNew': 'Scan new QR code',
    'header.settings': 'Settings',
    'header.generate': 'Create QR code',

    // Scanner
    'scan.empty.title': 'Scan a QR Code',
    'scan.empty.subtitle': 'Point your camera at a QR code to get started',
    'scan.result.title': 'Scan Result',
    'scan.again': 'Scan Again',
    'scan.continuous': 'Continuous',
    'scan.batch': 'Batch Mode',
    'scan.gallery': 'Gallery',

    // Result types
    'type.url': 'URL',
    'type.wifi': 'Wi-Fi',
    'type.email': 'Email',
    'type.phone': 'Phone',
    'type.geo': 'Location',
    'type.sms': 'SMS',
    'type.vcard': 'Contact',
    'type.calendar': 'Event',
    'type.text': 'Text',

    // Actions
    'action.copy': 'Copy',
    'action.copyAll': 'Copy All',
    'action.copyPassword': 'Copy Password',
    'action.open': 'Open',
    'action.copied': 'Copied to clipboard ✓',
    'action.copyFailed': 'Failed to copy',
    'action.share': 'Share',
    'action.export': 'Export',

    // Wi-Fi
    'wifi.network': 'Network',
    'wifi.security': 'Security',
    'wifi.password': 'Password',
    'wifi.hidden': 'Hidden Network',
    'wifi.showPassword': 'Show password',
    'wifi.hidePassword': 'Hide password',

    // History
    'history.title': 'Recent Scans',
    'history.clear': 'Clear',
    'history.clearConfirm.title': 'Clear History',
    'history.clearConfirm.message': 'Are you sure you want to delete all scan history?',
    'history.clearConfirm.cancel': 'Cancel',
    'history.clearConfirm.confirm': 'Clear All',
    'history.search': 'Search history...',
    'history.empty': 'No scans match your search',
    'history.export.title': 'Export History',
    'history.export.json': 'Export as JSON',
    'history.export.csv': 'Export as CSV',
    'history.exported': 'History exported ✓',

    // Generator
    'generator.title': 'Create QR Code',
    'generator.input': 'Enter text or URL',
    'generator.generate': 'Generate',
    'generator.save': 'Save Image',
    'generator.share': 'Share',
    'generator.close': 'Close',

    // Permission
    'permission.title': 'Camera Permission Required',
    'permission.message': 'Please allow camera access to scan QR codes. You may need to enable it in your device settings.',
    'permission.retry': 'Try Again',
    'permission.insecure.title': 'Camera Not Available',
    'permission.insecure.message': 'Camera requires a secure context (HTTPS).',

    // Settings
    'settings.title': 'Settings',
    'settings.haptic': 'Vibration Feedback',
    'settings.haptic.desc': 'Vibrate on successful scan',
    'settings.continuous': 'Continuous Scanning',
    'settings.continuous.desc': 'Auto-resume scanner after a scan',
    'settings.language': 'Language',
    'settings.close': 'Close',
    'settings.madeBy': 'Made with ❤️ by',

    // Onboarding
    'onboarding.slide1.title': 'Fast & Private',
    'onboarding.slide1.text': 'QuickQR uses your camera only for scanning. No data leaves your device.',
    'onboarding.slide2.title': 'Smart Detection',
    'onboarding.slide2.text': 'URLs, Wi-Fi, contacts, emails — automatically detected and ready to use.',
    'onboarding.slide3.title': 'Ready to Scan',
    'onboarding.slide3.text': 'Point your camera at any QR code to get started.',
    'onboarding.next': 'Next',
    'onboarding.start': 'Get Started',
    'onboarding.skip': 'Skip',
};

const TR: typeof EN = {
    'app.title': 'QuickQR',
    'header.switchCamera': 'Kamerayı değiştir',
    'header.torch.on': 'Feneri kapat',
    'header.torch.off': 'Feneri aç',
    'header.scanNew': 'Yeni QR kodu tara',
    'header.settings': 'Ayarlar',
    'header.generate': 'QR kodu oluştur',

    'scan.empty.title': 'QR Kod Tara',
    'scan.empty.subtitle': 'Başlamak için kameranızı bir QR koda tutun',
    'scan.result.title': 'Tarama Sonucu',
    'scan.again': 'Tekrar Tara',
    'scan.continuous': 'Sürekli',
    'scan.batch': 'Toplu Mod',
    'scan.gallery': 'Galeri',

    'type.url': 'URL',
    'type.wifi': 'Wi-Fi',
    'type.email': 'E-posta',
    'type.phone': 'Telefon',
    'type.geo': 'Konum',
    'type.sms': 'SMS',
    'type.vcard': 'Kişi',
    'type.calendar': 'Etkinlik',
    'type.text': 'Metin',

    'action.copy': 'Kopyala',
    'action.copyAll': 'Tümünü Kopyala',
    'action.copyPassword': 'Şifreyi Kopyala',
    'action.open': 'Aç',
    'action.copied': 'Panoya kopyalandı ✓',
    'action.copyFailed': 'Kopyalama başarısız',
    'action.share': 'Paylaş',
    'action.export': 'Dışa Aktar',

    'wifi.network': 'Ağ',
    'wifi.security': 'Güvenlik',
    'wifi.password': 'Şifre',
    'wifi.hidden': 'Gizli Ağ',
    'wifi.showPassword': 'Şifreyi göster',
    'wifi.hidePassword': 'Şifreyi gizle',

    'history.title': 'Son Taramalar',
    'history.clear': 'Temizle',
    'history.clearConfirm.title': 'Geçmişi Temizle',
    'history.clearConfirm.message': 'Tüm tarama geçmişini silmek istediğinizden emin misiniz?',
    'history.clearConfirm.cancel': 'İptal',
    'history.clearConfirm.confirm': 'Hepsini Sil',
    'history.search': 'Geçmişte ara...',
    'history.empty': 'Aramanızla eşleşen tarama yok',
    'history.export.title': 'Geçmişi Dışa Aktar',
    'history.export.json': 'JSON olarak aktar',
    'history.export.csv': 'CSV olarak aktar',
    'history.exported': 'Geçmiş dışa aktarıldı ✓',

    'generator.title': 'QR Kod Oluştur',
    'generator.input': 'Metin veya URL girin',
    'generator.generate': 'Oluştur',
    'generator.save': 'Resmi Kaydet',
    'generator.share': 'Paylaş',
    'generator.close': 'Kapat',

    'permission.title': 'Kamera İzni Gerekli',
    'permission.message': 'QR kodları taramak için kamera erişimine izin verin. Cihaz ayarlarından etkinleştirmeniz gerekebilir.',
    'permission.retry': 'Tekrar Dene',
    'permission.insecure.title': 'Kamera Kullanılamıyor',
    'permission.insecure.message': 'Kamera güvenli bağlantı (HTTPS) gerektirir.',

    'settings.title': 'Ayarlar',
    'settings.haptic': 'Titreşim Geri Bildirimi',
    'settings.haptic.desc': 'Başarılı taramada titret',
    'settings.continuous': 'Sürekli Tarama',
    'settings.continuous.desc': 'Taramadan sonra otomatik devam et',
    'settings.language': 'Dil',
    'settings.close': 'Kapat',
    'settings.madeBy': '❤️ ile yapıldı —',

    'onboarding.slide1.title': 'Hızlı & Gizli',
    'onboarding.slide1.text': 'QuickQR kameranızı yalnızca tarama için kullanır. Hiçbir veri cihazınızdan çıkmaz.',
    'onboarding.slide2.title': 'Akıllı Algılama',
    'onboarding.slide2.text': 'URL\'ler, Wi-Fi, kişiler, e-postalar — otomatik algılanır ve kullanıma hazırdır.',
    'onboarding.slide3.title': 'Taramaya Hazır',
    'onboarding.slide3.text': 'Başlamak için kameranızı herhangi bir QR koda tutun.',
    'onboarding.next': 'İleri',
    'onboarding.start': 'Başla',
    'onboarding.skip': 'Atla',
};

const TRANSLATIONS: Record<string, typeof EN> = {en: EN, tr: TR};

@Injectable({providedIn: 'root'})
export class I18nService {

    constructor(private settingsService: SettingsService) {}

    t(key: TranslationKey): string {
        const lang = this.settingsService.language;
        return TRANSLATIONS[lang]?.[key] || EN[key] || key;
    }

    get lang(): string {
        return this.settingsService.language;
    }
}
