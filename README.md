# QuickQR

> Fast, private, and 100% offline QR code scanner & generator for Android. Built natively with Kotlin and Jetpack Compose.

<p align="center">
  <a href="https://github.com/mustafaer/quickqr/releases"><img src="https://img.shields.io/github/v/release/mustafaer/quickqr?style=flat-square" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/mustafaer/quickqr?style=flat-square" alt="License" /></a>
  <a href="https://play.google.com/store/apps/details?id=net.mustafaer.quickqr"><img src="https://img.shields.io/badge/Google%20Play-Download-brightgreen?style=flat-square&logo=google-play" alt="Google Play" /></a>
</p>

---

QuickQR is a lightweight QR code scanner, barcode reader, and QR generator for Android. It works entirely on your device: it does not request the `INTERNET` permission, carries no trackers, and shows no ads.

## ✨ Features

- ⚡ **Fast scanning** — Google ML Kit reads QR codes and common 1D/2D barcodes straight from the camera preview, or from an image in your gallery.
- 🧠 **Knows what it read** — Links, Wi-Fi networks, contacts (vCard/MECARD), calendar events, emails, phone numbers, SMS and geo coordinates are recognised automatically, with the right action offered for each: join the network, add the contact, save the event, dial, or open.
- 🎨 **QR generator** — Build codes for plain text, links, Wi-Fi credentials, email, phone numbers and SMS from purpose-built forms. Save to your gallery or share directly.
- 🔒 **Private by construction** — No `INTERNET` permission, so nothing can be uploaded even in principle. History and settings never leave the device.
- 📁 **Searchable history** — The last 100 scans, stored locally, searchable in every supported language, exportable to CSV or JSON.
- 🌍 **Five languages** — English, Turkish, German, Hindi and Arabic, with full right-to-left support and per-app language selection on Android 13+.
- 🌗 **Material 3** — Light, dark or system theme, with optional Material You wallpaper colours on Android 12+.

## 🛠️ Tech stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **Camera** | CameraX |
| **Scanning** | Google ML Kit Barcode Scanning (on-device) |
| **Generation** | ZXing |
| **Storage** | Room (history), Preferences DataStore (settings) |
| **Build** | Gradle Kotlin DSL with a version catalog, R8 for release builds |
| **Platform** | minSdk 23, targetSdk 36, compileSdk 36 |

## 🚀 Getting started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) Koala/Ladybug or newer
- JDK 21
- Android SDK 36

### Build

```bash
git clone https://github.com/mustafaer/quickqr.git
```

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest lintDebug
```

`assembleRelease` produces an **unsigned** APK — release signing is configured in
Android Studio (*Build → Generate Signed App Bundle / APK*) rather than in the
build script, so no keystore or credentials live in this repository.

## 📦 Project structure

```text
quickqr/
├── .github/workflows/ci.yml      # Unit tests, lint, unsigned release build
├── app/
│   ├── proguard-rules.pro
│   ├── build.gradle.kts
│   ├── schemas/                  # Exported Room schemas, committed for migrations
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/net/mustafaer/quickqr/
│       │   │   ├── data/         # Room, DAO, DataStore, AppLanguage, ThemeMode
│       │   │   ├── ui/
│       │   │   │   ├── screens/
│       │   │   │   ├── components/
│       │   │   │   ├── theme/
│       │   │   │   └── viewmodel/
│       │   │   ├── utils/        # Parsing, generation, export — no Android deps
│       │   │   └── MainActivity.kt
│       │   └── res/              # Strings in 5 languages, backup rules, locales
│       └── test/                 # JVM unit tests for the utils layer
├── gradle/libs.versions.toml     # Dependency version catalog
└── build.gradle.kts
```

The `utils` package is deliberately free of Android framework types so parsing,
payload building and export logic can be covered by plain JVM unit tests.

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) — it covers the build commands, the code
standards, and what adding a new language involves.

## 📄 License

MIT — see [LICENSE](LICENSE).

## 👤 Author

**Mustafa ER** — MEDEV Studios

- Website: [medevstudios.com](https://medevstudios.com)
- Privacy policy: [QuickQR privacy policy](https://medevstudios.com/quickqr/privacy-policy.html)
- GitHub: [@mustafaer](https://github.com/mustafaer)

## 💖 Support

- ⭐ Star this repository
- ☕ [Buy me a coffee](https://www.buymeacoffee.com/mustafaer)
- 🎉 [Patreon](https://www.patreon.com/mustafaer)
