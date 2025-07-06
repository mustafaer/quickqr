import type {CapacitorConfig} from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'net.mustafaer.quickqr',
    appName: 'QuickQR',
    webDir: 'www',
    plugins: {
        SplashScreen: {
            launchShowDuration: 3000,
            backgroundColor: "#ffffff",
            androidSplashResourceName: "splash",
            androidScaleType: "CENTER_CROP",
            showSpinner: false
        }
    }
};

export default config;
