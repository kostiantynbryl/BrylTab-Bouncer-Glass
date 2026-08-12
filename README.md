# BrylTab Bouncer Glass

[![Android 16](https://img.shields.io/badge/Android-16-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/16)
[![LSPosed](https://img.shields.io/badge/LSPosed%20%2F%20Vector-module-6f42c1)](https://github.com/LSPosed/LSPosed)
[![Build APK](https://github.com/kostiantynbryl/BrylTab-Bouncer-Glass/actions/workflows/build.yml/badge.svg)](https://github.com/kostiantynbryl/BrylTab-Bouncer-Glass/actions/workflows/build.yml)

A narrowly scoped LSPosed/Vector module for the **DOOGEE U10 running Android 16**. It keeps the stock Android credential screen intact while making the PIN bouncer background almost transparent and preserving lockscreen content behind it.

## What it changes

- Sets the PIN/security bouncer scrim to **10% opacity / 90% transparency**.
- Keeps lockscreen content visible during the `LOCKSCREEN -> PRIMARY_BOUNCER` transition.
- Hooks **System UI only** (`com.android.systemui`).
- Fails safely when a targeted class or field is not present.

## What it does not change

The module does **not** modify PIN verification, GateKeeper, Keystore, biometric authentication, credential storage, lockout policy, or any other security decision. Authentication remains fully stock.

## Target / compatibility

This first release is intentionally device-specific. It was prepared against the supplied DOOGEE U10 Android 16 `SystemUI.apk`:

```text
SHA-256: a10283c5f977490641cc19c2f2601564b79608f2a803073d2f624a33b3b760e3
```

Verified target classes in that build:

```text
com.android.systemui.statusbar.phone.ScrimState$3
  -> BOUNCER

com.android.systemui.statusbar.phone.ScrimState$4
  -> BOUNCER_SCRIMMED

com.android.systemui.keyguard.ui.viewmodel.
LockscreenToPrimaryBouncerTransitionViewModel$$ExternalSyntheticLambda0
  -> lockscreen alpha transition
```

Other Android 16 ROMs may use different SystemUI implementations or R8 output. Do not assume compatibility without testing.

## Requirements

- Android 16
- Root with Magisk-compatible Zygisk environment
- LSPosed or Vector with legacy Xposed API compatibility
- DOOGEE U10 SystemUI matching the target build above for the initial test release

## Install

1. Install the generated APK normally.
2. Open LSPosed/Vector.
3. Enable **BrylTab Bouncer Glass**.
4. Scope it only to **System UI (`com.android.systemui`)**.
5. Reboot the device.
6. Open the lockscreen and swipe to the PIN screen.

Expected behavior: the normal PIN keypad remains unchanged, while the lockscreen stays visible behind a light 10% dark scrim.

## Recovery

If SystemUI becomes unstable:

1. Disable **BrylTab Bouncer Glass** in LSPosed/Vector.
2. Reboot.

The module never replaces `SystemUI.apk`, so disabling the hook restores stock behavior.

For development devices, a bootloop protection module is strongly recommended before experimenting with SystemUI hooks.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36
- Gradle 8.13

Build locally:

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds the APK automatically on pushes to `main`, pull requests, and manual workflow runs. The workflow also publishes a SHA-256 checksum alongside the APK artifact.

## Project structure

```text
app/src/main/java/com/bryltab/bouncerglass/HookEntry.java  Xposed hooks
app/src/main/assets/xposed_init                             legacy module entry point
app/src/main/AndroidManifest.xml                            Xposed metadata/scope
.github/workflows/build.yml                                 CI APK build
```

## Status

**v0.1.0 — initial hardware test build.**

The first goal is to confirm two behaviors independently on the target tablet:

1. bouncer scrim is reduced to 10% opacity;
2. lockscreen clock/date content remains visible behind the PIN keypad.

See [`CHANGELOG.md`](CHANGELOG.md) for version history.
