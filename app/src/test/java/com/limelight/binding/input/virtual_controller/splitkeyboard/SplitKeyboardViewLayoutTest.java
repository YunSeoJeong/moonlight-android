package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class SplitKeyboardViewLayoutTest {
    private Context context;
    private SplitKeyboardView keyboardView;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        SplitKeyboardPreferences preferences = new SplitKeyboardPreferences(context);
        KeyboardStateController stateController = new KeyboardStateController(
                new ConnectedNoOpTransport(), preferences);
        keyboardView = new SplitKeyboardView(context);
        keyboardView.bind(stateController, preferences);
    }

    @Test
    public void compatibilityPreferenceAddsTopCenterSessionToggle() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(SplitKeyboardPreferences.KEY_SUB_DISPLAY_MOUSE_CONTROLS, true)
                .putBoolean(SplitKeyboardPreferences.KEY_MOUSE_TOUCH_COMPATIBILITY, true)
                .commit();
        SplitKeyboardPreferences preferences = new SplitKeyboardPreferences(context);
        keyboardView = new SplitKeyboardView(context);
        keyboardView.bind(new KeyboardStateController(
                new ConnectedNoOpTransport(), preferences), preferences);
        keyboardView.measure(
                View.MeasureSpec.makeMeasureSpec(2160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(641, View.MeasureSpec.EXACTLY));
        keyboardView.layout(0, 0, 2160, 641);

        assertEquals(SplitKeyboardLayout.KEY_COUNT + 1, keyboardView.getChildCount());
        View toggle = keyboardView.getChildAt(SplitKeyboardLayout.KEY_COUNT);
        assertTrue(toggle.getLeft() < 1080 && toggle.getRight() > 1080);
        assertTrue(toggle.getTop() < keyboardView.getHeight() / 3);
        assertTrue(toggle.getContentDescription().toString().contains(
                context.getString(R.string.split_keyboard_compatibility_off)));

        float x = (toggle.getLeft() + toggle.getRight()) / 2f;
        float y = (toggle.getTop() + toggle.getBottom()) / 2f;
        keyboardView.onTouchEvent(MotionEvent.obtain(
                0, 0, MotionEvent.ACTION_DOWN, x, y, 0));
        keyboardView.onTouchEvent(MotionEvent.obtain(
                0, 10, MotionEvent.ACTION_UP, x, y, 0));

        SubDisplayKeyboardControlsSession session =
                SubDisplayKeyboardControlsSession.getInstance();
        assertTrue(session.isTouchCompatibilityEnabled());
        assertTrue(toggle.getContentDescription().toString().contains(
                context.getString(R.string.split_keyboard_compatibility_on)));
        session.setTouchCompatibilityEnabled(false);
    }

    @Test
    public void fold6KeyboardAreaContainsEveryKeyWithoutOverlap() {
        keyboardView.measure(
                View.MeasureSpec.makeMeasureSpec(2160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(641, View.MeasureSpec.EXACTLY));
        keyboardView.layout(0, 0, 2160, 641);

        assertEquals(SplitKeyboardLayout.KEY_COUNT, keyboardView.getChildCount());
        for (int i = 0; i < keyboardView.getChildCount(); i++) {
            View child = keyboardView.getChildAt(i);
            assertTrue("positive width for child " + i, child.getWidth() > 0);
            assertTrue("positive height for child " + i, child.getHeight() > 0);
            assertTrue("left bound for child " + i, child.getLeft() >= 0);
            assertTrue("top bound for child " + i, child.getTop() >= 0);
            assertTrue("right bound for child " + i, child.getRight() <= 2160);
            assertTrue("bottom bound for child " + i, child.getBottom() <= 641);

            Rect childRect = new Rect(child.getLeft(), child.getTop(),
                    child.getRight(), child.getBottom());
            for (int j = 0; j < i; j++) {
                View previous = keyboardView.getChildAt(j);
                Rect previousRect = new Rect(previous.getLeft(), previous.getTop(),
                        previous.getRight(), previous.getBottom());
                assertFalse("children overlap: " + i + " and " + j,
                        Rect.intersects(childRect, previousRect));
            }
        }
    }

    @Test
    public void ordinaryKeysUseOneCommonWidthAcrossEveryRow() {
        keyboardView.measure(
                View.MeasureSpec.makeMeasureSpec(2160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(641, View.MeasureSpec.EXACTLY));
        keyboardView.layout(0, 0, 2160, 641);

        List<KeySpec> specs = specsInChildOrder();
        int ordinaryWidth = -1;
        for (int i = 0; i < specs.size(); i++) {
            KeySpec spec = specs.get(i);
            if (spec.widthWeight != 1f || spec.logicalKey == LogicalKey.SPACE) {
                continue;
            }
            int width = keyboardView.getChildAt(i).getWidth();
            if (ordinaryWidth == -1) {
                ordinaryWidth = width;
            }
            assertEquals("common width for " + spec.logicalKey, ordinaryWidth, width);
        }

        assertTrue(widthOf(LogicalKey.BACKSPACE, specs) > ordinaryWidth);
        assertTrue(widthOf(LogicalKey.ENTER, specs) > ordinaryWidth);
        assertTrue(widthOf(LogicalKey.LEFT_SHIFT, specs) > ordinaryWidth);
        assertTrue(widthOf(LogicalKey.ESCAPE, specs) > ordinaryWidth);
        assertTrue(widthOf(LogicalKey.TAB, specs) > ordinaryWidth);
        assertTrue(widthOf(LogicalKey.CAPS_LOCK, specs) > ordinaryWidth);
    }

    @Test
    public void leftAndRightGroupsStayWithinOneOrdinaryKeyOfEachOther() {
        for (KeyboardRowSpec row : SplitKeyboardLayout.createRows()) {
            float difference = Math.abs(totalUnits(row.leftKeys)
                    - totalUnits(row.rightKeys));
            assertTrue("unbalanced row by " + difference + " key units",
                    difference <= 1f);
        }
    }

    @Test
    public void leftLetterRowsStaggerDownAndToTheRight() {
        List<KeyboardRowSpec> rows = SplitKeyboardLayout.createRows();
        float qStart = leadingUnits(rows.get(1).leftKeys, LogicalKey.KEY_Q);
        float aStart = leadingUnits(rows.get(2).leftKeys, LogicalKey.KEY_A);
        float zStart = leadingUnits(rows.get(3).leftKeys, LogicalKey.KEY_Z);

        assertTrue("A must start to the right of Q", aStart > qStart);
        assertTrue("Z must start to the right of A", zStart > aStart);
        assertEquals(0.5f, aStart - qStart, 0f);
        assertEquals(0.5f, zStart - aStart, 0f);
    }

    private int widthOf(LogicalKey key, List<KeySpec> specs) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).logicalKey == key) {
                return keyboardView.getChildAt(i).getWidth();
            }
        }
        throw new AssertionError("Missing key " + key);
    }

    private static List<KeySpec> specsInChildOrder() {
        List<KeySpec> specs = new ArrayList<>();
        for (KeyboardRowSpec row : SplitKeyboardLayout.createRows()) {
            specs.addAll(row.leftKeys);
            specs.addAll(row.rightKeys);
            specs.addAll(row.navigationKeys);
            if (row.centerKey != null) {
                specs.add(row.centerKey);
            }
        }
        return specs;
    }

    private static float totalUnits(List<KeySpec> specs) {
        float units = 0f;
        for (KeySpec spec : specs) {
            units += spec.widthWeight;
        }
        return units;
    }

    private static float leadingUnits(List<KeySpec> specs, LogicalKey key) {
        float units = 0f;
        for (KeySpec spec : specs) {
            if (spec.logicalKey == key) {
                return units;
            }
            units += spec.widthWeight;
        }
        throw new AssertionError("Missing key " + key);
    }

    private static final class ConnectedNoOpTransport implements RemoteKeyboardTransport {
        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean sendKeyDown(LogicalKey key, byte activeModifiers) {
            return true;
        }

        @Override
        public boolean sendKeyUp(LogicalKey key, byte activeModifiers) {
            return true;
        }

        @Override
        public void resetModifierState() {
        }
    }
}
