# NotificationBadgePrototype

Lightweight Android 9+ notification badges designed for iOS-style launchers and Launcher 3.

## What this version does

- Counts active notifications with NotificationListenerService.
- Uses AccessibilityService to read the launcher's visible icon bounds.
- Matches selected apps by their launcher label.
- Places a small red numeric badge at the top-right of each matching icon.
- Re-scans only on launcher accessibility events.
- No polling loop.
- Overlay views exist only for apps with an active count.
- Updates automatically when notification counts, selected apps, or launcher layout change.
- Targets Android 9 (API 28).
- Avoids heavy libraries.

## Setup

1. Install the debug APK.
2. Open the app.
3. Enable Notification access.
4. Enable Display over other apps.
5. Enable Accessibility access.
6. Choose the apps that should show badges.
7. Return to the launcher.

## Clone launcher

The service detects the current HOME launcher package and also accepts launcher-like package names as a fallback.

The icon is identified using Accessibility text/content-description and screen bounds.

If a clone launcher does not expose app labels through Accessibility, automatic attachment cannot be guaranteed.

## Performance

- No bitmap processing.
- No periodic timer.
- Accessibility scans are debounced.
- Overlay views exist only for active badges.

## Build

GitHub Actions uses:

- JDK 17
- Android Gradle Plugin 8.5.2
- Gradle 8.7
- Android SDK 35

The generated artifact is:

NotificationBadgePrototype-debug/app-debug.apk
