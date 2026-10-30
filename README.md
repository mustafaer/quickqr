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
- 🧠 **Knows what it read** — Links, Wi-Fi networks (including WPA3), contacts (vCard/MECARD), calendar events, UPI payment codes, emails, phone numbers, SMS and geo coordinates are recognised automatically, with the right action offered for each: join the network, add the contact, save the event, pay, dial, or open.
- 🛡️ **Checks links before you open them** — Every scanned link is inspected on the device: the real destination is shown first, and look-alike domains (`paypa1.com`, Cyrillic `аpple.com`), brand names placed in front of someone else's site (`paypal.com.secure-login.xyz`), hidden `user@host` tricks, bare IP addresses and short links are called out. Nothing is uploaded to check it.
- 💸 **Safe UPI payments** — A UPI code shows who is being paid and how much, and reminds you that scanning a QR can only ever send money.
- 🎨 **QR generator** — Build codes for plain text, links, Wi-Fi credentials, email, phone numbers and SMS from purpose-built forms, with a live preview. Error correction is picked automatically: the strongest level that keeps the code small, and up to 2,953 bytes of content. Save a sharp, print-ready PNG to your gallery or share it directly.
- 🔒 **Private by construction** — No `INTERNET` permission, so nothing can be uploaded even in principle. Scan history is excluded from Google's cloud backup; it only moves in a direct device-to-device transfer you start yourself. Settings are backed up.
- 📁 **Searchable history** — The last 100 scans plus any you pin, stored locally, searchable in every supported language, exportable to CSV or JSON. Deleting a scan can be undone.
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
├── app/
│   ├── proguard-rules.pro
│   ├── build.gradle.kts
│   ├── schemas/                  # Exported Room schemas, committed for migrations
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/net/mustafaer/quickqr/
│       │   │   ├── data/         # Room, repositories, DataStore, media export, gallery reader
│       │   │   ├── ui/
│       │   │   │   ├── screens/
│       │   │   │   ├── components/   # Camera, viewfinder, result sheet, QR canvas
│       │   │   │   ├── theme/
│       │   │   │   └── viewmodel/    # One ViewModel per tab
│       │   │   ├── utils/        # Parsing, link inspection, encoding, export — no Android deps
│       │   │   ├── AppContainer.kt   # Manual dependency injection
│       │   │   ├── QuickQrApp.kt
│       │   │   └── MainActivity.kt
│       │   └── res/              # Strings in 5 languages, backup rules, locales
│       └── test/                 # JVM unit tests for the utils layer
├── gradle/libs.versions.toml     # Dependency version catalog
└── build.gradle.kts
```

The `utils` package is deliberately free of Android framework types so parsing,
link inspection, QR encoding, payload building and export logic can be covered by
plain JVM unit tests. The ViewModels take their collaborators through
constructors, so they are tested the same way with fakes.

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
