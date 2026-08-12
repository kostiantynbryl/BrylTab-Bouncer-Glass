# Changelog

All notable changes to BrylTab Bouncer Glass are documented here.

## 0.1.3 — 2026-08-12

### Fixed

- Preserve the final `KeyguardRootView` alpha while the primary bouncer is active, at the binder collector that actually calls `View.setAlpha()`.
- Track bouncer lifetime through `KeyguardSecurityContainerController.onResume()` / `onPause()` so normal unlock transitions are restored afterwards.
- Temporarily render `WallpaperManager.FLAG_LOCK` on the keyguard root while PIN is shown, instead of exposing the home wallpaper through the transparent bouncer.
- Restore the original keyguard-root background when the bouncer pauses.
- Add targeted diagnostics for root capture, final alpha enforcement, bouncer lifecycle, and lock-wallpaper loading.

## 0.1.2 — 2026-08-12

### Fixed

- Force `KeyguardSecurityContainer.mTransparentModeEnabled = true` on the target DOOGEE U10 Android 16 SystemUI.
- Re-clear the container background whenever `reloadBackgroundColor()` runs.
- Clear the first inflated container in `onFinishInflate()` so the opaque fallback cannot reappear before a configuration refresh.
- Keep the v0.1.1 ScrimController and lockscreen-alpha hooks as secondary protection.

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
