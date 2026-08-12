# Changelog

All notable changes to BrylTab Bouncer Glass are documented here.

## 0.1.1 — 2026-08-12

### Fixed

- Enforce bouncer opacity after `ScrimController.applyState$1()` recalculates SystemUI alpha values.
- Cap the actual rendered bouncer scrim at 10% opacity in `setScrimAlpha()` and `updateScrimColor()`.
- Keep front and notification scrims from becoming opaque while the primary bouncer is active.
- Add a legacy `BouncerViewBinder` fallback that clears only the bouncer root background.
- Add diagnostic Xposed log messages for the state, controller and final render hooks.

## 0.1.0 — 2026-08-12

### Added

- Android 16 targeted LSPosed/Vector module.
- 10% bouncer scrim opacity (90% transparent background).
- Lockscreen alpha preservation during the primary bouncer transition.
- Strict `com.android.systemui` package guard.
- Fail-safe exception handling and one-time Xposed log messages.
- GitHub Actions APK build with SHA-256 artifact.
