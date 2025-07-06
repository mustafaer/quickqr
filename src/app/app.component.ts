import {Component} from '@angular/core';
import {
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonContent,
    IonHeader, IonIcon,
    IonTitle,
    IonToolbar
} from '@ionic/angular/standalone';
import {ZXingScannerModule} from "@zxing/ngx-scanner";
import {BarcodeFormat} from "@zxing/library";

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    imports: [IonCard, IonCardContent, IonCardHeader, IonContent, IonHeader, IonTitle, IonToolbar, ZXingScannerModule, IonIcon, IonButton],
})
export class AppComponent {
    scannedResult: string | null = null;
    qrCodeFormat = [BarcodeFormat.QR_CODE];


    constructor() {
    }

    onCodeResult(result: string) {
        this.scannedResult = result;
    }

    copyToClipboard(text: string) {
        if (navigator && navigator.clipboard) {
            navigator.clipboard.writeText(text);
        } else {
            // Fallback for older browsers
            const textarea = document.createElement('textarea');
            textarea.value = text;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
        }
    }
}
