# Split keyboard layout and input fixes

Base: `YunSeoJeong/moonlight-android`, `moonlight-noir`, commit `759365e7b544b74f0e0edc705943528458eed105`.

## Behavior

- Keep the top 16:9 stream area and the existing Korean/English/shifted-symbol/number legends and Fn behavior.
- Rebalance the first row: 1–6 / Fn F1–F6 on the left, 7–0, minus, equals / Fn F7–F12 on the right.
- Use one common letter width, a smaller common punctuation width, two independently touchable Space keys, and a clear center gap. Remove the separate left Alt and navigation cluster; put Delete at the bottom right. Existing right Alt / Fn Korean toggle remains.
- Nav is hold-only. While held, I/J/K/L display and send up/left/down/right. Releasing Nav restores the legends immediately. A key already held keeps its original transmitted identity until released, preventing a stuck arrow or letter. Multiple Nav pointers and cancellation are supported.
- Include right-side modifiers in the common modifier recognizer. Send software Ctrl as a non-normalized VK intent, retaining left/right identity, to avoid normalized physical scan-code interpretation on Korean Windows layouts. Physical keyboard translation is unchanged. Hosts configured to force scan codes still need device validation.
- Carry floating-point trackpad deltas into the session accumulator, preserving subpixel movement at low sensitivity. Compatibility cursor movement also receives the original fractional delta. Switching pad modes resets the active pointer to avoid stale movement.
- Start the split keyboard in sensor landscape, including when host-resolution rotation is enabled at startup. Preserve subsequent explicit host rotations. Without the split keyboard, Auto Orientation follows system auto-rotation preferences during streaming.

## Verification performed

- `git diff --check` passed.
- Compiled the changed core layout/state/mouse classes with Java 17 and minimal Android boundary stubs; 24 smoke checks passed. Covered layout count and F-key split, Delete placement, all four Nav mappings, release order, normal-letter restoration, multiple Nav fingers, cancellation, right-Ctrl chord, dual-Space reference counts, positive/negative fractional movement, and compatibility cursor precision.
- This smoke check does **not** validate Android UI rendering, orientation callbacks, JNI transport, Windows input behavior, or an APK build.
- Added regression tests for layout geometry, Nav touch/labels/state, modifiers, fractional mouse/scroll, and Game orientation. Added `.github/workflows/split-keyboard.yml` to run the relevant Robolectric tests and build debug APKs after publication.
- Full Gradle/Robolectric/APK verification was not run: the current environment has no Android SDK and the Gradle wrapper could not download its distribution. The user approved publication and PR #5 is open. Initial CI stopped while setup-android requested the retired SDK tools package; the workflow now explicitly installs platform-tools. Full CI results are pending.

## Device acceptance checks

| Action | Expected result |
| --- | --- |
| Enable split keyboard and start a stream with the unfolded Fold held portrait | App opens landscape; remote image remains above the keyboard at 16:9 |
| Rotate to the other landscape side | Keyboard and stream follow the landscape orientation; held keys are released |
| Launch with host-resolution rotation enabled, then explicitly rotate | Initial keyboard is landscape; explicit rotation is respected |
| Type I/J/K/L without Nav | Ordinary letters; existing Korean legends shown |
| Hold Nav and use I/J/K/L | Only these four keys show arrows and move the cursor accordingly |
| Hold Nav + I, release Nav first, then I | Arrow key is released correctly; next I types a letter |
| Tap Nav then type J | J types normally; Nav never latches |
| Use Fn with the top row | F1–F12 work in left/right groups of six; normal number/symbol entry still works |
| Use right Ctrl + C/V/A, then release | Copy/paste/select all work, and Ctrl does not remain stuck |
| Hold both Ctrl keys, release one, type A | Ctrl stays active until the remaining key is released |
| Hold both Space keys, release one | Space remains held until the other is released |
| Hold RT and move very slowly at 10–50% sensitivity | Small motions accumulate instead of disappearing; test both directions |
| Repeat with touch compatibility enabled and with RB scrolling | Cursor/scroll responds to small movements; switching modes does not jump |
| Background/disconnect/rotate while Nav or Ctrl is held | No stuck keys after return/reconnect |

Run with an Android SDK/NDK installation:

```sh
./gradlew :app:testNonRoot_gameDebugUnitTest \
  --tests 'com.limelight.binding.input.virtual_controller.splitkeyboard.*' \
  --tests 'com.limelight.binding.input.KeyboardTranslatorTest' \
  --tests 'com.limelight.GameOrientationTest'
./gradlew :app:assembleNonRoot_gameDebug
```
