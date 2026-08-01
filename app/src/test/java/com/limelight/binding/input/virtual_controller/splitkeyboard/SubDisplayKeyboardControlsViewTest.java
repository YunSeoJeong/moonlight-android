package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.RectF;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.Control;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class SubDisplayKeyboardControlsViewTest {
    private SubDisplayKeyboardControlsView view;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        view = new SubDisplayKeyboardControlsView(context);
    }

    @Test
    public void referenceSizeMatchesProvidedComponentRects() {
        view.layout(0, 0, 2376, 968);

        assertRect(Control.RB, 210f, 10f, 860f, 360f);
        assertRect(Control.RT, 400f, 460f, 1050f, 910f);
        assertRect(Control.LB, 1530f, 10f, 2180f, 360f);
        assertRect(Control.LT, 1350f, 460f, 2000f, 910f);
    }

    @Test
    public void componentRectsScaleWithTheActualSubDisplay() {
        view.layout(0, 0, 1188, 484);

        assertRect(Control.RB, 105f, 5f, 430f, 180f);
        assertRect(Control.RT, 200f, 230f, 525f, 455f);
        assertRect(Control.LB, 765f, 5f, 1090f, 180f);
        assertRect(Control.LT, 675f, 230f, 1000f, 455f);
    }

    private void assertRect(Control control, float left, float top,
                            float right, float bottom) {
        RectF actual = view.getControlBoundsForTesting(control);
        assertEquals(left, actual.left, 0.01f);
        assertEquals(top, actual.top, 0.01f);
        assertEquals(right, actual.right, 0.01f);
        assertEquals(bottom, actual.bottom, 0.01f);
    }
}
