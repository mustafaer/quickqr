import type {CapacitorConfig} from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'net.mustafaer.quickqr',
    appName: 'QuickQR',
    webDir: 'www',
    android: {
        backgroundColor: '#6C5CE7',
    },
    plugins: {
        SplashScreen: {
            launchShowDuration: 2000,
            backgroundColor: "#6C5CE7",
            androidSplashResourceName: "splash",
            androidScaleType: "CENTER_CROP",
            showSpinner: false,
            splashFullScreen: true,
            splashImmersive: true,
        },
        StatusBar: {
            overlaysWebView: false,
            style: 'DARK',
            backgroundColor: '#6C5CE7',
        },
        Keyboard: {
            resize: 'body',
            resizeOnFullScreen: true,
        },
    }
};

export default config;
