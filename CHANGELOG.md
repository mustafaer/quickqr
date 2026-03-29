# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

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

