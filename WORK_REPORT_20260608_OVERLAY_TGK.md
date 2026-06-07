# RedTriggerFix Overlay/TGK Work Report - 2026-06-08

## Scope

This report summarizes the current Codex work on the RedTriggerFix Android app around per-app TGK profiles, overlay position editing, runtime L/R markers, and TGK guard shutdown behavior.

## Main Changes

- Added `SYSTEM_ALERT_WINDOW` permission and registered `OverlayPickService`.
- Added `AppProfile.showOverlayMarkers` persisted as `showMarkers` in profile JSON.
- Changed new profile defaults:
  - orientation: portrait
  - mode: single tap
  - left and right shoulder keys: both enabled
  - rapid fire value still retained for when rapid mode is selected
- Reworked the profile editor UI:
  - "portrait" is shown before "landscape"
  - changing orientation resets L/R coordinates to that orientation's default points
  - rapid fire display range is 1-30/s
  - preset chips are low / mid / high, with numeric value shown separately
  - L/R colors use light pink and light blue in app UI
- Added overlay-based coordinate picking:
  - "在目标应用上悬浮取点" opens the target app and then shows draggable L/R markers
  - picker markers are always visible while editing, regardless of the runtime marker setting
  - coordinates are saved from `MotionEvent.rawX/rawY` to better match physical screen coordinates
  - overlay params use full-screen layout flags and display-cutout mode
- Added runtime overlay markers:
  - per profile switch: "在目标应用中显示 L/R 标记"
  - runtime markers are smaller and non-touchable, so they should not block target app input
  - runtime markers are hidden when the active profile is released or the service shuts down
- Changed `TriggerService` from periodic polling to an event-driven guard:
  - listens to `Settings.Global.CONTENT_URI`
  - evaluates `red_magic_forground_pkg` changes
  - applies the enabled profile for the current foreground package
  - releases TGK when leaving configured apps
  - refreshes active profile after profile changes
- Strengthened master-off shutdown:
  - `ProfileStore.disableTriggers()` now sends `TriggerService.ACTION_SHUTDOWN`
  - service hides overlay picker/runtime markers
  - calls `NativeTgkController.disable()`, which clears TGK switches/points and calls `releaseTgk`
  - unbinds/stops the Shizuku UserService
  - has a fallback stop after 2.5 seconds

## Files Changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/redtrigger/AppProfile.kt`
- `app/src/main/kotlin/com/redtrigger/ProfileStore.kt`
- `app/src/main/kotlin/com/redtrigger/TriggerService.kt`
- `app/src/main/kotlin/com/redtrigger/OverlayPickService.kt`
- `app/src/main/kotlin/com/redtrigger/ui/MainScreen.kt`

## Verification Done

- Built successfully with:

```powershell
.\gradlew.bat assembleDebug
```

- Installed successfully to:

```text
192.168.1.105:38977
```

- Confirmed overlay app-op was allowed:

```powershell
adb -s 192.168.1.105:38977 shell appops set com.redtriggerfix SYSTEM_ALERT_WINDOW allow
```

## Known Logic Risks

- Foreground detection still depends on RedMagic/system foreground reporting. If `red_magic_forground_pkg` does not update promptly, runtime marker display and TGK profile switching may lag or miss transitions.
- Overlay coordinates and vendor TGK coordinates are closer now, but still need real-device validation across portrait, landscape, status-bar-visible, and fullscreen target apps.
- The event-driven guard releases TGK when leaving configured apps. This is cleaner than the old sticky behavior, but may need tuning if RedMagic's vendor state machine expects a different lifecycle.
- Shutdown is now stronger, but should be tested with:
  - guard on -> configured app -> guard off
  - guard off while Shizuku UserService is disconnected
  - guard off while overlay picker is visible
  - guard off after switching out of a configured app

## Suggested Next Tests

- Add a temporary diagnostics card showing:
  - current foreground package
  - active profile package
  - saved L/R coordinates
  - runtime marker visibility intent
  - last TGK enable/disable result
- In a portrait target app:
  - pick L/R with overlay
  - save
  - verify the displayed runtime marker matches actual shoulder-trigger tap position
- Repeat the same in a true fullscreen landscape game.
- Verify master switch off leaves `isGlobalKeyEnable`, `isLeftGameKeyEnable`, and `isRightGameKeyEnable` false or released.
