package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.binding.input.ControllerHandler;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WebGamepadLayoutLoaderTest {
    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        WebGamepadLayoutLoader.clearImportedLayout(context);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove("seekbar_virtual_gamepad_anti_deadzone")
                .apply();
    }

    @Test
    public void touchpadAnalogStickMapsDragDeltaToRightStick() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        WebGamepadLayoutLoader.clearImportedLayout(context);
        WebGamepadLayoutLoader.saveImportedLayout(context, "{\n" +
                "  \"resolutions\": [{\n" +
                "    \"width\": 100,\n" +
                "    \"height\": 100,\n" +
                "    \"components\": [{\n" +
                "      \"id\": \"trackpad\",\n" +
                "      \"type\": \"touchpad\",\n" +
                "      \"originalType\": \"touchpad\",\n" +
                "      \"label\": \"TRACKPAD\",\n" +
                "      \"rect\": {\"x\": 0, \"y\": 0, \"w\": 100, \"h\": 100},\n" +
                "      \"runtime\": {\n" +
                "        \"inputType\": \"controller\",\n" +
                "        \"mode\": \"analogStick\",\n" +
                "        \"binding\": {\"axisX\": \"RightStickX\", \"axisY\": \"RightStickY\"},\n" +
                "        \"deadzone\": 0,\n" +
                "        \"maxDelta\": 24\n" +
                "      }\n" +
                "    }]\n" +
                "  }]\n" +
                "}");

        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller = new VirtualController(controllerHandler, frame, context);

        assertTrue(WebGamepadLayoutLoader.loadIfAvailable(controller, context));
        assertEquals(1, controller.getElements().size());

        VirtualControllerElement touchpad = controller.getElements().get(0);
        touchpad.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50, 50, 0));
        touchpad.onTouchEvent(MotionEvent.obtain(0, 16, MotionEvent.ACTION_MOVE, 74, 50, 0));

        verify(controllerHandler).reportOscState(
                eq(0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0x7FFE),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0));

        clearInvocations(controllerHandler);
        touchpad.onTouchEvent(MotionEvent.obtain(0, 32, MotionEvent.ACTION_MOVE, 74, 26, 0));

        verify(controllerHandler).reportOscState(
                eq(0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0x7FFE),
                eq((byte) 0),
                eq((byte) 0));

        clearInvocations(controllerHandler);
        touchpad.onTouchEvent(MotionEvent.obtain(0, 48, MotionEvent.ACTION_UP, 74, 26, 0));

        verify(controllerHandler).reportOscState(
                eq(0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0));
    }

    @Test
    public void touchpadAnalogStickAppliesClientAntiDeadzone() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        WebGamepadLayoutLoader.clearImportedLayout(context);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt("seekbar_virtual_gamepad_anti_deadzone", 12)
                .apply();
        WebGamepadLayoutLoader.saveImportedLayout(context, "{\n" +
                "  \"resolutions\": [{\n" +
                "    \"width\": 100,\n" +
                "    \"height\": 100,\n" +
                "    \"components\": [{\n" +
                "      \"id\": \"trackpad\",\n" +
                "      \"type\": \"touchpad\",\n" +
                "      \"originalType\": \"touchpad\",\n" +
                "      \"rect\": {\"x\": 0, \"y\": 0, \"w\": 100, \"h\": 100},\n" +
                "      \"runtime\": {\n" +
                "        \"inputType\": \"controller\",\n" +
                "        \"mode\": \"analogStick\",\n" +
                "        \"binding\": {\"axisX\": \"RightStickX\", \"axisY\": \"RightStickY\"},\n" +
                "        \"deadzone\": 0,\n" +
                "        \"maxDelta\": 24\n" +
                "      }\n" +
                "    }]\n" +
                "  }]\n" +
                "}");

        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller = new VirtualController(controllerHandler, frame, context);

        assertTrue(WebGamepadLayoutLoader.loadIfAvailable(controller, context));

        VirtualControllerElement touchpad = controller.getElements().get(0);
        touchpad.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50, 50, 0));
        touchpad.onTouchEvent(MotionEvent.obtain(0, 16, MotionEvent.ACTION_MOVE, 51, 50, 0));

        float rawMagnitude = 1f / 24f;
        short expected = (short) ((0.12f + rawMagnitude * 0.88f) * 0x7FFE);
        verify(controllerHandler).reportOscState(
                eq(0),
                eq((short) 0),
                eq((short) 0),
                eq(expected),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0));
    }
}
