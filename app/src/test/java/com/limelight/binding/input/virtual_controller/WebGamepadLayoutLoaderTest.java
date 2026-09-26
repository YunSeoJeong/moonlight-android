package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.limelight.binding.input.virtual_controller.lol.LolVirtualGamepadPreferences;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.mockito.InOrder;
import org.json.JSONObject;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {
        com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class
})
public class WebGamepadLayoutLoaderTest {
    private Game previousGame;

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        Game.instance = previousGame;
        SubDisplayKeyboardControlsSession.getInstance().reset();
        SubDisplayKeyboardControlsSession.getInstance()
                .setTouchCompatibilityEnabled(false);
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
        touchpad.layout(0, 0, 100, 100);
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
        touchpad.layout(0, 0, 100, 100);
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

    @Test
    public void builtInLolAssetLoadsMainAndSubLayouts() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout mainFrame = new FrameLayout(context);
        mainFrame.layout(0, 0, 2160, 641);
        VirtualController mainController = new VirtualController(
                mock(ControllerHandler.class), mainFrame, null, context,
                VirtualController.DISPLAY_TARGET_MAIN, false, null,
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH, false);

        assertTrue(WebGamepadLayoutLoader.loadAsset(mainController, context,
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH));
        assertEquals(10, mainController.getElements().size());
        FrameLayout.LayoutParams stickParams = (FrameLayout.LayoutParams)
                mainController.getElements().get(0).getLayoutParams();
        assertEquals(430, stickParams.leftMargin);
        assertEquals(115, stickParams.topMargin);
        assertEquals(350, stickParams.width);
        assertEquals(350, stickParams.height);
        FrameLayout.LayoutParams trackpadParams = (FrameLayout.LayoutParams)
                mainController.getElements().get(5).getLayoutParams();
        assertEquals(1230, trackpadParams.leftMargin);
        assertEquals(195, trackpadParams.topMargin);
        assertEquals(570, trackpadParams.width);
        assertEquals(370, trackpadParams.height);

        FrameLayout subFrame = new FrameLayout(context);
        subFrame.layout(0, 0, 2376, 968);
        VirtualController subController = new VirtualController(
                mock(ControllerHandler.class), subFrame, null, context,
                VirtualController.DISPLAY_TARGET_SUB, false, null,
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH, false);

        assertTrue(WebGamepadLayoutLoader.loadAsset(subController, context,
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH));
        assertEquals(6, subController.getElements().size());
    }

    @Test
    public void lolAssetConfiguresTouchSynchronizationExceptions()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        JSONObject root;
        try (InputStream stream = context.getAssets().open(
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH)) {
            byte[] bytes = new byte[stream.available()];
            int read = stream.read(bytes);
            root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
        }

        JSONArray resolutions = root.getJSONArray("resolutions");
        int buttonCount = 0;
        int compatibilityTrackpadCount = 0;
        boolean leftAltFound = false;
        for (int i = 0; i < resolutions.length(); i++) {
            JSONArray components = resolutions.getJSONObject(i)
                    .getJSONArray("components");
            for (int j = 0; j < components.length(); j++) {
                JSONObject component = components.getJSONObject(j);
                JSONObject runtime = component.getJSONObject("runtime");
                if ("button".equals(component.getString("type"))) {
                    if ("lol_key_alt".equals(component.getString("id"))) {
                        assertFalse(runtime.getBoolean("syncTouchBeforeInput"));
                        assertEquals("LAlt", component.getString("label"));
                        assertEquals("AltLeft", runtime.getJSONObject("binding")
                                .getString("key"));
                        leftAltFound = true;
                    }
                    else {
                        assertTrue(runtime.getBoolean("syncTouchBeforeInput"));
                    }
                    buttonCount++;
                }
                else if ("touchpad".equals(component.getString("type"))) {
                    assertTrue(runtime.getBoolean("touchCompatibility"));
                    compatibilityTrackpadCount++;
                }
            }
        }

        assertEquals(14, buttonCount);
        assertEquals(1, compatibilityTrackpadCount);
        assertTrue(leftAltFound);
    }

    @Test
    public void lolButtonSynchronizesTouchBeforeKeyboardDown() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Game game = installConnectedGame();
        when(game.isSplitKeyboardMouseTransportConnected()).thenReturn(true);
        when(game.sendSplitKeyboardTouchEvent(
                org.mockito.ArgumentMatchers.anyByte(), anyInt(), anyFloat(), anyFloat()))
                .thenReturn(true);

        SubDisplayKeyboardControlsSession session =
                SubDisplayKeyboardControlsSession.getInstance();
        session.setCursorBounds(100, 100);
        session.setTouchCompatibilityEnabled(true);

        VirtualControllerElement button = createButtonElement(context, "{\n" +
                "  \"inputType\": \"keyboard\",\n" +
                "  \"behavior\": \"hold\",\n" +
                "  \"binding\": {\"key\": \"KeyQ\"},\n" +
                "  \"syncTouchBeforeInput\": true\n" +
                "}");
        button.onTouchEvent(MotionEvent.obtain(
                0, 1, MotionEvent.ACTION_DOWN, 50, 50, 0));

        InOrder order = inOrder(game);
        order.verify(game).sendSplitKeyboardTouchEvent(
                eq(MoonBridge.LI_TOUCH_EVENT_DOWN), anyInt(), anyFloat(), anyFloat());
        order.verify(game).sendSplitKeyboardTouchEvent(
                eq(MoonBridge.LI_TOUCH_EVENT_CANCEL), anyInt(), anyFloat(), anyFloat());
        order.verify(game).keyboardEvent(true, (short) android.view.KeyEvent.KEYCODE_Q);
    }

    @Test
    public void compatibleLolTrackpadMovesLocalCursorWithoutRelativeMousePacket()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Game game = installConnectedGame();
        when(game.isSplitKeyboardMouseTransportConnected()).thenReturn(true);

        SubDisplayKeyboardControlsSession session =
                SubDisplayKeyboardControlsSession.getInstance();
        session.setCursorBounds(100, 100);
        session.setInputSensitivities(100, 100, 100);
        session.setTouchCompatibilityEnabled(true);

        VirtualControllerElement touchpad = createTouchpadElement(context, "{\n" +
                "  \"inputType\": \"mouse\",\n" +
                "  \"mode\": \"relative\",\n" +
                "  \"binding\": {\"moveX\": \"MouseX\", \"moveY\": \"MouseY\"},\n" +
                "  \"touchCompatibility\": true\n" +
                "}");
        touchpad.onTouchEvent(pointerEvent(0, MotionEvent.ACTION_DOWN,
                new float[]{20, 20}));
        touchpad.onTouchEvent(pointerEvent(16, MotionEvent.ACTION_MOVE,
                new float[]{30, 25}));

        assertEquals(60f, session.getCursorX(), 0.01f);
        assertEquals(55f, session.getCursorY(), 0.01f);
        verify(game, never()).mouseMove(anyInt(), anyInt());
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

    private VirtualControllerElement createButtonElement(Context context, String runtime)
            throws Exception {
        FrameLayout frame = new FrameLayout(context);
        frame.layout(0, 0, 100, 100);
        VirtualController controller =
                new VirtualController(mock(ControllerHandler.class), frame, context);
        JSONObject component = new JSONObject(
                "{\"id\":\"button\",\"type\":\"button\",\"label\":\"Q\"," +
                        "\"runtime\":" + runtime + "}");
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
