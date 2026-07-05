# QuickQR

> Fast, private, and 100% offline QR code scanner & generator for Android. Built natively using Jetpack Compose.

<p align="center">
  <img src="/Users/mustafaer/.gemini/antigravity-ide/brain/67caf8c1-e76a-4c7e-8662-7a11600127af/quickqr_feature_graphic_horizontal_1781003918274.png" alt="QuickQR Banner" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/nicemustafa/quickqr/releases"><img src="https://img.shields.io/github/v/release/nicemustafa/quickqr?style=flat-square" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/nicemustafa/quickqr?style=flat-square" alt="License" /></a>
  <a href="https://play.google.com/store/apps/details?id=net.mustafaer.quickqr"><img src="https://img.shields.io/badge/Google%20Play-Download-brightgreen?style=flat-square&logo=google-play" alt="Google Play" /></a>
</p>

---

QuickQR is a premium, lightweight, and professional-grade QR code scanner, barcode reader, and custom QR generator for Android. Built entirely from scratch with **Kotlin and Jetpack Compose**, it prioritizes speed, high-quality user experience, and absolute user privacy by working **100% offline with zero advertisements**.

## ✨ Features

- ⚡ **Lightning-Fast Scanning** — Powered by Google's ML Kit Barcode Scanning API for near-instant QR and barcode detection.
- 🎨 **Custom QR Generator** — Generate high-quality QR codes in seconds for URLs, Wi-Fi, email, phone numbers, SMS, and plain text.
- 🔒 **Privacy First (100% Offline)** — The app does **not** request `INTERNET` permission. All processing and storage happen locally on your device.
- 📁 **Searchable History & Exports** — Keep track of your scans with local database storage. Export your history easily to CSV or JSON formats.
- ⚡ **Intelligent Context Actions** — Open URLs instantly, connect to Wi-Fi networks, send emails/SMS, or call numbers directly from scan results.
- 🎨 **Modern Material 3 Design** — Full support for system Light/Dark mode, featuring beautiful transitions, responsive layouts, and standard premium buttons.
- 🔋 **Zero Bloat & Battery Efficient** — No trackers, no background processes, and zero ads to preserve your device performance.

## 🛠️ Tech Stack

- **Language:** Kotlin 2.x
- **UI Framework:** Jetpack Compose (Material 3)
- **Scanning API:** Google ML Kit Barcode Scanning & CameraX
- **Local Storage:** Jetpack Room (SQLite) for history, Preferences DataStore for settings
- **QR Code Generation:** ZXing (Zebra Crossing)
- **Platform:** Native Android (minSdk 26, targetSdk 36)
- **Build System:** Kotlin DSL (`build.gradle.kts`) with R8/ProGuard compiler optimizations

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Koala/Ladybug or newer)
- Java Development Kit (JDK) 17 or 21
- Android SDK 36 (Android 16 API level support)

### Setup & Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mustafaer/quickqr.git
   cd quickqr
   ```

2. **Open the project** in Android Studio.

3. **Build the Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Build the Optimized Release APK / App Bundle:**
   ```bash
   ./gradlew assembleRelease
   ```
   *Note: Ensure you configure your signing properties in `keystore.properties` for production releases.*

## 📦 Project Structure

```text
quickqr/
├── app/
│   ├── proguard-rules.pro        # Comprehensive 100+ line ProGuard ruleset
│   ├── build.gradle.kts          # App-level build configurations & dependencies
│   └── src/main/
│       ├── AndroidManifest.xml   # Target API 35/36 & hardened offline permissions
│       ├── java/net/mustafaer/quickqr/
│       │   ├── data/             # Room Database, DAOs, DataStore repository
│       │   ├── ui/
│       │   │   ├── screens/      # Scanner, Generator, History, Settings Screens
│       │   │   ├── components/   # Unified buttons, bottom sheets, layouts
│       │   │   └── theme/        # Material 3 colors, shapes, typography
│       │   └── MainActivity.kt
│       └── res/                  # Localized strings, backup policies, locales configs
└── build.gradle.kts              # Project-level build configurations
```

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## 👤 Author & Studio

**Mustafa ER** (Founder of MEDEV Studios)
- Website: [MEDEV Studios](https://medevstudios.com)
- Privacy Policy: [QuickQR Privacy Policy](https://medevstudios.com/quickqr/privacy-policy.html)
- Email: mustafaerpro@gmail.com
- GitHub: [@mustafaer](https://github.com/mustafaer)

## 💖 Support

If you find QuickQR useful, feel free to support:
- ⭐ Star this repository
- ☕ [Buy me a coffee](https://www.buymeacoffee.com/mustafaer)
- 🎉 [Patreon](https://www.patreon.com/mustafaer)
