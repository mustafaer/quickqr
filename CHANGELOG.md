# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

> Versions 1.x were the original Ionic/Angular/Capacitor app. Version 2.0.0 is a
> complete rewrite as a native Kotlin and Jetpack Compose application; entries
> below that line describe a different codebase.

## [Unreleased]

### Added
- **Link inspection, on the device.** Every scanned link now leads with its real destination — the registrable domain in bold, any subdomain dimmed — and calls out look-alike domains (`paypa1.com`, Cyrillic `аpple.com`, `pay-pal.com`), a brand's domain placed in front of someone else's (`paypal.com.secure-login.xyz`), `user@host` tricks, bare public IP addresses, unencrypted `http`, unusual ports and short links. Links with a severe finding make Copy the default and Open a deliberate "Open anyway". The check uses a bundled brand list weighted toward Türkiye, Germany, India and the Arabic-speaking countries; nothing is uploaded.
- **UPI payment codes** (`upi://pay`, `upi://mandate`) are recognised. The sheet shows the payee's UPI ID, the name the code claims, any pre-filled amount, and always the reminder that scanning a QR can only send money — the most common UPI fraud depends on people not knowing that. Codes with no valid payee get no Pay button.
- **WPA3** networks: `T:SAE` codes, which Android's own "Share Wi-Fi" writes, connect with a WPA3 passphrase, and the generator can create them.
- **Pinned scans** are kept regardless of the 100-scan history limit (database schema 2, migrated automatically).
- **Undo** after deleting a scan.
- **Viewfinder lock-on**: the frame springs onto the code that was read before the result opens, and on each continuous-mode save.
- ML Kit's **automatic zoom** for codes that are detected but too small to read, plus pinch-to-zoom and tap-to-focus.

### Fixed
- **ML Kit could send usage data to Google**, contradicting the README's "no `INTERNET` permission". The barcode library merges that permission into the app to upload metrics such as device model, app version and performance figures to `firebaselogging.googleapis.com`. The permission is now removed in the manifest. The upload attempts fail on the device and are caught by ML Kit itself; scanning, which never needed the network, is unaffected.
- **Scan history went to Google's cloud backup**, contradicting "History and settings never leave the device" — including every scanned Wi-Fi password. History is now excluded from cloud backup and only moves in a user-started device-to-device transfer.
- **Wi-Fi passwords were shown in plain text** in the history list, in continuous-mode snackbars and in Android 13+'s clipboard preview. History rows and snackbars now show the network name, copies are marked sensitive, the mask no longer reveals the password's length, and a revealed password blocks screenshots.
- **WPA3 networks failed to connect**: `T:SAE` was read as WPA2.
- **E-mail QR codes showed `Hi+there`** in other apps' subject lines. Query values are now percent-encoded per RFC 6068.
- **The scan region was computed on swapped axes** on portrait phones: ML Kit's upright boxes were compared against the sensor's landscape width and height, rejecting codes low in the frame. Boxes now arrive in preview coordinates and are tested against the drawn viewfinder.
- **Continuous mode re-fired every two seconds** while a code stayed in view — a new vibration, snackbar and database write each time. A code now fires once when it enters the view and again only after it has left.
- **The scan-line animation never stopped**, keeping the display at its maximum refresh rate on every tab and while a result was open.
- **A large gallery photo could crash the app**: it was decoded at full resolution, and the resulting `OutOfMemoryError` was not caught. Pictures are now decoded at no more than 2048 px.
- **Generated QR codes were blurry** on screen (a 512 px bitmap scaled with filtering) and low-resolution when saved. The preview is drawn as vectors and saved images are rendered at whole pixels per module, around 1500 px.
- **QR content was capped at 1,273 bytes** and every code was denser than needed, because error correction was always the maximum level.
- **Debug builds could not share files**, and could not be installed next to the release: the FileProvider authority was hard-coded to the release application ID.
- **A floating calendar time with a `TZID`** was read as the phone's local time.
- **Explicit Light or Dark themes recreated the activity on every cold start**; the theme is now applied before the first activity exists.
- Text fields in the generator no longer route through an asynchronous stream, which could drop characters during fast typing.

### Changed
- `AppViewModel` is split into one ViewModel per tab with a small manual dependency container, and the scan session is a single state value, so the result sheet can no longer show a new payload with the previous payload's type. The scanner ViewModel is covered by tests with fakes, and the new link inspection, look-alike detection, UPI parsing, encoder and classifier by plain JVM tests — 94 unit tests in all.
- The camera runs on `LifecycleCameraController` with ML Kit's CameraX analyzer at 720p, restricted to the barcode formats QuickQR can act on.
- The three-screen onboarding is replaced by a dismissible privacy note on the scanner, so the camera is the first thing a new user sees.
- The gallery button uses the system photo picker; no storage permission is involved.
- History rows show what was scanned — the network name, contact, event or site — with a relative time, instead of the raw payload; "BEGIN:VCARD" is gone.
- Type badges and icons use Material tonal colour pairs that follow dynamic colour and pass WCAG AA in both themes; the old white-on-colour badges measured as low as 2.1:1.
- The generator previews live as you type; the Generate button is gone.
- The R8 configuration no longer keeps all of Compose, CameraX and ZXing, so release builds shrink and optimise them. ML Kit keeps its rules: it loads components by reflection, and without them release builds crash on the first scan.
- The unused `ui-text-google-fonts` dependency is removed.

### Removed
- The GitHub Actions workflow. Unit tests, lint and the unsigned release build are run locally before each release.

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
