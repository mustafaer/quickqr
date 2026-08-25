# Contributing to QuickQR

Thanks for your interest. QuickQR is a native Android app written in Kotlin with
Jetpack Compose — there is no Node, npm or web toolchain involved.

## Getting started

1. **Fork** the repository and clone your fork:
   ```bash
   git clone https://github.com/<your-username>/quickqr.git
   cd quickqr
   ```
2. **Open the project** in Android Studio (Koala/Ladybug or newer). Android Studio
   downloads the Gradle distribution and the Android SDK components on first sync;
   no manual dependency install step is needed.
3. **Create a branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

### Requirements

- Android Studio Koala/Ladybug or newer
- JDK 21 (the Gradle daemon toolchain is pinned to 21)
- Android SDK 36

## Development

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

`lint` is configured with `abortOnError = true`, so a lint error fails the build.
The same three commands run in CI on every pull request, plus an unsigned
`assembleRelease` to catch R8 and resource-shrinking problems that a debug build
never surfaces.

## Project layout

```text
app/src/main/java/net/mustafaer/quickqr/
├── data/          Room database, DAO, DataStore settings, AppLanguage, ThemeMode
├── ui/
│   ├── screens/   Scanner, Generator, History, Settings, Onboarding, MainContainer
│   ├── components/ScanResultSheet
│   └── theme/     Material 3 colour schemes and typography
├── utils/         TypeDetector, QrGenerator, QrPayload, HistoryExporter,
│                  HistoryFilter, HapticHelper
└── MainActivity.kt
```

## Code standards

- **Keep parsing logic out of Compose.** `TypeDetector`, `QrPayload`,
  `HistoryExporter` and `HistoryFilter` are deliberately free of Android
  framework types so they can be covered by plain JVM unit tests. If you need
  something from `android.*` in one of them, that is a sign the logic belongs
  somewhere else.
- **Add a test with the fix.** Every bug fixed in those four classes should come
  with a regression test in `app/src/test/`.
- **No hardcoded user-facing strings.** Everything the user can read or that a
  screen reader can announce goes in `res/values/strings.xml`, including
  `contentDescription` values. See the translation section below.
- **Room migrations are mandatory.** The database has no destructive fallback. If
  you change `ScanEntity`, bump the version in `AppDatabase`, write a
  `Migration`, and commit the regenerated schema JSON from `app/schemas/`.
- Follow the surrounding style: 4-space indent, trailing commas omitted, imports
  explicit except where a wildcard is already established.

## Translations

QuickQR ships in English, Turkish, German, Hindi and Arabic. Adding a language
means three things and nothing else:

1. An entry in `AppLanguage` (`data/AppLanguage.kt`) with the language code and
   its name **written in that language**.
2. A `res/values-<code>/strings.xml`.
3. A `<locale>` line in `res/xml/locales_config.xml`.

When adding or changing a string:

- Strings that must not be translated (`Wi-Fi`, `SMS`, `WPA/WPA2`, the app name)
  are marked `translatable="false"` in the default file — do not copy those into
  a locale file.
- Keep terminology consistent with the platform's own wording in that language
  rather than translating the English literally.
- Watch for words that collide. Arabic `مسح` means both "scan" and "erase", so
  clearing history deliberately uses `حذف` instead.
- Test with a long language. German and Turkish labels run considerably longer
  than English; layouts must not depend on a fixed label width.

## Pull requests

1. Make sure `./gradlew testDebugUnitTest lintDebug assembleRelease` all pass.
2. Describe what changed and why. Screenshots help for UI work — ideally in both
   light and dark, and in one right-to-left language.
3. Keep the change focused. Unrelated cleanups belong in their own PR.
