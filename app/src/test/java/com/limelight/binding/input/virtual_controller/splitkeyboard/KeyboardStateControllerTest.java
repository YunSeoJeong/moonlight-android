package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class KeyboardStateControllerTest {
    private Context context;
    private FakeTransport transport;
    private KeyboardStateController controller;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        transport = new FakeTransport();
        controller = new KeyboardStateController(
                transport, new SplitKeyboardPreferences(context));
    }

    @Test
    public void regularKeySendsOrderedDownAndUp() {
        KeySpec a = find(LogicalKey.KEY_A);

        assertTrue(controller.pointerDown(1, a, 10));
        controller.pointerUp(1, 20);

        assertEquals("D:KEY_A:0", transport.events.get(0));
        assertEquals("U:KEY_A:0", transport.events.get(1));
        assertEquals(1, transport.prepareCount);
        assertEquals("P", transport.operations.get(0));
        assertEquals("D:KEY_A", transport.operations.get(1));
    }

    @Test
    public void subDisplayUiCanBeHiddenWithoutDisablingControls() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(SplitKeyboardPreferences.KEY_SUB_DISPLAY_MOUSE_CONTROLS, true)
                .putBoolean(SplitKeyboardPreferences.KEY_MOUSE_TOUCH_COMPATIBILITY, true)
                .putBoolean(SplitKeyboardPreferences.KEY_HIDE_SUB_DISPLAY_CONTROLS_UI, true)
                .commit();

        SplitKeyboardPreferences preferences = new SplitKeyboardPreferences(context);

        assertTrue(preferences.subDisplayMouseControls);
        assertTrue(preferences.mouseTouchCompatibility);
        assertTrue(preferences.hideSubDisplayControlsUi);
    }

    @Test
    public void heldModifierSupportsTwoPointerChord() {
        KeySpec ctrl = find(LogicalKey.LEFT_CTRL);
        KeySpec c = find(LogicalKey.KEY_C);

        controller.pointerDown(1, ctrl, 0);
        controller.pointerDown(2, c, 20);
        controller.pointerUp(2, 30);
        controller.pointerUp(1, 500);

        assertEquals("D:LEFT_CTRL:2", transport.events.get(0));
        assertEquals("D:KEY_C:2", transport.events.get(1));
        assertEquals("U:KEY_C:2", transport.events.get(2));
        assertEquals("U:LEFT_CTRL:0", transport.events.get(3));
    }

    @Test
    public void singleModifierTapAppliesToNextKeyOnly() {
        KeySpec ctrl = find(LogicalKey.LEFT_CTRL);
        KeySpec c = find(LogicalKey.KEY_C);

        controller.pointerDown(1, ctrl, 0);
        controller.pointerUp(1, 100);
        controller.pointerDown(2, c, 150);
        controller.pointerUp(2, 160);

        assertEquals(4, transport.events.size());
        assertEquals("D:LEFT_CTRL:2", transport.events.get(0));
        assertEquals("D:KEY_C:2", transport.events.get(1));
        assertEquals("U:KEY_C:2", transport.events.get(2));
        assertEquals("U:LEFT_CTRL:0", transport.events.get(3));
        assertTrue(!controller.isLocked(LogicalKey.LEFT_CTRL));
    }

    @Test
    public void nativeHangulModeSendsPhysicalToggleKeyPair() {
        controller.pointerDown(3, find(LogicalKey.FN), 0);
        controller.pointerUp(3, 40);
        KeySpec hangul = find(LogicalKey.RIGHT_ALT);

        controller.pointerDown(4, hangul, 60);
        controller.pointerUp(4, 70);

        assertEquals("D:HANGUL_TOGGLE:0", transport.events.get(0));
        assertEquals("U:HANGUL_TOGGLE:0", transport.events.get(1));
        assertTrue(!controller.isFnActive());
    }

    @Test
    public void cancelReleasesEveryRemoteDownKey() {
        controller.pointerDown(1, find(LogicalKey.LEFT_SHIFT), 0);
        controller.pointerDown(2, find(LogicalKey.KEY_A), 10);

        controller.releaseAllPressedKeys(ReleaseReason.ACTION_CANCEL);

        assertEquals("U:KEY_A:1", transport.events.get(2));
        assertEquals("U:LEFT_SHIFT:0", transport.events.get(3));
        assertTrue(controller.getPressedKeysForTesting().isEmpty());
    }

    @Test
    public void heldKeyStaysDownSoHostControlsKeyRepeat() {
        controller.pointerDown(1, find(LogicalKey.KEY_A), 0);

        assertEquals(1, transport.events.size());

        controller.pointerUp(1, 2_000);
        assertEquals("U:KEY_A:0", transport.events.get(1));
    }

    @Test
    public void quickDoubleTapLocksModifierUntilTappedAgain() {
        controller.pointerDown(1, find(LogicalKey.LEFT_CTRL), 0);
        controller.pointerUp(1, 100);
        controller.pointerDown(2, find(LogicalKey.LEFT_CTRL), 150);
        controller.pointerUp(2, 220);
        controller.pointerDown(3, find(LogicalKey.KEY_A), 250);
        controller.pointerUp(3, 260);

        assertTrue(controller.isLocked(LogicalKey.LEFT_CTRL));
        assertTrue(!controller.isOneShot(LogicalKey.LEFT_CTRL));
        assertEquals(3, transport.events.size());
        assertEquals("D:LEFT_CTRL:2", transport.events.get(0));
        assertEquals("D:KEY_A:2", transport.events.get(1));
        assertEquals("U:KEY_A:2", transport.events.get(2));

        controller.pointerDown(4, find(LogicalKey.LEFT_CTRL), 300);
        controller.pointerUp(4, 340);
        assertEquals("U:LEFT_CTRL:0", transport.events.get(3));
        assertTrue(!controller.isLocked(LogicalKey.LEFT_CTRL));
    }

    @Test
    public void hangulRightAltIsAlwaysMomentary() {
        KeySpec rightAlt = find(LogicalKey.RIGHT_ALT);

        controller.pointerDown(1, rightAlt, 0);
        controller.pointerUp(1, 100);
        controller.pointerDown(2, rightAlt, 150);
        controller.pointerUp(2, 220);

        assertEquals(4, transport.events.size());
        assertEquals("D:RIGHT_ALT:4", transport.events.get(0));
        assertEquals("U:RIGHT_ALT:0", transport.events.get(1));
        assertEquals("D:RIGHT_ALT:4", transport.events.get(2));
        assertEquals("U:RIGHT_ALT:0", transport.events.get(3));
        assertTrue(!controller.isLocked(LogicalKey.RIGHT_ALT));
        assertTrue(!controller.isOneShot(LogicalKey.RIGHT_ALT));
    }

    @Test
    public void activatingRightTrackpadOnlyReleasesRightSideKeys() {
        KeySpec leftCtrl = find(LogicalKey.LEFT_CTRL);
        KeySpec rightAlt = find(LogicalKey.RIGHT_ALT);
        controller.pointerDown(1, leftCtrl, 0);
        controller.pointerDown(2, rightAlt, 10);
        Set<KeySpec> rightKeys = Collections.newSetFromMap(new IdentityHashMap<>());
        rightKeys.add(rightAlt);

        controller.releasePressedKeys(rightKeys, ReleaseReason.ACTION_CANCEL);

        assertTrue(controller.getPressedKeysForTesting().contains(LogicalKey.LEFT_CTRL));
        assertTrue(!controller.getPressedKeysForTesting().contains(LogicalKey.RIGHT_ALT));
        assertEquals("U:RIGHT_ALT:2", transport.events.get(2));
        controller.pointerUp(1, 500);
        assertEquals("U:LEFT_CTRL:0", transport.events.get(3));
    }

    @Test
    public void momentaryFnMapsFunctionKeyWithoutSendingFn() {
        controller.pointerDown(1, find(LogicalKey.FN), 0);
        controller.pointerDown(2, find(LogicalKey.NUMBER_1), 10);
        controller.pointerUp(2, 20);
        controller.pointerUp(1, 500);

        assertEquals(2, transport.events.size());
        assertEquals("D:F1:0", transport.events.get(0));
        assertEquals("U:F1:0", transport.events.get(1));
    }

    @Test
    public void singleFnTapAppliesToNextKeyOnly() {
        controller.pointerDown(1, find(LogicalKey.FN), 0);
        controller.pointerUp(1, 50);
        assertTrue(controller.isOneShot(LogicalKey.FN));

        controller.pointerDown(2, find(LogicalKey.NUMBER_1), 100);
        controller.pointerUp(2, 110);

        assertEquals("D:F1:0", transport.events.get(0));
        assertEquals("U:F1:0", transport.events.get(1));
        assertTrue(!controller.isFnActive());
    }

    @Test
    public void quickDoubleFnTapLocksFunctionLayer() {
        controller.pointerDown(1, find(LogicalKey.FN), 0);
        controller.pointerUp(1, 50);
        controller.pointerDown(2, find(LogicalKey.FN), 100);
        controller.pointerUp(2, 150);

        assertTrue(controller.isFnActive());
        assertTrue(!controller.isOneShot(LogicalKey.FN));

        controller.pointerDown(3, find(LogicalKey.NUMBER_1), 200);
        controller.pointerUp(3, 210);
        controller.pointerDown(4, find(LogicalKey.NUMBER_2), 220);
        controller.pointerUp(4, 230);

        assertEquals("D:F1:0", transport.events.get(0));
        assertEquals("U:F1:0", transport.events.get(1));
        assertEquals("D:F2:0", transport.events.get(2));
        assertEquals("U:F2:0", transport.events.get(3));
        assertTrue(controller.isFnActive());
    }

    @Test
    public void shiftSpaceHangulModeSendsOrderedChord() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(SplitKeyboardPreferences.KEY_HANGUL_MODE,
                        HangulKeyMode.SHIFT_SPACE.name())
                .commit();
        controller = new KeyboardStateController(
                transport, new SplitKeyboardPreferences(context));

        controller.pointerDown(1, find(LogicalKey.FN), 0);
        controller.pointerUp(1, 40);
        controller.pointerDown(2, find(LogicalKey.RIGHT_ALT), 60);
        controller.pointerUp(2, 70);

        assertEquals("D:LEFT_SHIFT:1", transport.events.get(0));
        assertEquals("D:SPACE:1", transport.events.get(1));
        assertEquals("U:SPACE:1", transport.events.get(2));
        assertEquals("U:LEFT_SHIFT:0", transport.events.get(3));
    }

    private static KeySpec find(LogicalKey key) {
        for (KeyboardRowSpec row : SplitKeyboardLayout.createRows()) {
            for (KeySpec spec : row.leftKeys) {
                if (spec.logicalKey == key) return spec;
            }
            for (KeySpec spec : row.rightKeys) {
                if (spec.logicalKey == key) return spec;
            }
            for (KeySpec spec : row.navigationKeys) {
                if (spec.logicalKey == key) return spec;
            }
            if (row.centerKey != null && row.centerKey.logicalKey == key) {
                return row.centerKey;
            }
        }
        throw new AssertionError("Missing key " + key);
    }

    private static final class FakeTransport implements RemoteKeyboardTransport {
        final List<String> events = new ArrayList<>();
        final List<String> operations = new ArrayList<>();
        int prepareCount;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void prepareForKeyInput() {
            prepareCount++;
            operations.add("P");
        }

        @Override
        public boolean sendKeyDown(LogicalKey key, byte activeModifiers) {
            events.add("D:" + key + ":" + activeModifiers);
            operations.add("D:" + key);
            return true;
        }

        @Override
        public boolean sendKeyUp(LogicalKey key, byte activeModifiers) {
            events.add("U:" + key + ":" + activeModifiers);
            operations.add("U:" + key);
            return true;
        }

        @Override
        public void resetModifierState() {
        }
    }
}
