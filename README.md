# NotificationBadgePrototype

Lightweight Android 9+ notification badges designed for iOS-style launchers and Launcher 3.

## What this version does

- Counts active notifications with `NotificationListenerService`.
- Uses **AccessibilityService** to read the launcher's visible icon bounds.
- Matches selected apps by their launcher label.
- Places a small red numeric badge at the **top-right of each matching icon** instead of using one fixed screen coordinate.
- Re-scans only on launcher accessibility events; there is **no polling loop**.
- Uses one accessibility service/process and only creates overlay TextViews for apps that currently have a badge.
- Updates automatically when notification counts, selected apps, or launcher layout change.
- Targets Android 9 (API 28) and avoids heavy libraries.

## Setup on the phone

1. Install the debug APK.
2. Open the app.
3. Enable **Notification access**.
4. Enable **Display over other apps**.
5. Enable **Accessibility access** for Notification Badge.
6. Choose the apps that should show badges.
7. Return to the launcher. Badges should attach to the matching icons automatically.

## Apple System / clone launcher

The service first detects the current HOME launcher package. It also accepts launcher-like package names as a fallback, which helps with some Android 9 clone launchers. The icon is identified from Accessibility text/content-description and its screen bounds.

If a particular clone launcher does not expose app labels through Accessibility, automatic attachment cannot be guaranteed because Android does not provide a standard public API for another app to read launcher icon coordinates.

## RAM / performance

- No bitmap processing.
- No periodic timer.
- Accessibility scans are debounced and capped.
- Overlay views exist only for apps with an active count.
- Old global X/Y calibration is no longer used for automatic placement.

## Build

GitHub Actions uses JDK 17, Android Gradle Plugin 8.5.2 and Gradle 8.7.

Workflow artifact: `NotificationBadgePrototype-debug/app-debug.apk`.
