package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.Game;
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
    private Game previousGame;

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        Game.instance = previousGame;
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
    public void relativeMouseTouchpadAppliesSensitivityAndAxisInversion() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        VirtualControllerElement touchpad = createTouchpadElement(context, "{\n" +
                "  \"inputType\": \"mouse\",\n" +
                "  \"mode\": \"relative\",\n" +
                "  \"binding\": {\"moveX\": \"MouseX\", \"moveY\": \"MouseY\"},\n" +
                "  \"sensitivity\": 5,\n" +
                "  \"invertX\": true,\n" +
                "  \"invertY\": false\n" +
                "}");
        Game game = installConnectedGame();

        touchpad.onTouchEvent(pointerEvent(0, MotionEvent.ACTION_DOWN,
                new float[]{20, 20}));
        touchpad.onTouchEvent(pointerEvent(16, MotionEvent.ACTION_MOVE,
                new float[]{23, 24}));

        verify(game).mouseMove(-15, 20);
    }

    @Test
    public void relativeMouseTouchpadSendsTwoFingerVerticalScrollAndResumesPointer() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        VirtualControllerElement touchpad = createTouchpadElement(context, "{\n" +
                "  \"inputType\": \"mouse\",\n" +
                "  \"mode\": \"relative\",\n" +
                "  \"binding\": {\n" +
                "    \"moveX\": \"MouseX\",\n" +
                "    \"moveY\": \"MouseY\",\n" +
                "    \"scrollY\": \"MouseWheelY\"\n" +
                "  },\n" +
                "  \"sensitivity\": 2,\n" +
                "  \"multiTouch\": true\n" +
                "}");
        Game game = installConnectedGame();

        touchpad.onTouchEvent(pointerEvent(0, MotionEvent.ACTION_DOWN,
                new float[]{10, 20}));
        touchpad.onTouchEvent(pointerEvent(8,
                MotionEvent.ACTION_POINTER_DOWN |
                        (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{10, 20, 30, 20}));
        touchpad.onTouchEvent(pointerEvent(16, MotionEvent.ACTION_MOVE,
                new float[]{10, 60, 30, 60}));

        verify(game).mouseHighResScrollEvent((short) 80, (short) 0);

        touchpad.onTouchEvent(pointerEvent(24,
                MotionEvent.ACTION_POINTER_UP |
                        (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{10, 60, 30, 60}));
        touchpad.onTouchEvent(pointerEvent(32, MotionEvent.ACTION_MOVE,
                new float[]{10, 64}));

        verify(game).mouseMove(0, 8);
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

    private VirtualControllerElement createTouchpadElement(Context context, String runtime)
            throws Exception {
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller =
                new VirtualController(mock(ControllerHandler.class), frame, context);
        JSONObject component = new JSONObject(
                "{\"id\":\"trackpad\",\"type\":\"touchpad\",\"originalType\":\"touchpad\"," +
                        "\"label\":\"TRACKPAD\",\"runtime\":" + runtime + "}");
        Method createElement = WebGamepadLayoutLoader.class.getDeclaredMethod(
                "createElement", VirtualController.class, Context.class,
                JSONObject.class, PreferenceConfiguration.class);
        createElement.setAccessible(true);
        VirtualControllerElement element = (VirtualControllerElement) createElement.invoke(
                null, controller, context, component, new PreferenceConfiguration());
        assertNotNull(element);
        element.layout(0, 0, 100, 100);
        return element;
    }

    private Game installConnectedGame() {
        previousGame = Game.instance;
        Game game = mock(Game.class);
        game.connected = true;
        Game.instance = game;
        return game;
    }

    private static MotionEvent pointerEvent(long eventTime, int action, float[] coordinates) {
        int pointerCount = coordinates.length / 2;
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] pointerCoords =
                new MotionEvent.PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;

            pointerCoords[i] = new MotionEvent.PointerCoords();
            pointerCoords[i].x = coordinates[i * 2];
            pointerCoords[i].y = coordinates[i * 2 + 1];
            pointerCoords[i].pressure = 1;
            pointerCoords[i].size = 1;
        }

        return MotionEvent.obtain(
                0,
                eventTime,
                action,
                pointerCount,
                properties,
                pointerCoords,
                0,
                0,
                1,
                1,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
    }
}
