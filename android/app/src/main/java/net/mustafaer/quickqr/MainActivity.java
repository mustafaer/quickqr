package net.mustafaer.quickqr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.PermissionRequest;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;

public class MainActivity extends BridgeActivity {

    private static final int CAMERA_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Extend Capacitor's WebChromeClient to auto-grant WebView
        // camera/microphone permission requests.
        this.bridge.getWebView().setWebChromeClient(
                new BridgeWebChromeClient(this.bridge) {
                    @Override
                    public void onPermissionRequest(final PermissionRequest request) {
                        runOnUiThread(() -> request.grant(request.getResources()));
                    }
                }
        );

        // Check native CAMERA permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            // Already granted — notify WebView after page loads
            signalCameraPermission(true);
        } else {
            // Request permission — dialog will appear
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            signalCameraPermission(granted);
        }
    }

    /**
     * Notify the WebView (Angular app) about camera permission status.
     * Sets a global variable AND dispatches a custom event.
     * - If Angular loads BEFORE this signal: it reads the event
     * - If Angular loads AFTER this signal: it reads window.nativeCameraGranted
     */
    private void signalCameraPermission(boolean granted) {
        String js = String.format(
                "window.nativeCameraGranted = %b; " +
                "window.dispatchEvent(new CustomEvent('nativeCameraPermission', " +
                "  {detail: {granted: %b}}));",
                granted, granted
        );

        // Retry signaling several times to cover page load timing
        for (int delay = 500; delay <= 3000; delay += 500) {
            final int d = delay;
            this.bridge.getWebView().postDelayed(() ->
                    this.bridge.getWebView().evaluateJavascript(js, null),
                    d
            );
        }
    }
}
