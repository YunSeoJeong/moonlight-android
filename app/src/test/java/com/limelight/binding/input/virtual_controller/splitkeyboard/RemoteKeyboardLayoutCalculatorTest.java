package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class RemoteKeyboardLayoutCalculatorTest {
    private final RemoteKeyboardLayoutCalculator calculator =
            new RemoteKeyboardLayoutCalculator();

    @Test
    public void fold6InnerDisplayUsesExactRequestedSplit() {
        RemoteKeyboardLayoutCalculator.Bounds bounds =
                calculator.calculate(2160, 1856, true);

        assertEquals(new Rect(0, 0, 2160, 1215), bounds.remoteRect);
        assertEquals(new Rect(0, 1215, 2160, 1856), bounds.keyboardRect);
        assertEquals(641, bounds.keyboardRect.height());
        assertEquals(bounds.remoteRect.bottom, bounds.keyboardRect.top);
    }

    @Test
    public void hiddenKeyboardReturnsWholeContentBounds() {
        RemoteKeyboardLayoutCalculator.Bounds bounds =
                calculator.calculate(2160, 1856, false);

        assertEquals(new Rect(0, 0, 2160, 1856), bounds.remoteRect);
        assertTrue(bounds.keyboardRect.isEmpty());
    }

    @Test
    public void narrowLandscapeRetainsRemoteAspectAndKeyboardArea() {
        RemoteKeyboardLayoutCalculator.Bounds bounds =
                calculator.calculate(2400, 1080, true);

        assertEquals(16f / 9f,
                (float) bounds.remoteRect.width() / bounds.remoteRect.height(), 0.01f);
        assertEquals(1080, bounds.keyboardRect.bottom);
        assertEquals(bounds.remoteRect.bottom, bounds.keyboardRect.top);
        assertTrue(bounds.keyboardRect.height() > 0);
    }

}
