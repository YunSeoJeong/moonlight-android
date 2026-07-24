package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class WebGamepadLayoutLoaderTest {
    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        FoldChordSession.reset();
        WebGamepadLayoutLoader.clearImportedLayout(context);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove("seekbar_virtual_gamepad_anti_deadzone")
                .remove("seekbar_osc_opacity")
                .remove("checkbox_hide_osc_settings_button")
                .apply();
    }

    @Test
    public void foldChordInputTypeCreatesNamedChordButton() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller =
                new VirtualController(mock(ControllerHandler.class), frame, context);

        JSONObject component = new JSONObject("{\n" +
                "  \"id\": \"foldchord-lr\",\n" +
                "  \"type\": \"button\",\n" +
                "  \"label\": \"LR\",\n" +
                "  \"runtime\": {\n" +
                "    \"inputtype\": \"foldchord\",\n" +
                "    \"binding\": {\"chord\": \"LR\"}\n" +
                "  }\n" +
                "}");
        Method createElement = WebGamepadLayoutLoader.class.getDeclaredMethod(
                "createElement", VirtualController.class, Context.class,
                JSONObject.class, PreferenceConfiguration.class);
        createElement.setAccessible(true);
        VirtualControllerElement element = (VirtualControllerElement) createElement.invoke(
                null, controller, context, component, new PreferenceConfiguration());

        assertNotNull(element);
        element.onTouchEvent(MotionEvent.obtain(0, 1_000,
                MotionEvent.ACTION_DOWN, 50, 50, 0));
        element.onTouchEvent(MotionEvent.obtain(0, 1_070,
                MotionEvent.ACTION_UP, 50, 50, 0));
    }

    @Test
    public void componentOpacitySupportsStyleAndFlatFields() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller =
                new VirtualController(mock(ControllerHandler.class), frame, context);
        Method createElement = WebGamepadLayoutLoader.class.getDeclaredMethod(
                "createElement", VirtualController.class, Context.class,
                JSONObject.class, PreferenceConfiguration.class);
        createElement.setAccessible(true);
        PreferenceConfiguration config = new PreferenceConfiguration();

        VirtualControllerElement styleOpacity = (VirtualControllerElement) createElement.invoke(
                null, controller, context, new JSONObject(
                        "{\"id\":\"style\",\"type\":\"button\",\"style\":{\"opacity\":0.3}," +
                                "\"opacity\":0.8}"), config);
        VirtualControllerElement flatOpacity = (VirtualControllerElement) createElement.invoke(
                null, controller, context, new JSONObject(
                        "{\"id\":\"flat\",\"type\":\"button\",\"opacity\":0}"), config);
        VirtualControllerElement clampedOpacity = (VirtualControllerElement) createElement.invoke(
                null, controller, context, new JSONObject(
                        "{\"id\":\"clamped\",\"type\":\"button\",\"style\":{\"opacity\":2}}"), config);
        VirtualControllerElement defaultOpacity = (VirtualControllerElement) createElement.invoke(
                null, controller, context, new JSONObject(
                        "{\"id\":\"default\",\"type\":\"button\"}"), config);

        assertEquals(0.3f, styleOpacity.getAlpha(), 0.001f);
        assertEquals(0f, flatOpacity.getAlpha(), 0.001f);
        assertEquals(1f, clampedOpacity.getAlpha(), 0.001f);
        assertEquals(1f, defaultOpacity.getAlpha(), 0.001f);
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

    @Test
    public void hideSettingsButtonPreferenceHidesConfigureButton() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        WebGamepadLayoutLoader.clearImportedLayout(context);
        WebGamepadLayoutLoader.saveImportedLayout(context, "{\n" +
                "  \"resolutions\": [{\n" +
                "    \"width\": 100,\n" +
                "    \"height\": 100,\n" +
                "    \"components\": []\n" +
                "  }]\n" +
                "}");
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("checkbox_hide_osc_settings_button", true)
                .apply();

        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller = new VirtualController(controllerHandler, frame, context);

        controller.refreshLayout();
        controller.show();

        Button configureButton = null;
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (child instanceof Button) {
                configureButton = (Button) child;
                break;
            }
        }

        assertNotNull(configureButton);
        assertEquals(View.GONE, configureButton.getVisibility());
    }
}
