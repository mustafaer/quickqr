# QuickQR

> A fast, private QR code scanner and generator for Android that works fully offline. Native Kotlin and Jetpack Compose, open source under the MIT license.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=net.mustafaer.quickqr"><img src="https://img.shields.io/badge/Google%20Play-Download-brightgreen?style=flat-square&logo=google-play" alt="Google Play" /></a>
  <img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 6.0+" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/mustafaer/quickqr?style=flat-square" alt="License" /></a>
  <a href="https://github.com/mustafaer/quickqr/commits/development"><img src="https://img.shields.io/github/last-commit/mustafaer/quickqr/development?style=flat-square" alt="Last commit" /></a>
</p>

---

QuickQR scans QR codes and barcodes, shows you what a code really contains before you act on it, and makes new codes. Everything happens on your phone. QuickQR has no internet access at all: no accounts, no ads, no analytics, and nothing you scan is uploaded. [Privacy](#-privacy) explains how that holds.

## ✨ Features

- ⚡ **Fast scanning** — Reads QR codes, Data Matrix, Aztec, PDF417 and the common product and shipping barcodes (EAN, UPC, Code 128, Code 39), from the camera or from a photo. The scanner zooms in by itself when a code is too small to read, and supports pinch-to-zoom and tap-to-focus. Continuous mode saves one code after another without stopping.
- 🧠 **Knows what it read** — Links, Wi-Fi networks (including WPA3), contacts (vCard/MECARD), calendar events, UPI payment codes, emails, phone numbers, SMS and geo coordinates are recognised automatically, with the right action offered for each: join the network, add the contact, save the event, pay, dial, or open.
- 🛡️ **Checks links before you open them** — Every scanned link is inspected on the device. The real destination is shown first, and the scanner calls out:
  - look-alike domains (`paypa1.com`, Cyrillic `аpple.com`)
  - a brand's name placed in front of someone else's site (`paypal.com.secure-login.xyz`)
  - hidden `user@host` tricks
  - bare IP addresses, unencrypted `http` and short links

  A link with a serious warning opens only through a deliberate "Open anyway".
- 💸 **Safer UPI payments** — A UPI code shows who is being paid and how much, and reminds you that scanning a QR can only ever send money.
- 🎨 **QR generator** — Build codes for plain text, links, Wi-Fi (WPA/WPA2, WPA3, WEP or open), email, phone numbers and SMS from purpose-built forms, with a live preview.
  - Error correction is picked automatically: the strongest level that keeps the code easy to scan.
  - A code holds up to 2,953 bytes.
  - Save a sharp, print-ready PNG to your gallery or share it directly.
- 🔑 **Careful with passwords** — Wi-Fi passwords never appear in the history list or in the messages continuous mode shows. A copied password is marked sensitive, so Android 13+ hides it in the clipboard preview. Screenshots are blocked while a password is revealed.
- 📁 **Searchable history** — The last 100 scans plus any you pin, stored on the device, searchable in every supported language, exportable to CSV or JSON. Deleting a scan can be undone.
- 🌍 **Five languages** — English, Turkish, German, Hindi and Arabic, with full right-to-left support. Pick one in the app's settings, or on Android 13+ in the system's per-app language settings.
- 🌗 **Material 3** — Light, dark or system theme, with optional Material You wallpaper colours on Android 12+.

## 🔒 Privacy

QuickQR has no internet access. Android lets an app open a network connection only if the app holds the `INTERNET` permission, and QuickQR doesn't. Neither QuickQR nor any library inside it can upload what you scan, your history or your settings.

- **History is excluded from Google's cloud backup.** It only moves in a device-to-device transfer you start yourself. Settings are included in backups.
- **Photos come through the system photo picker**, so QuickQR can't see the rest of your gallery.

**About ML Kit.** Scanning uses Google's ML Kit with its barcode model built into the app, so it needs no connection. The library does add the `INTERNET` permission, to send usage data to Google: device model, app version, and performance and error metrics. Google lists all of it in its [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).

QuickQR removes that permission in its manifest. ML Kit's upload attempts fail on the device, and nothing is sent. Releases up to 2.3.0 still include the permission.

### Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Scanning. |
| `VIBRATE` | The optional vibration when a code is read. |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and older only) | Saving a generated code to the gallery. Newer Android versions need no permission for this. |
| `ACCESS_NETWORK_STATE` | QuickQR's own code doesn't use it; ML Kit and CameraX add it. It only lets an app check whether the device is online, and can't be used to send anything. |

The release APK also lists `net.mustafaer.quickqr.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX creates this private permission so that only QuickQR itself can reach its internal broadcast receivers. After a release build, `app/build/outputs/logs/manifest-merger-release-report.txt` shows which library adds each permission.

## 📲 Download

QuickQR is on [Google Play](https://play.google.com/store/apps/details?id=net.mustafaer.quickqr). There are no prebuilt APKs on GitHub; to sideload it, [build it from source](#-getting-started).

## 🛠️ Tech stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **Camera** | CameraX `LifecycleCameraController` with ML Kit's CameraX analyzer |
| **Scanning** | Google ML Kit Barcode Scanning, with the model bundled in the app |
| **Generation** | ZXing (encoder only) |
| **Storage** | Room (history), Preferences DataStore (settings) |
| **Architecture** | One ViewModel per tab, manual dependency injection |
| **Tests** | JUnit 4 and kotlinx-coroutines-test: 94 JVM unit tests |
| **Build** | Gradle 9.5 Kotlin DSL with a version catalog, Android Gradle Plugin 9.2, R8 in release builds |
| **Platform** | Android 6.0+ (minSdk 23), targetSdk 36, compileSdk 36 |

## 🚀 Getting started

### Prerequisites

- A recent [Android Studio](https://developer.android.com/studio). The project uses Android Gradle Plugin 9.2 and is developed on Android Studio 2026.2.
- JDK 21 for Gradle. Android Studio's bundled JDK is enough to start the build. If JDK 21 isn't installed, Gradle downloads it.
- Android SDK 36. Android Studio installs it on first sync. On the command line, point `ANDROID_HOME`, or `sdk.dir` in `local.properties`, at your SDK.

### Build

```bash
git clone https://github.com/mustafaer/quickqr.git
```

```bash
cd quickqr
```

```bash
./gradlew assembleDebug
```

The debug build has a `.debug` application ID, so it installs alongside the Play version. To run the unit tests and lint:

```bash
./gradlew testDebugUnitTest lintDebug
```

### Release build

```bash
./gradlew assembleRelease
```

This produces an **unsigned**, R8-minified APK in `app/build/outputs/apk/release/`.

- **Signing** happens in Android Studio (*Build → Generate Signed App Bundle / APK*) rather than in the build script, so no keystore or credentials live in this repository.
- **R8** shrinks and obfuscates the code and writes `app/build/outputs/mapping/release/mapping.txt`. Upload that file to Play Console with each release, so crash reports show the real class names.

There is no CI. Run the tests, lint and a release build locally before opening a pull request.

## 📦 Project structure

```text
quickqr/
├── app/
│   ├── proguard-rules.pro        # R8 rules: one narrow keep for ML Kit
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
│       │   │   ├── utils/        # Parsing, link inspection, encoding, export (no Android deps)
│       │   │   ├── AppContainer.kt   # Manual dependency injection
│       │   │   ├── QuickQrApp.kt
│       │   │   └── MainActivity.kt
│       │   └── res/              # Strings in 5 languages, backup rules, locales
│       └── test/                 # JVM unit tests for utils and the ViewModels
├── fastlane/metadata/android/    # Play Store release notes, per language
├── gradle/libs.versions.toml     # Dependency version catalog
├── CHANGELOG.md
├── CONTRIBUTING.md
└── build.gradle.kts
```

The `utils` package is deliberately free of Android framework types, so parsing,
link inspection, QR encoding, payload building and export logic are covered by
plain JVM unit tests. The ViewModels take their collaborators through
constructors, so they are tested the same way with fakes.

## 🗺️ Project status

`development` is the default branch and holds the next release. Its new features:

- on-device link inspection
- UPI payments
- WPA3 networks
- pinned scans and undo
- a rebuilt scanner
- no `INTERNET` permission

They're listed under *Unreleased* in [CHANGELOG.md](CHANGELOG.md), which has the full version history.

### Known limitations

- **Link checks are offline heuristics.** A short link (`bit.ly` and the like) can't be expanded without a connection, so QuickQR can only warn that the destination is hidden. A phishing domain that imitates no known brand isn't flagged.
- **Look-alike detection covers a curated brand list.** It is weighted toward Türkiye, Germany, India and the Arabic-speaking countries.
- **The generator makes QR codes only.** Other barcode formats can be scanned, but not created.

## 🤝 Contributing

Issues and pull requests are welcome: bug reports, translations, and brands for the
look-alike list (with the brand's own registrable domain). Open pull requests
against `development`. [CONTRIBUTING.md](CONTRIBUTING.md) covers the build commands,
the code standards, and what adding a new language involves.

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
