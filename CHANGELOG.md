# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

> Versions 1.x were the original Ionic/Angular/Capacitor app. Version 2.0.0 is a
> complete rewrite as a native Kotlin and Jetpack Compose application; entries
> below that line describe a different codebase.

## [2.3.0] - 2026-08-25

### Added
- **Wi-Fi connect action**: scanning a Wi-Fi QR now offers to join the network. On Android 11+ this uses the system's add-network dialog and needs no extra permission; below that the password is copied and Wi-Fi settings are opened.
- **Typed QR generator**: the Create tab now builds text, link, Wi-Fi, email, phone and SMS codes from purpose-built forms instead of one plain text field. Switching type keeps what was already typed in the others.
- **Appearance setting**: light, dark or follow-system, applied through `AppCompatDelegate` so it also drives the `-night` resources.
- **Material You**: optional wallpaper-based colours on Android 12+.
- **App version** shown in Settings.
- **Contact details**: vCard parsing now keeps every phone number and email address, and reads address, note and birthday.
- **Unit tests**: 49 tests covering payload detection, vCard/MECARD/iCalendar parsing, generator payload building, history search and export escaping.
- **CI**: GitHub Actions runs unit tests, lint and an unsigned release build on every push and pull request.

### Fixed
- **History search returned nothing** for any query containing `%`, `_` or `\` — a regression shipped in 2.2.1. Wildcards were escaped in Kotlin but the SQL `LIKE` had no `ESCAPE` clause, so the backslashes were matched literally. Search now runs in Kotlin, which also makes it case-insensitive in Turkish, German, Hindi and Arabic — SQLite's `LIKE` only folds ASCII case.
- **Backup and restore saved nothing.** Both backup rule files listed only `sharedpref`, but settings live in Preferences DataStore (`files/datastore/`) and history lives in the Room database. All three are now included.
- **A schema change would have wiped every user's history.** The destructive migration fallback is gone and the schema is exported to `app/schemas/` so real migrations can be written against it.
- **All-day events were never detected**: the check searched the whole regex match for a `T`, which the property name `DTSTART` always contains.
- **`N:` fallback on a vCard** swallowed the rest of the card when `FN` was absent.
- **Folded vCard and iCalendar lines** are now unfolded, so a wrapped note or description is no longer truncated at the fold.
- **`\\n` in a vCard value** became a newline instead of a backslash followed by `n`; escapes are now resolved in one left-to-right pass.
- **`https://user@host.tld` was classified as an email**, so Open launched a mail client instead of a browser.
- **An SMS QR with an empty message** produced a dangling `?body=`.
- **QR generation failures were silent.** Content that exceeds a QR code's capacity now says so, with the actual and maximum byte counts, instead of leaving the empty placeholder on screen.
- **A permanently denied camera permission was a dead end** — "Try again" could no longer show the system dialog. The screen now offers to open app settings.
- **Gallery scans in continuous mode** showed only a truncated toast; they now open the result sheet like any other deliberate scan.
- **CSV exports were open to formula injection.** A scanned code starting with `=`, `+`, `-` or `@` executed when the export was opened in a spreadsheet.
- **Export ignored the active search filter**, always writing the full history.
- **Silent failures** on export, sharing and the privacy-policy link now report what went wrong.
- **The FileProvider was configured with access to the entire external storage root**, which the app never used.
- **A purple flash on cold start**: the window background was the brand colour and ignored dark mode. It now matches the Compose surface in both themes.
- **The adaptive icon background was inset by 16.7%**, leaving transparent corners under a launcher's mask.
- **Stale QR image**: a generation job cancelled mid-encode could still publish its result over a newer one.
- **Double `ImageProxy.close()`** on the camera analysis failure path, introduced in 2.2.1: `addOnCompleteListener` already covered failures.

### Changed
- The scanner stays in the composition across tab switches, so returning to it no longer rebuilds the preview, the analysis executor and the ML Kit client — only the camera use cases unbind.
- Barcodes are only accepted from the central region of the frame, so a code at the very edge of the darkened area is no longer read and saved without being aimed at.
- Scan-result detail rows stack the label above the value instead of using a fixed 100 dp label column, which clipped longer German, Turkish, Hindi and Arabic labels.
- Toasts replaced with snackbars, so feedback follows the app's language and theme.
- Turkish, German, Hindi and Arabic strings revised for terminology and consistency. `Wi-Fi`, `SMS` and `QR` are no longer transliterated in some strings and Latin in others; Arabic no longer uses `مسح` for both "scan" and "clear history".
- Dependencies moved to a Gradle version catalog; the Compose BOM is declared once.
- Debug builds carry a `.debug` application ID suffix so they install alongside the Play release.

## [2.2.1] - 2026-07-21

### Fixed
- Pre-compiled the `TypeDetector` regex patterns instead of rebuilding them on every call.
- Guaranteed disposal of the ML Kit barcode client after a gallery scan, and limited cached export files to a one-hour lifetime.
- Added vertical scrolling to the Generator and Settings screens so the soft keyboard and small displays no longer cut content off.
- Passed the SMS body as `EXTRA_TEXT` as well as `sms_body`, for apps that only read the former.
- Fixed the onboarding page indicator reading the animation state without its delegate.

### Known issues
- Escaping `%` and `_` in the history search without an `ESCAPE` clause in the query made any search containing those characters return nothing. Fixed in 2.3.0.
- The extra `addOnFailureListener` added to the camera analyser closed each failed frame twice. Fixed in 2.3.0.

## [2.0.0] - 2026-06-15

### Changed
- **Complete native rewrite.** The Ionic/Angular/Capacitor app was replaced with a native Kotlin and Jetpack Compose implementation: CameraX and ML Kit for scanning, ZXing for generation, Room for history, Preferences DataStore for settings, Material 3 theming, and per-app language support.

## [1.2.0] - 2026-05-01

_Last release of the Ionic/Angular app._

### Added
- **New QR types**: SMS (`sms:` / `smsto:`), vCard (`BEGIN:VCARD`), Calendar (`BEGIN:VCALENDAR` / `BEGIN:VEVENT`) detection
- **Wi-Fi QR parsing**: network name, security type, password (with show/hide toggle), and hidden network indicator
- **Swipe-to-delete**: individual history items can be swiped to delete
- **Clear history confirmation**: alert dialog before clearing all scan history
- **Screen reader support**: `aria-live` region announces scan results, all icon-only buttons have `aria-label`
- **Accessibility**: added `.sr-only` utility, `role="alert"` on error states, `aria-labelledby` on history section
- **SEO meta tags**: `<meta name="description">`, Open Graph, and Twitter Card tags
- **iOS homescreen**: added `apple-mobile-web-app-capable` and related meta tags

### Changed
- **Architecture**: extracted business logic into `TypeDetectorService` and `HistoryService`
- **Models**: moved all interfaces, types, and constants to `models/scan.model.ts`
- **History limit**: increased from 20 to 50 items
- **History IDs**: now use `crypto.randomUUID()` instead of `Date.now()` + random
- **Type detection**: expanded regex to cover `ftp://`, `www.`, `MATMSG:` (email), SMS URIs
- **Web manifest**: added missing `name`, `short_name`, `start_url`, `display`, `orientation`, `categories` fields

## [1.1.0] - 2026-03-29

### Added
- Modern UI with custom purple colour theme and dark mode support
- Scanner viewfinder overlay with animated scan line
- Smart QR type detection: URL, Wi-Fi, Email, Phone, Location, Text
- Scan history — last 20 scans stored locally with type icons
- Front/back camera switching
- Flashlight (torch) toggle
- Haptic feedback on successful scan
- One-tap copy to clipboard with toast notification
- Camera permission retry flow with "Try Again" button
- `prefers-reduced-motion` accessibility support

### Changed
- Upgraded to Angular 20, Ionic 8, Capacitor 7
- Switched to `ChangeDetectionStrategy.OnPush` for better performance
- Added scan debounce (1.5s) to prevent rapid duplicate scans

## [1.0.0] - 2025-07-06

### Added
- Initial release
- Basic QR code scanning with camera
- Copy scanned result to clipboard
