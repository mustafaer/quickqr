# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-05-01

### Added
- **New QR types**: SMS (`sms:` / `smsto:`), vCard (`BEGIN:VCARD`), Calendar (`BEGIN:VCALENDAR` / `BEGIN:VEVENT`) detection
- **Wi-Fi QR parsing**: Parsed network name, security type, password (with show/hide toggle), and hidden network indicator
- **vCard summary**: Shows contact name extracted from FN/N fields
- **Calendar event summary**: Shows event title from SUMMARY field
- **Swipe-to-delete**: Individual history items can be swiped to delete
- **Clear history confirmation**: Alert dialog before clearing all scan history
- **Screen reader support**: `aria-live` region announces scan results, all icon-only buttons have `aria-label`
- **Accessibility**: Added `.sr-only` utility, `role="alert"` on error states, `aria-labelledby` on history section
- **SEO meta tags**: `<meta name="description">`, Open Graph, and Twitter Card tags
- **iOS homescreen**: Added `apple-mobile-web-app-capable` and related meta tags
- **Scan Again button**: Prominent in-card button to resume scanning

### Changed
- **Architecture**: Extracted business logic into `TypeDetectorService` and `HistoryService`
- **Models**: Moved all interfaces, types, and constants to `models/scan.model.ts`
- **Scanner behavior**: Scanner no longer auto-resumes — user must explicitly tap "Scan Again"
- **History limit**: Increased from 20 to 50 items
- **History IDs**: Now use `crypto.randomUUID()` instead of `Date.now()` + random
- **Error handling**: Replaced silent `catch {}` blocks with `console.warn()` logging
- **Inline styles**: Moved all 10+ inline styles to proper SCSS classes
- **Empty state**: Enhanced with animated icon wrapper and better visual hierarchy
- **Type detection**: Expanded regex to cover `ftp://`, `www.`, `MATMSG:` (email), SMS URIs
- **Web manifest**: Added missing `name`, `short_name`, `start_url`, `display`, `orientation`, `categories` fields
- **Web manifest**: Fixed MIME type from `image/png` to `image/webp` to match actual file extensions
- **Theme**: Added `--ion-color-medium` definition for proper chip coloring

### Fixed
- **Race condition**: Removed duplicate `scannerEnabled = true` in `retryPermission()` that could trigger parallel camera access
- **Magic numbers**: Extracted all inline constants (`300ms` delay, `50` char truncation) to named constants

## [1.1.0] - 2026-03-29

### Added
- Modern UI with custom purple color theme and dark mode support
- Scanner viewfinder overlay with animated scan line
- Smart QR type detection: URL, Wi-Fi, Email, Phone, Location, Text
- Scan history — last 20 scans stored locally with type icons
- Front/back camera switching
- Flashlight (torch) toggle
- Haptic feedback on successful scan
- One-tap copy to clipboard with toast notification
- "Open" button for URLs, emails, phone numbers, and locations
- Scan pause with success checkmark overlay after each scan
- Animated result card with slide-up entrance
- Empty state guidance when no scans yet
- Camera permission retry flow with "Try Again" button
- `prefers-reduced-motion` accessibility support
- `@capacitor/browser` for in-app URL opening

### Changed
- Upgraded to Angular 20, Ionic 8, Capacitor 7
- Switched to `ChangeDetectionStrategy.OnPush` for better performance
- Added scan debounce (1.5s) to prevent rapid duplicate scans
- Scanner auto-pauses after successful scan to save battery
- Registered all Ionicons via `addIcons()` for standalone mode
- Production build with minification and resource shrinking enabled
- Updated splash screen to match new theme color

## [1.0.0] - 2025-07-06

### Added
- Initial release
- Basic QR code scanning with camera
- Copy scanned result to clipboard

