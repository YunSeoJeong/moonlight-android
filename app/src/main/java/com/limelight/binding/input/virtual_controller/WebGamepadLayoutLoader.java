package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class WebGamepadLayoutLoader {
    private static final String ASSET_PATH = "config/gamepad.json";
    private static final String IMPORTED_FILE_NAME = "gamepad.json";
    private static final int DEFAULT_LAYER = 1;

    public static void saveImportedLayout(Context context, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray resolutions = root.optJSONArray("resolutions");
        if (resolutions == null || resolutions.length() == 0) {
            throw new JSONException("Missing resolutions");
        }

        try (FileOutputStream os = new FileOutputStream(getImportedLayoutFile(context))) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JSONException(e.getMessage());
        }
    }

    public static boolean clearImportedLayout(Context context) {
        File file = getImportedLayoutFile(context);
        return !file.exists() || file.delete();
    }

    public static boolean loadIfAvailable(final VirtualController controller, final Context context) {
        try {
            String json = readLayout(context);
            if (json == null || json.trim().isEmpty()) {
                return false;
            }

            JSONObject root = new JSONObject(json);
            JSONObject resolution = chooseResolution(root.optJSONArray("resolutions"), context);
            if (resolution == null) {
                return false;
            }

            JSONArray components = resolution.optJSONArray("components");
            JSONArray mouseRegions = resolution.optJSONArray("mouseRegions");
            if ((components == null || components.length() == 0) &&
                    (mouseRegions == null || mouseRegions.length() == 0)) {
                return false;
            }

            float scale = getScale(resolution, context);
            int added = 0;

            if (components != null) {
                for (int i = 0; i < components.length(); i++) {
                    JSONObject component = components.optJSONObject(i);
                    if (component != null && isMouseRegion(component)) {
                        addMouseRegion(controller, context, component, scale);
                        added++;
                    }
                }
            }

            if (mouseRegions != null) {
                for (int i = 0; i < mouseRegions.length(); i++) {
                    JSONObject region = mouseRegions.optJSONObject(i);
                    if (region != null) {
                        addMouseRegion(controller, context, region, scale);
                        added++;
                    }
                }
            }

            if (components != null) {
                for (int i = 0; i < components.length(); i++) {
                    JSONObject component = components.optJSONObject(i);
                    if (component == null || isMouseRegion(component)) {
                        continue;
                    }
                    VirtualControllerElement element = createElement(controller, context, component);
                    if (element != null) {
                        addElement(controller, element, component, scale);
                        added++;
                    }
                }
            }

            if (added == 0) {
                return false;
            }

            controller.setOpacity(PreferenceConfiguration.readPreferences(context).oscOpacity);
            return true;
        } catch (Exception e) {
            LimeLog.warning("Unable to load web gamepad layout: " + e.getMessage());
            return false;
        }
    }

    private static File getImportedLayoutFile(Context context) {
        return new File(context.getFilesDir(), IMPORTED_FILE_NAME);
    }

    private static String readLayout(Context context) {
        String imported = readImportedLayout(context);
        if (imported != null && !imported.trim().isEmpty()) {
            return imported;
        }
        return readAsset(context);
    }

    private static String readImportedLayout(Context context) {
        File file = getImportedLayoutFile(context);
        if (!file.exists()) {
            return null;
        }

        try (FileInputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = is.read(buffer);
            if (read <= 0) {
                return null;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LimeLog.warning("Unable to read imported gamepad layout: " + e.getMessage());
            return null;
        }
    }

    private static String readAsset(Context context) {
        try (InputStream is = context.getAssets().open(ASSET_PATH)) {
            byte[] buffer = new byte[is.available()];
            int read = is.read(buffer);
            if (read <= 0) {
                return null;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject chooseResolution(JSONArray resolutions, Context context) {
        if (resolutions == null || resolutions.length() == 0) {
            return null;
        }

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        JSONObject best = null;
        long bestScore = Long.MAX_VALUE;

        for (int i = 0; i < resolutions.length(); i++) {
            JSONObject candidate = resolutions.optJSONObject(i);
            if (candidate == null) {
                continue;
            }

            JSONArray components = candidate.optJSONArray("components");
            JSONArray mouseRegions = candidate.optJSONArray("mouseRegions");
            boolean empty = (components == null || components.length() == 0) &&
                    (mouseRegions == null || mouseRegions.length() == 0);
            if (empty) {
                continue;
            }

            long widthDelta = candidate.optInt("width") - screen.widthPixels;
            long heightDelta = candidate.optInt("height") - screen.heightPixels;
            long score = widthDelta * widthDelta + heightDelta * heightDelta;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static float getScale(JSONObject resolution, Context context) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int sourceWidth = Math.max(1, resolution.optInt("width", screen.widthPixels));
        int sourceHeight = Math.max(1, resolution.optInt("height", screen.heightPixels));
        return Math.min((float) screen.widthPixels / sourceWidth,
                (float) screen.heightPixels / sourceHeight);
    }

    private static void addElement(VirtualController controller, VirtualControllerElement element,
                                   JSONObject component, float scale) {
        JSONObject rect = getRect(component);
        controller.addElement(element,
                Math.round(rect.optInt("x") * scale),
                Math.round(rect.optInt("y") * scale),
                Math.max(20, Math.round(rect.optInt("w", 80) * scale)),
                Math.max(20, Math.round(rect.optInt("h", 80) * scale)));
    }

    private static void addMouseRegion(VirtualController controller, Context context,
                                       JSONObject component, float scale) {
        addElement(controller, new MouseRegionElement(controller, context),
                component, scale);
    }

    private static JSONObject getRect(JSONObject component) {
        JSONObject rect = component.optJSONObject("rect");
        if (rect != null) {
            return rect;
        }

        JSONObject fallback = new JSONObject();
        try {
            fallback.put("x", component.optInt("x"));
            fallback.put("y", component.optInt("y"));
            fallback.put("w", component.optInt("w", 80));
            fallback.put("h", component.optInt("h", 80));
        } catch (JSONException ignored) {
        }
        return fallback;
    }

    private static boolean isMouseRegion(JSONObject component) {
        JSONObject runtime = component.optJSONObject("runtime");
        return "region".equals(component.optString("type")) ||
                "mouseRegion".equals(component.optString("originalType")) ||
                "mouseFilter".equals(component.optString("subtype")) ||
                (runtime != null && "mouseFilter".equals(runtime.optString("role")));
    }

    private static VirtualControllerElement createElement(final VirtualController controller,
                                                          final Context context,
                                                          JSONObject component) {
        JSONObject runtime = component.optJSONObject("runtime");
        if (runtime == null) {
            runtime = new JSONObject();
        }

        String type = component.optString("type", component.optString("originalType", "button"));
        String originalType = component.optString("originalType", type);
        String inputType = runtime.optString("inputType", component.optString("inputType", "controller"));
        String label = component.optString("label", "");
        JSONObject style = component.optJSONObject("style");
        String shape = style != null ? style.optString("shape", component.optString("shape", "circle"))
                : component.optString("shape", "circle");

        int elementId = component.optString("id", type + ":" + label).hashCode();

        if ("stick".equals(type) || "stick".equals(originalType)) {
            return new WebStick(controller, context, elementId, inputType, runtime, label, shape);
        }

        if ("trigger".equals(type) || "trigger".equals(originalType)) {
            return new WebButton(controller, context, elementId, inputType, runtime, label, shape, true);
        }

        return new WebButton(controller, context, elementId, inputType, runtime, label, shape, false);
    }

    private static int parseControllerFlag(String binding, String label) {
        String value = normalize(binding.isEmpty() ? label : binding);
        switch (value) {
            case "a":
            case "buttona":
                return ControllerPacket.A_FLAG;
            case "b":
            case "buttonb":
                return ControllerPacket.B_FLAG;
            case "x":
            case "buttonx":
                return ControllerPacket.X_FLAG;
            case "y":
            case "buttony":
                return ControllerPacket.Y_FLAG;
            case "lb":
            case "l1":
            case "leftbumper":
                return ControllerPacket.LB_FLAG;
            case "rb":
            case "r1":
            case "rightbumper":
                return ControllerPacket.RB_FLAG;
            case "back":
            case "select":
            case "minus":
                return ControllerPacket.BACK_FLAG;
            case "start":
            case "plus":
            case "play":
                return ControllerPacket.PLAY_FLAG;
            case "guide":
            case "home":
                return ControllerPacket.SPECIAL_BUTTON_FLAG;
            case "l3":
            case "leftstickbutton":
                return ControllerPacket.LS_CLK_FLAG;
            case "r3":
            case "rightstickbutton":
                return ControllerPacket.RS_CLK_FLAG;
            case "touchpad":
                return ControllerPacket.TOUCHPAD_FLAG;
            case "dpadup":
            case "up":
                return ControllerPacket.UP_FLAG;
            case "dpaddown":
            case "down":
                return ControllerPacket.DOWN_FLAG;
            case "dpadleft":
            case "left":
                return ControllerPacket.LEFT_FLAG;
            case "dpadright":
            case "right":
                return ControllerPacket.RIGHT_FLAG;
            default:
                return 0;
        }
    }

    private static int parseMouseButton(String value) {
        switch (normalize(value)) {
            case "mouseleft":
            case "left":
                return EvdevListener.BUTTON_LEFT;
            case "mouseright":
            case "right":
                return EvdevListener.BUTTON_RIGHT;
            case "mousemiddle":
            case "middle":
                return EvdevListener.BUTTON_MIDDLE;
            case "mousex1":
            case "x1":
                return EvdevListener.BUTTON_X1;
            case "mousex2":
            case "x2":
                return EvdevListener.BUTTON_X2;
            default:
                return EvdevListener.BUTTON_LEFT;
        }
    }

    private static int parseAndroidKey(String value) {
        switch (normalize(value)) {
            case "keya": return KeyEvent.KEYCODE_A;
            case "keyb": return KeyEvent.KEYCODE_B;
            case "keyc": return KeyEvent.KEYCODE_C;
            case "keyd": return KeyEvent.KEYCODE_D;
            case "keye": return KeyEvent.KEYCODE_E;
            case "keyf": return KeyEvent.KEYCODE_F;
            case "keyg": return KeyEvent.KEYCODE_G;
            case "keyh": return KeyEvent.KEYCODE_H;
            case "keyi": return KeyEvent.KEYCODE_I;
            case "keyj": return KeyEvent.KEYCODE_J;
            case "keyk": return KeyEvent.KEYCODE_K;
            case "keyl": return KeyEvent.KEYCODE_L;
            case "keym": return KeyEvent.KEYCODE_M;
            case "keyn": return KeyEvent.KEYCODE_N;
            case "keyo": return KeyEvent.KEYCODE_O;
            case "keyp": return KeyEvent.KEYCODE_P;
            case "keyq": return KeyEvent.KEYCODE_Q;
            case "keyr": return KeyEvent.KEYCODE_R;
            case "keys": return KeyEvent.KEYCODE_S;
            case "keyt": return KeyEvent.KEYCODE_T;
            case "keyu": return KeyEvent.KEYCODE_U;
            case "keyv": return KeyEvent.KEYCODE_V;
            case "keyw": return KeyEvent.KEYCODE_W;
            case "keyx": return KeyEvent.KEYCODE_X;
            case "keyy": return KeyEvent.KEYCODE_Y;
            case "keyz": return KeyEvent.KEYCODE_Z;
            case "space": return KeyEvent.KEYCODE_SPACE;
            case "shiftleft": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "shiftright": return KeyEvent.KEYCODE_SHIFT_RIGHT;
            case "controlleft": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "controlright": return KeyEvent.KEYCODE_CTRL_RIGHT;
            case "altleft": return KeyEvent.KEYCODE_ALT_LEFT;
            case "altright": return KeyEvent.KEYCODE_ALT_RIGHT;
            case "escape": return KeyEvent.KEYCODE_ESCAPE;
            case "enter": return KeyEvent.KEYCODE_ENTER;
            case "tab": return KeyEvent.KEYCODE_TAB;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.US);
    }

    private static void sendKeyboard(int keyCode, boolean down) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.keyboardEvent(down, (short) keyCode);
    }

    private static void sendMouseButton(int button, boolean down) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseButtonEvent(button, down);
    }

    private static abstract class WebElement extends VirtualControllerElement {
        protected final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected final RectF rect = new RectF();
        protected final String label;
        protected final String shape;

        WebElement(VirtualController controller, Context context, int id, String label, String shape) {
            super(controller, context, id);
            this.label = label == null ? "" : label;
            this.shape = shape == null ? "circle" : shape;
        }

        protected void drawBody(Canvas canvas, boolean pressed) {
            canvas.drawColor(Color.TRANSPARENT);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(pressed ? pressedColor : getDefaultColor());
            paint.setStyle(Paint.Style.STROKE);
            rect.set(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth() - paint.getStrokeWidth(), getHeight() - paint.getStrokeWidth());
            if ("circle".equals(shape) || "pill".equals(shape)) {
                canvas.drawOval(rect, paint);
            } else {
                canvas.drawRoundRect(rect, 10, 10, paint);
            }
        }

        protected void drawLabel(Canvas canvas) {
            if (label.isEmpty()) {
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(16, getWidth() * 0.25f));
            paint.setColor(getDefaultColor());
            canvas.drawText(label, getWidth() / 2f,
                    getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f, paint);
        }
    }

    private static class WebButton extends WebElement {
        private final String inputType;
        private final JSONObject runtime;
        private final boolean trigger;

        WebButton(VirtualController controller, Context context, int elementId, String inputType,
                  JSONObject runtime, String label, String shape, boolean trigger) {
            super(controller, context, elementId, label, shape);
            this.inputType = inputType;
            this.runtime = runtime;
            this.trigger = trigger;
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
            drawBody(canvas, isPressed());
            drawLabel(canvas);
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(true);
                    apply(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    apply(false);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void apply(boolean down) {
            JSONObject binding = runtime.optJSONObject("binding");
            if ("keyboard".equals(inputType)) {
                sendKeyboard(parseAndroidKey(binding != null ? binding.optString("key") : runtime.optString("binding")), down);
            } else if ("mouse".equals(inputType)) {
                sendMouseButton(parseMouseButton(binding != null ? binding.optString("button") : runtime.optString("binding")), down);
            } else {
                VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
                if (trigger || "LeftTrigger".equals(runtime.optString("binding")) ||
                        "RightTrigger".equals(runtime.optString("binding"))) {
                    String target = binding != null ? binding.optString("trigger") : runtime.optString("binding");
                    boolean right = normalize(target).contains("right") || normalize(label).equals("rt");
                    if (right) {
                        inputContext.rightTrigger = (byte) (down ? 0xFF : 0x00);
                    } else {
                        inputContext.leftTrigger = (byte) (down ? 0xFF : 0x00);
                    }
                } else {
                    int flag = parseControllerFlag(binding != null ? binding.optString("button") : runtime.optString("binding"), label);
                    if (down) {
                        inputContext.inputMap |= flag;
                    } else {
                        inputContext.inputMap &= ~flag;
                    }
                }
                virtualController.sendControllerInputContext();
            }
        }
    }

    private static class WebStick extends WebElement {
        private final String inputType;
        private final JSONObject runtime;
        private final float deadzone;
        private int lastHorizontalKey = KeyEvent.KEYCODE_UNKNOWN;
        private int lastVerticalKey = KeyEvent.KEYCODE_UNKNOWN;

        WebStick(VirtualController controller, Context context, int elementId, String inputType,
                 JSONObject runtime, String label, String shape) {
            super(controller, context, elementId, label, shape);
            this.inputType = inputType;
            this.runtime = runtime;
            this.deadzone = (float) runtime.optDouble("deadzone", 0.15);
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
            drawBody(canvas, isPressed());
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(isPressed() ? pressedColor : getDefaultColor());
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                    Math.min(getWidth(), getHeight()) * 0.16f, paint);
            drawLabel(canvas);
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            float x = clamp((event.getX() - getWidth() / 2f) / (getWidth() / 2f));
            float y = clamp((event.getY() - getHeight() / 2f) / (getHeight() / 2f));

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    setPressed(true);
                    apply(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    apply(0, 0);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void apply(float x, float y) {
            if (Math.abs(x) < deadzone) {
                x = 0;
            }
            if (Math.abs(y) < deadzone) {
                y = 0;
            }

            if ("keyboard".equals(inputType)) {
                applyKeyboardStick(x, y);
                return;
            }

            JSONObject binding = runtime.optJSONObject("binding");
            String axisX = binding != null ? binding.optString("axisX") : "";
            boolean right = normalize(axisX).contains("right");
            VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
            if (right) {
                inputContext.rightStickX = (short) (x * 0x7FFE);
                inputContext.rightStickY = (short) (-y * 0x7FFE);
            } else {
                inputContext.leftStickX = (short) (x * 0x7FFE);
                inputContext.leftStickY = (short) (-y * 0x7FFE);
            }
            virtualController.sendControllerInputContext(10, 0x11);
        }

        private void applyKeyboardStick(float x, float y) {
            JSONObject bindings = runtime.optJSONObject("bindings");
            if (bindings == null) {
                return;
            }

            int horizontal = x < 0 ? parseAndroidKey(bindings.optString("left")) :
                    x > 0 ? parseAndroidKey(bindings.optString("right")) : KeyEvent.KEYCODE_UNKNOWN;
            int vertical = y < 0 ? parseAndroidKey(bindings.optString("up")) :
                    y > 0 ? parseAndroidKey(bindings.optString("down")) : KeyEvent.KEYCODE_UNKNOWN;

            if (horizontal != lastHorizontalKey) {
                sendKeyboard(lastHorizontalKey, false);
                sendKeyboard(horizontal, true);
                lastHorizontalKey = horizontal;
            }
            if (vertical != lastVerticalKey) {
                sendKeyboard(lastVerticalKey, false);
                sendKeyboard(vertical, true);
                lastVerticalKey = vertical;
            }
        }

        private float clamp(float value) {
            return Math.max(-1f, Math.min(1f, value));
        }
    }

    private static class MouseRegionElement extends VirtualControllerElement {
        MouseRegionElement(VirtualController controller, Context context) {
            super(controller, context, 0);
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            return true;
        }
    }
}
