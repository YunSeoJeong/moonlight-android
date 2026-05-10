# Dual Display Development Guide

This guide documents the foldable dual-display PoC that shows a small control
surface on the cover display while keeping the Moonlight streaming session on
the main display.

The verified path is Jetpack WindowManager's WindowArea presentation API. The
older Android multi-display path using `DisplayManager` can still work for
external displays, simulated displays, DeX, or HDMI, but Galaxy Fold cover
screens may not appear as launchable `Display` instances.

## Goal

Keep the remote streaming session active on the main display and present a
minimal control UI, initially a Back button, on the foldable cover display.

The Back button should call the existing in-stream back behavior, so it opens
the current back menu or performs the same action as `Game.onBackPressed()`.

## Terms

- **Main display**: the screen that hosts `Game` and renders the remote session.
- **Cover display**: the secondary foldable screen used as an auxiliary control
  surface.
- **Launchable display**: a `Display` returned by `DisplayManager` that accepts
  `ActivityOptions.setLaunchDisplayId()`.
- **WindowArea**: Jetpack WindowManager abstraction for special display areas on
  foldables, including dual-screen presentation areas.
- **Dual-screen presentation**: WindowArea operation that lets the app present
  content on a second display area while the main Activity stays on the primary
  display.

## Current PoC Files

- `app/src/main/java/com/limelight/utils/FoldCoverBackButtonActivity.java`
  tries the `DisplayManager` path first.
- `app/src/main/java/com/limelight/utils/FoldCoverBackButtonWindowAreaPoc.java`
  tries the WindowArea dual-screen path when no launchable secondary display is
  exposed.
- `app/src/main/java/com/limelight/Game.java` starts and closes the PoC with the
  stream lifecycle.
- `app/src/main/res/xml/preferences.xml` exposes the `Fold cover back button PoC`
  setting.
- `app/build.gradle` adds `androidx.window:window` and
  `androidx.window:window-java`.

## Recommended Architecture

Use a layered display strategy:

1. Try `DisplayManager` for normal secondary displays.
2. Fall back to WindowArea dual-screen presentation for foldables.
3. Report unsupported states in-app without crashing.

This keeps external monitor support and foldable cover-display support separate
but lets the feature share one user-facing toggle.

## DisplayManager Path

Use this path when Android exposes the secondary target as a real `Display`.

Basic flow:

1. Read `DisplayManager.getDisplays()`.
2. Find a display whose ID differs from `Game.getDisplay().getDisplayId()`.
3. Create an Activity intent for the control surface.
4. Launch with:

```java
ActivityOptions options = ActivityOptions.makeBasic();
options.setLaunchDisplayId(targetDisplay.getDisplayId());
game.startActivity(intent, options.toBundle());
```

Observed limitation:

On Galaxy Fold cover-display use cases, Android may expose only the currently
active app display through `DisplayManager`. In that state, this path fails with
no launchable target. This does not mean the hardware cannot do dual display; it
only means the public multi-display Activity API cannot see it.

## WindowArea Path

Use this path when `DisplayManager` has no secondary display but the device may
support foldable dual-screen presentation.

Required dependencies:

```gradle
implementation 'androidx.window:window:1.6.0-alpha03'
implementation 'androidx.window:window-java:1.6.0-alpha03'
```

The current alpha declares minSdk 23. This app still supports minSdk 21, so the
PoC uses:

```xml
<uses-sdk tools:overrideLibrary="androidx.window,androidx.window.core,androidx.window.java" />
```

The feature must be runtime-gated. The current PoC only attempts WindowArea on
Android 14 or newer:

```java
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    return false;
}
```

Basic flow:

1. Create `WindowAreaControllerCallbackAdapter`.
2. Register a window area listener.
3. For each `WindowArea`, query:

```java
windowArea.getCapability(
        WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA);
```

4. Continue only when status is `WINDOW_AREA_STATUS_AVAILABLE` or
   `WINDOW_AREA_STATUS_ACTIVE`.
5. Start presentation with:

```java
windowAreaController.presentContentOnWindowArea(
        area.getToken(),
        game,
        executor,
        callback);
```

6. In `onSessionStarted()`, attach the control view:

```java
session.setContentView(createBackButtonView(session.getContext()));
```

7. Store the `WindowAreaSessionPresenter` so it can be closed when streaming
   ends.

## Input Behavior

The cover button should call existing game behavior instead of duplicating input
logic:

```java
if (Game.instance != null) {
    Game.instance.onBackPressed();
    requestFocusToGameActivity(false);
}
```

This keeps behavior aligned with existing settings such as the Back Menu. It
also avoids creating separate keyboard/controller packet paths for the PoC.

## Lifecycle Rules

Start the dual-display control surface only after the streaming Activity has
initialized enough state for `Game.instance` and preferences to be valid.

Close the surface from `Game.onDestroy()`:

```java
FoldCoverBackButtonActivity.closeFoldCoverBackButton();
```

For WindowArea sessions:

- Keep a reference to `WindowAreaSessionPresenter`.
- Call `close()` when the stream ends.
- Remove the window-area listener.
- Treat `onSessionEnded()` as normal; the device can end the presentation when
  folded, rotated, backgrounded, or when system state changes.

## Error Handling

WindowArea callbacks can vary by device and OEM implementation. The PoC wraps
these steps defensively:

- listener callback body
- capability lookup
- `presentContentOnWindowArea()`
- `onSessionStarted()` content attachment

Failures should show an in-app message and close the attempted session. They
should not terminate the stream.

Useful user-facing states:

- `No launchable secondary display was exposed by Android. Trying WindowArea dual-screen API.`
- `Checking WindowArea dual-screen support.`
- `WindowArea dual-screen presentation is not available on this device state.`
- `WindowArea Back button presentation started.`
- `Could not check WindowArea dual-screen support.`

## In-App Diagnostics

ADB should not be required for normal testing. Add a diagnostic screen or dialog
before productionizing this feature.

Recommended fields:

- Android SDK version.
- Device manufacturer/model.
- Current `Game` display ID.
- `DisplayManager.getDisplays()` count and display IDs.
- WindowArea count.
- Each WindowArea `OPERATION_PRESENT_ON_AREA` status.
- Last presentation session state.
- Last exception class and message.

These diagnostics let testers report whether the failure is:

- no secondary `Display`,
- WindowArea unsupported,
- WindowArea available but presentation failed,
- presentation started but content or focus failed.

## User Flow

Suggested production setting:

`Fold cover controls`

Setting summary:

`Show selected controls on the foldable cover display while streaming, when supported by the device.`

Expected runtime behavior:

1. User enables the setting.
2. User starts a stream with the device unfolded.
3. App starts streaming on the main display.
4. App probes dual-display support.
5. If available, cover display shows the selected control UI.
6. If unavailable, app shows one concise message and keeps streaming normally.

## Production Hardening Checklist

- Replace the PoC setting name with a user-facing feature name.
- Add an in-app diagnostic dialog.
- Avoid repeated toast spam by caching unsupported state per stream.
- Add a small controller model so the cover UI can host more than Back later.
- Support orientation and display-size changes in the cover presentation.
- Decide whether the cover UI should stay black, dim, or use a low-brightness
  theme.
- Ensure all WindowArea operations are guarded by SDK and capability checks.
- Keep the stream alive even if dual-display setup fails.
- Close WindowArea sessions on stream stop, disconnect, Activity destruction,
  and display/session end.
- Consider adding a watchdog that closes the cover session if `Game.instance`
  disappears.

## Testing Matrix

Test at least these cases:

| Case | Expected result |
| --- | --- |
| Galaxy Fold, unfolded, setting on | WindowArea presentation starts if device state supports it |
| Galaxy Fold, setting off | No dual-display probe or cover UI |
| Fold/rotate while streaming | No crash; session either continues or ends cleanly |
| Stream disconnect | Cover UI closes |
| Back button tap | Existing `Game.onBackPressed()` behavior |
| Back button long press | PoC cover session closes |
| Non-foldable phone | No crash; unsupported message only |
| Android below 14 | WindowArea path skipped with version message |
| External display/DeX | `DisplayManager` path may launch the control Activity |

## Known Constraints

- Samsung Camera's cover-preview behavior proves the hardware can drive both
  displays, but it does not prove the same private/system route is available to
  third-party apps.
- The verified public route is WindowArea dual-screen presentation.
- WindowArea APIs are still evolving, so dependency version and callback
  behavior should be revisited before release.
- `DisplayManager` and WindowArea are separate capabilities. Success in one does
  not imply success in the other.

## References

- Android foldable display modes:
  https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/support-foldable-display-modes
- `WindowAreaController` reference:
  https://developer.android.com/reference/androidx/window/area/WindowAreaController
- `ActivityOptions.setLaunchDisplayId()` reference:
  https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)
