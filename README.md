# RedTriggerFix

Independent Android prototype for restoring RedMagic native shoulder trigger mapping on RMOS11 devices.

RedTriggerFix uses the RedMagic TGK/native input path discovered during the 2026-06-05 investigation. It does not use KeyMapper, accessibility injection, `input tap`, overlays, game memory modification, or `/dev/uinput`. The app binds a Shizuku UserService running as shell and calls RedMagic vendor extensions on `android.hardware.input.IInputManager`.

Package and version line:

- Application ID: `com.redtriggerfix`
- Version line: `0.1.x`, starting from `0.1.0`
- APK name: `RedTriggerFix-v<version>.apk`

## Current Scope

- Select a foreground target app, default `com.tencent.tmgp.sgame`.
- Store left/right touch coordinates, mode, rapid-fire count, and polling interval.
- Run a foreground watchdog service.
- Enable native TGK mapping when the target app is foreground.
- Keep native TGK state unchanged when the target app leaves foreground, avoiding shutdown-time cleanup races.
- Show current foreground package and TGK status.

## Requirements

- RedMagic / Nubia device with RedMagic TGK vendor methods.
- Shizuku installed and running.
- Shizuku permission granted to this app.

## Build

```powershell
.\gradlew.bat assembleDebug
```

If building locally, create `local.properties` with your Android SDK path. Do not commit it.

## Notes

The standalone ADB proof-of-concept lives in `..\lite`. Research notes, logs, and reverse-engineering artifacts live in `..\research`.

This project intentionally targets a narrow device and OS behavior. It is not a general Android keymapper.
