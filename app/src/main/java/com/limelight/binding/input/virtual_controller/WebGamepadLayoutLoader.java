package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import android.app.Activity;
import android.content.SharedPreferences;

public class WebGamepadLayoutLoader {
    private static final String ASSET_PATH = "config/gamepad.json";
    private static final String IMPORTED_FILE_NAME = "gamepad.json";
    private static final String IMPORTED_LAYOUTS_DIR = "gamepad_layouts";
    private static final String PREF_NAME = "gamepad_layouts";
    private static final String ACTIVE_LAYOUT_PREF = "active_layout";
    private static final int DEFAULT_LAYER = 1;

    public static class ImportedLayout {
        public final String id;
        public final String name;
        public final boolean active;

        ImportedLayout(String id, String name, boolean active) {
            this.id = id;
            this.name = name;
            this.active = active;
        }
    }

    public static void saveImportedLayout(Context context, String json) throws JSONException {
        saveImportedLayout(context, json, null);
    }

    public static ImportedLayout saveImportedLayout(Context context, String json, String suggestedName) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray resolutions = root.optJSONArray("resolutions");
        if (resolutions == null || resolutions.length() == 0) {
            throw new JSONException("Missing resolutions");
        }

        File dir = getImportedLayoutsDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new JSONException("Unable to create layout directory");
        }

        String displayName = getDisplayName(root, suggestedName);
        String id = uniqueLayoutId(dir, displayName);
        try (FileOutputStream os = new FileOutputStream(new File(dir, id))) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JSONException(e.getMessage());
        }

        setActiveLayout(context, id);
        return new ImportedLayout(id, displayName, true);
    }

    public static boolean clearImportedLayout(Context context) {
        String active = getActiveLayoutId(context);
        if (active == null) {
            File file = getImportedLayoutFile(context);
            return !file.exists() || file.delete();
        }

        boolean removed = deleteImportedLayout(context, active);
        clearActiveLayout(context);
        return removed;
    }

    public static List<ImportedLayout> listImportedLayouts(Context context) {
        List<ImportedLayout> layouts = new ArrayList<>();
        String active = getActiveLayoutId(context);

        File legacy = getImportedLayoutFile(context);
        if (legacy.exists()) {
            layouts.add(new ImportedLayout(IMPORTED_FILE_NAME, getLayoutName(legacy), active == null));
        }

        File dir = getImportedLayoutsDir(context);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                layouts.add(new ImportedLayout(file.getName(), getLayoutName(file), file.getName().equals(active)));
            }
        }

        Collections.sort(layouts, Comparator.comparing(layout -> layout.name.toLowerCase(Locale.US)));
        return layouts;
    }

    public static void setActiveLayout(Context context, String id) {
        context.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putString(ACTIVE_LAYOUT_PREF, id)
                .apply();
    }

    public static void clearActiveLayout(Context context) {
        context.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
                .edit()
                .remove(ACTIVE_LAYOUT_PREF)
                .apply();
    }

    public static boolean deleteImportedLayout(Context context, String id) {
        File file = getLayoutFile(context, id);
        boolean removed = !file.exists() || file.delete();
        if (id != null && id.equals(getActiveLayoutId(context))) {
            clearActiveLayout(context);
        }
        return removed;
    }

    public static boolean loadIfAvailable(final VirtualController controller, final Context context) {
        try {
            String json = readLayout(context);
            if (json == null || json.trim().isEmpty()) {
                LimeLog.info("WebGamepadLayoutLoader: no layout json available for target=" +
                        controller.getDisplayTarget());
                return false;
            }

            JSONObject root = new JSONObject(json);
            PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
            LayoutTarget layoutTarget = getLayoutTarget(controller, context, config);
            JSONObject resolution = chooseResolution(root.optJSONArray("resolutions"), layoutTarget);
            if (resolution == null) {
                LimeLog.warning("WebGamepadLayoutLoader: no matching resolution for target=" +
                        layoutTarget.displayTarget + " layoutTarget=" +
                        layoutTarget.width + "x" + layoutTarget.height +
                        " source=" + config.virtualGamepadLayoutResolutionSource);
                return false;
            }

            JSONArray components = resolution.optJSONArray("components");
            JSONArray mouseRegions = resolution.optJSONArray("mouseRegions");
            boolean resolutionTargetMatchesExplicitly =
                    isExplicitDisplayTargetMatch(resolution, layoutTarget.displayTarget);

            LayoutTransform transform = getLayoutTransform(resolution, context, layoutTarget);
            int added = 0;

            List<JSONObject> mouseRegionList = new ArrayList<>();
            List<JSONObject> componentList = new ArrayList<>();

            if (components != null) {
                for (int i = 0; i < components.length(); i++) {
                    JSONObject component = components.optJSONObject(i);
                    if (component == null) {
                        continue;
                    }
                    if (!matchesComponentDisplayTarget(component, layoutTarget.displayTarget,
                            resolutionTargetMatchesExplicitly)) {
                        continue;
                    }
                    if (isMouseRegion(component)) {
                        mouseRegionList.add(component);
                    } else {
                        componentList.add(component);
                    }
                }
            }

            if (mouseRegions != null) {
                for (int i = 0; i < mouseRegions.length(); i++) {
                    JSONObject region = mouseRegions.optJSONObject(i);
                    if (region != null && matchesComponentDisplayTarget(region,
                            layoutTarget.displayTarget, resolutionTargetMatchesExplicitly)) {
                        mouseRegionList.add(region);
                    }
                }
            }

            Comparator<JSONObject> zComparator = Comparator.comparingInt(WebGamepadLayoutLoader::getZ);
            Collections.sort(mouseRegionList, zComparator);
            Collections.sort(componentList, zComparator);

            for (JSONObject region : mouseRegionList) {
                addMouseRegion(controller, context, region, transform, layoutTarget);
                added++;
            }

            for (JSONObject component : componentList) {
                VirtualControllerElement element = createElement(controller, context, component, config);
                if (element != null) {
                    addElement(controller, element, component, transform, layoutTarget);
                    added++;
                }
            }

            if (added == 0 && !resolutionTargetMatchesExplicitly) {
                LimeLog.warning("WebGamepadLayoutLoader: matched resolution has no usable elements for target=" +
                        layoutTarget.displayTarget + " resolution=" +
                        resolution.optInt("width") + "x" + resolution.optInt("height") +
                        " explicitResolutionTarget=" + resolutionTargetMatchesExplicitly +
                        " componentCandidates=" + componentList.size() +
                        " mouseRegionCandidates=" + mouseRegionList.size());
                return false;
            }

            LimeLog.info("WebGamepadLayoutLoader: loaded layout for target=" +
                    layoutTarget.displayTarget + " resolution=" +
                    resolution.optInt("width") + "x" + resolution.optInt("height") +
                    " layoutTarget=" + layoutTarget.width + "x" + layoutTarget.height +
                    " explicitResolutionTarget=" + resolutionTargetMatchesExplicitly +
                    " elementsAdded=" + added +
                    " components=" + componentList.size() +
                    " mouseRegions=" + mouseRegionList.size() +
                    " alignToReferenceView=" + layoutTarget.alignToReferenceView);
            return true;
        } catch (Exception e) {
            LimeLog.warning("Unable to load web gamepad layout: " + e.getMessage());
            return false;
        }
    }

    private static File getImportedLayoutFile(Context context) {
        return new File(context.getFilesDir(), IMPORTED_FILE_NAME);
    }

    private static File getImportedLayoutsDir(Context context) {
        return new File(context.getFilesDir(), IMPORTED_LAYOUTS_DIR);
    }

    private static String getActiveLayoutId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String active = prefs.getString(ACTIVE_LAYOUT_PREF, null);
        if (active != null && getLayoutFile(context, active).exists()) {
            return active;
        }
        return null;
    }

    private static File getLayoutFile(Context context, String id) {
        if (IMPORTED_FILE_NAME.equals(id)) {
            return getImportedLayoutFile(context);
        }
        return new File(getImportedLayoutsDir(context), id);
    }

    private static String readLayout(Context context) {
        String active = getActiveLayoutId(context);
        if (active != null) {
            String imported = readFile(getLayoutFile(context, active));
            if (imported != null && !imported.trim().isEmpty()) {
                return imported;
            }
        }

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

        return readFile(file);
    }

    private static String readFile(File file) {
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

    private static String getLayoutName(File file) {
        String json = readFile(file);
        if (json != null) {
            try {
                return getDisplayName(new JSONObject(json), stripJsonExtension(file.getName()));
            } catch (JSONException ignored) {
            }
        }
        return stripJsonExtension(file.getName());
    }

    private static String getDisplayName(JSONObject root, String suggestedName) {
        String name = root.optString("name", root.optString("title", ""));
        if (name.trim().isEmpty() && suggestedName != null) {
            name = suggestedName;
        }
        name = stripJsonExtension(name).trim();
        return name.isEmpty() ? "Imported Gamepad" : name;
    }

    private static String stripJsonExtension(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).endsWith(".json")
                ? value.substring(0, value.length() - 5)
                : value;
    }

    private static String uniqueLayoutId(File dir, String displayName) {
        String base = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) {
            base = "gamepad";
        }

        String id = base + ".json";
        int index = 2;
        while (new File(dir, id).exists()) {
            id = base + "_" + index + ".json";
            index++;
        }
        return id;
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

    private static JSONObject chooseResolution(JSONArray resolutions, LayoutTarget layoutTarget) {
        if (resolutions == null || resolutions.length() == 0) {
            return null;
        }

        int targetWidth = layoutTarget.width;
        int targetHeight = layoutTarget.height;
        JSONObject best = null;
        JSONObject bestExplicitEmpty = null;
        long bestScore = Long.MAX_VALUE;
        long bestExplicitEmptyScore = Long.MAX_VALUE;

        for (int i = 0; i < resolutions.length(); i++) {
            JSONObject candidate = resolutions.optJSONObject(i);
            if (candidate == null) {
                continue;
            }

            CandidateDisplayMatch displayMatch = getCandidateDisplayMatch(candidate, layoutTarget.displayTarget);
            if (!displayMatch.matches) {
                continue;
            }

            long widthDelta = candidate.optInt("width") - targetWidth;
            long heightDelta = candidate.optInt("height") - targetHeight;
            long score = widthDelta * widthDelta + heightDelta * heightDelta;

            if (hasTargetContent(candidate, layoutTarget.displayTarget,
                    displayMatch.resolutionTargetMatchesExplicitly)) {
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            } else if (displayMatch.resolutionTargetMatchesExplicitly &&
                    score < bestExplicitEmptyScore) {
                bestExplicitEmptyScore = score;
                bestExplicitEmpty = candidate;
            }
        }

        return best != null ? best : bestExplicitEmpty;
    }

    private static CandidateDisplayMatch getCandidateDisplayMatch(JSONObject resolution,
                                                                  String displayTarget) {
        String explicitTarget = readDisplayTarget(resolution);
        if (!explicitTarget.isEmpty()) {
            return new CandidateDisplayMatch(targetEquals(explicitTarget, displayTarget),
                    targetEquals(explicitTarget, displayTarget));
        }

        if (isMainDisplayTarget(displayTarget)) {
            return new CandidateDisplayMatch(true, false);
        }

        return new CandidateDisplayMatch(hasExplicitChildTarget(resolution, displayTarget), false);
    }

    private static boolean hasTargetContent(JSONObject resolution, String displayTarget,
                                            boolean resolutionTargetMatchesExplicitly) {
        JSONArray components = resolution.optJSONArray("components");
        if (hasTargetContent(components, displayTarget, resolutionTargetMatchesExplicitly)) {
            return true;
        }

        return hasTargetContent(resolution.optJSONArray("mouseRegions"), displayTarget,
                resolutionTargetMatchesExplicitly);
    }

    private static boolean hasTargetContent(JSONArray components, String displayTarget,
                                            boolean resolutionTargetMatchesExplicitly) {
        if (components == null) {
            return false;
        }

        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component != null && matchesComponentDisplayTarget(component, displayTarget,
                    resolutionTargetMatchesExplicitly)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitChildTarget(JSONObject resolution, String displayTarget) {
        JSONArray components = resolution.optJSONArray("components");
        if (hasExplicitChildTarget(components, displayTarget)) {
            return true;
        }
        return hasExplicitChildTarget(resolution.optJSONArray("mouseRegions"), displayTarget);
    }

    private static boolean hasExplicitChildTarget(JSONArray components, String displayTarget) {
        if (components == null) {
            return false;
        }

        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component == null) {
                continue;
            }
            String componentTarget = readDisplayTarget(component);
            if (!componentTarget.isEmpty() && targetEquals(componentTarget, displayTarget)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExplicitDisplayTargetMatch(JSONObject object, String displayTarget) {
        String explicitTarget = readDisplayTarget(object);
        return !explicitTarget.isEmpty() && targetEquals(explicitTarget, displayTarget);
    }

    private static boolean matchesComponentDisplayTarget(JSONObject component, String displayTarget,
                                                        boolean resolutionTargetMatchesExplicitly) {
        String explicitTarget = readDisplayTarget(component);
        if (!explicitTarget.isEmpty()) {
            return targetEquals(explicitTarget, displayTarget);
        }

        return resolutionTargetMatchesExplicitly || isMainDisplayTarget(displayTarget);
    }

    private static String readDisplayTarget(JSONObject object) {
        String target = object.optString("displayTarget", "");
        if (target.trim().isEmpty()) {
            target = object.optString("display", "");
        }

        JSONObject settings = object.optJSONObject("settings");
        if (target.trim().isEmpty() && settings != null) {
            target = settings.optString("displayTarget", settings.optString("display", ""));
        }

        return normalizeDisplayTarget(target);
    }

    private static String normalizeDisplayTarget(String target) {
        String value = normalize(target);
        if ("sub".equals(value) || "secondary".equals(value) || "cover".equals(value) ||
                "aux".equals(value) || "auxiliary".equals(value)) {
            return VirtualController.DISPLAY_TARGET_SUB;
        }
        if ("main".equals(value) || "primary".equals(value) || "stream".equals(value) ||
                "streaming".equals(value)) {
            return VirtualController.DISPLAY_TARGET_MAIN;
        }
        return "";
    }

    private static boolean targetEquals(String candidate, String displayTarget) {
        return normalizeDisplayTarget(candidate).equals(normalizeDisplayTarget(displayTarget));
    }

    private static boolean isMainDisplayTarget(String displayTarget) {
        return VirtualController.DISPLAY_TARGET_MAIN.equals(normalizeDisplayTarget(displayTarget));
    }

    private static class CandidateDisplayMatch {
        final boolean matches;
        final boolean resolutionTargetMatchesExplicitly;

        CandidateDisplayMatch(boolean matches, boolean resolutionTargetMatchesExplicitly) {
            this.matches = matches;
            this.resolutionTargetMatchesExplicitly = resolutionTargetMatchesExplicitly;
        }
    }

    private static LayoutTransform getLayoutTransform(JSONObject resolution,
                                                      Context context,
                                                      LayoutTarget layoutTarget) {
        int targetWidth = layoutTarget.width;
        int targetHeight = layoutTarget.height;
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int sourceWidth = Math.max(1, resolution.optInt("width", screen.widthPixels));
        int sourceHeight = Math.max(1, resolution.optInt("height", screen.heightPixels));
        double scale = Math.min((double) targetWidth / sourceWidth,
                (double) targetHeight / sourceHeight);
        double offsetX = (targetWidth - sourceWidth * scale) / 2.0;
        double offsetY = (targetHeight - sourceHeight * scale) / 2.0;
        return new LayoutTransform(scale, offsetX, offsetY);
    }

    private static LayoutTarget getLayoutTarget(VirtualController controller, Context context,
                                                PreferenceConfiguration config) {
        if (PreferenceConfiguration.VIRTUAL_GAMEPAD_LAYOUT_RESOLUTION_SOURCE_CLIENT
                .equals(config.virtualGamepadLayoutResolutionSource)) {
            return new LayoutTarget(
                    getClientTargetWidth(controller, context),
                    getClientTargetHeight(controller, context),
                    false,
                    controller.getDisplayTarget());
        }
        return new LayoutTarget(
                getTargetWidth(controller, context),
                getTargetHeight(controller, context),
                true,
                controller.getDisplayTarget());
    }

    private static int getTargetWidth(VirtualController controller, Context context) {
        int width = controller.getLayoutWidth();
        if (width > 0) {
            return width;
        }
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    private static int getTargetHeight(VirtualController controller, Context context) {
        int height = controller.getLayoutHeight();
        if (height > 0) {
            return height;
        }
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    private static int getClientTargetWidth(VirtualController controller, Context context) {
        int width = controller.getOverlayLayoutWidth();
        if (width > 0) {
            return width;
        }
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    private static int getClientTargetHeight(VirtualController controller, Context context) {
        int height = controller.getOverlayLayoutHeight();
        if (height > 0) {
            return height;
        }
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    private static void addElement(VirtualController controller, VirtualControllerElement element,
                                   JSONObject component, LayoutTransform transform,
                                   LayoutTarget layoutTarget) {
        JSONObject rect = getRect(component);
        controller.addElement(element,
                (int) Math.round(transform.offsetX + rect.optDouble("x") * transform.scale),
                (int) Math.round(transform.offsetY + rect.optDouble("y") * transform.scale),
                Math.max(20, (int) Math.round(rect.optDouble("w", 80) * transform.scale)),
                Math.max(20, (int) Math.round(rect.optDouble("h", 80) * transform.scale)),
                layoutTarget.alignToReferenceView);
    }

    private static void addMouseRegion(VirtualController controller, Context context,
                                       JSONObject component, LayoutTransform transform,
                                       LayoutTarget layoutTarget) {
        addElement(controller, new MouseRegionElement(controller, context),
                component, transform, layoutTarget);
    }

    private static JSONObject getRect(JSONObject component) {
        JSONObject rect = component.optJSONObject("rect");
        if (rect != null) {
            return rect;
        }

        JSONObject fallback = new JSONObject();
        try {
            fallback.put("x", component.optDouble("x"));
            fallback.put("y", component.optDouble("y"));
            fallback.put("w", component.optDouble("w", 80));
            fallback.put("h", component.optDouble("h", 80));
        } catch (JSONException ignored) {
        }
        return fallback;
    }

    private static class LayoutTransform {
        final double scale;
        final double offsetX;
        final double offsetY;

        LayoutTransform(double scale, double offsetX, double offsetY) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private static class LayoutTarget {
        final int width;
        final int height;
        final boolean alignToReferenceView;
        final String displayTarget;

        LayoutTarget(int width, int height, boolean alignToReferenceView) {
            this(width, height, alignToReferenceView, VirtualController.DISPLAY_TARGET_MAIN);
        }

        LayoutTarget(int width, int height, boolean alignToReferenceView, String displayTarget) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.alignToReferenceView = alignToReferenceView;
            this.displayTarget = displayTarget == null ?
                    VirtualController.DISPLAY_TARGET_MAIN : displayTarget;
        }
    }

    private static boolean isMouseRegion(JSONObject component) {
        JSONObject runtime = component.optJSONObject("runtime");
        return "region".equals(component.optString("type")) ||
                "mouseRegion".equals(component.optString("originalType")) ||
                "mouseFilter".equals(component.optString("subtype")) ||
                (runtime != null && "mouseFilter".equals(runtime.optString("role")));
    }

    private static int getZ(JSONObject component) {
        JSONObject runtime = component.optJSONObject("runtime");
        if (runtime != null) {
            return runtime.optInt("z", component.optInt("z", 0));
        }
        return component.optInt("z", 0);
    }

    private static VirtualControllerElement createElement(final VirtualController controller,
                                                          final Context context,
                                                          JSONObject component,
                                                          PreferenceConfiguration config) {
        JSONObject runtime = component.optJSONObject("runtime");
        if (runtime == null) {
            runtime = new JSONObject();
        }

        String type = component.optString("type", component.optString("originalType", "button"));
        String originalType = component.optString("originalType", type);
        String inputType = runtime.optString("inputType",
                runtime.optString("inputtype",
                        component.optString("inputType",
                                component.optString("inputtype", "controller"))));
        String label = component.optString("label", "");
        JSONObject style = component.optJSONObject("style");
        String shape = style != null ? style.optString("shape", component.optString("shape", "circle"))
                : component.optString("shape", "circle");

        int elementId = component.optString("id", type + ":" + label).hashCode();
        WebElement element;

        if ("foldchord".equals(normalize(inputType))) {
            int foldChordBit = parseFoldChordBinding(component, runtime, label);
            if (foldChordBit == 0) {
                LimeLog.warning("WebGamepadLayoutLoader: ignoring FoldChord element with invalid binding label=" +
                        label + " binding=" + runtime.opt("binding"));
                return null;
            }
            element = new WebButton(controller, context, elementId, inputType, runtime,
                    label, shape, false, foldChordBit);
            applyStyle(element, component, style);
            return element;
        }

        if ("mouseScroll".equals(type) || "mouseScroll".equals(originalType) ||
                "mouseScroll".equals(runtime.optString("role"))) {
            element = new WebMouseScroll(controller, context, elementId, runtime, label, shape);
            applyStyle(element, component, style);
            return element;
        }

        if (isAnalogStickTouchpad(type, originalType, runtime)) {
            element = new WebTouchpadAnalogStick(controller, context, elementId, runtime, label,
                    shape, config.virtualGamepadAntiDeadzone);
            applyStyle(element, component, style);
            applyMouseForwarding(element, component, style);
            return element;
        }

        if ("stick".equals(type) || "stick".equals(originalType)) {
            element = new WebStick(controller, context, elementId, inputType, runtime, label,
                    shape, config.virtualGamepadAntiDeadzone);
            applyStyle(element, component, style);
            applyMouseForwarding(element, component, style);
            return element;
        }

        if ("dpad".equals(type) || "dpad".equals(originalType)) {
            element = new WebDpad(controller, context, elementId, inputType, runtime, label, shape);
            applyStyle(element, component, style);
            applyMouseForwarding(element, component, style);
            return element;
        }

        if ("trigger".equals(type) || "trigger".equals(originalType)) {
            element = new WebButton(controller, context, elementId, inputType, runtime, label, shape, true);
            applyStyle(element, component, style);
            applyMouseForwarding(element, component, style);
            return element;
        }

        element = new WebButton(controller, context, elementId, inputType, runtime, label, shape, false);
        applyStyle(element, component, style);
        applyMouseForwarding(element, component, style);
        return element;
    }

    private static boolean isAnalogStickTouchpad(String type, String originalType, JSONObject runtime) {
        return ("touchpad".equals(normalize(type)) || "touchpad".equals(normalize(originalType))) &&
                ("analogstick".equals(normalize(runtime.optString("mode"))) ||
                        "analogstick".equals(normalize(runtime.optString("role"))));
    }

    private static void applyStyle(WebElement element, JSONObject component, JSONObject style) {
        if (style != null) {
            String colorValue = style.optString("color", null);
            String bgValue = style.optString("bg", null);
            if (!isEmptyColor(colorValue) || !isEmptyColor(bgValue)) {
                int normalColor = parseColor(colorValue, element.normalColor);
                int fillColor = parseColor(bgValue, Color.TRANSPARENT);
                element.setWebStyle(normalColor, fillColor);
            }
        }

        Double opacity = readComponentOpacity(component, style);
        if (opacity != null) {
            element.setAlpha((float) Math.max(0.0, Math.min(1.0, opacity)));
        }
    }

    private static Double readComponentOpacity(JSONObject component, JSONObject style) {
        Double opacity = readOpacity(style);
        return opacity != null ? opacity : readOpacity(component);
    }

    private static Double readOpacity(JSONObject owner) {
        if (owner == null || !owner.has("opacity") || owner.isNull("opacity")) {
            return null;
        }

        double opacity = owner.optDouble("opacity", Double.NaN);
        return Double.isNaN(opacity) || Double.isInfinite(opacity) ? null : opacity;
    }

    private static void applyMouseForwarding(WebElement element, JSONObject component, JSONObject style) {
        boolean mouseIgnore = true;
        if (style != null && style.has("mouseIgnore")) {
            mouseIgnore = style.optBoolean("mouseIgnore", true);
        } else if (component.has("mouseIgnore")) {
            mouseIgnore = component.optBoolean("mouseIgnore", true);
        }
        element.setForwardMousePosition(!mouseIgnore);
    }

    private static boolean isEmptyColor(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            return fallback;
        }
        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
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
            case "leftshoulder":
            case "leftbumper":
                return ControllerPacket.LB_FLAG;
            case "rb":
            case "r1":
            case "rightshoulder":
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
            case "lt":
            case "l2":
            case "lefttrigger":
            case "rt":
            case "r2":
            case "righttrigger":
                return 0;
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
                return 0;
        }
    }

    private static int parseFoldChordBinding(JSONObject component, JSONObject runtime,
                                             String label) {
        String value = readFoldChordBindingValue(runtime);
        if (value.isEmpty()) {
            value = readFoldChordBindingValue(component);
        }
        if (value.isEmpty()) {
            value = runtime.optString("chord",
                    runtime.optString("key",
                            component.optString("chord", component.optString("key", label))));
        }
        return FoldChordSession.parseBinding(value);
    }

    private static String readFoldChordBindingValue(JSONObject owner) {
        JSONObject binding = owner.optJSONObject("binding");
        if (binding != null) {
            return binding.optString("chord",
                    binding.optString("key",
                            binding.optString("button", binding.optString("code", ""))));
        }
        Object rawBinding = owner.opt("binding");
        if (rawBinding != null && rawBinding != JSONObject.NULL) {
            return String.valueOf(rawBinding);
        }
        return "";
    }

    private static int parseAndroidKey(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("digit") && normalized.length() == 6) {
            char digit = normalized.charAt(5);
            if (digit >= '0' && digit <= '9') {
                return KeyEvent.KEYCODE_0 + (digit - '0');
            }
        }
        if (normalized.length() >= 2 && normalized.charAt(0) == 'f') {
            try {
                int fKey = Integer.parseInt(normalized.substring(1));
                if (fKey >= 1 && fKey <= 12) {
                    return KeyEvent.KEYCODE_F1 + fKey - 1;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (normalized.startsWith("numpad") && normalized.length() == 7) {
            char digit = normalized.charAt(6);
            if (digit >= '0' && digit <= '9') {
                return KeyEvent.KEYCODE_NUMPAD_0 + (digit - '0');
            }
        }

        switch (normalized) {
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
            case "numpadenter": return KeyEvent.KEYCODE_NUMPAD_ENTER;
            case "tab": return KeyEvent.KEYCODE_TAB;
            case "backspace": return KeyEvent.KEYCODE_DEL;
            case "delete": return KeyEvent.KEYCODE_FORWARD_DEL;
            case "insert": return KeyEvent.KEYCODE_INSERT;
            case "home": return KeyEvent.KEYCODE_MOVE_HOME;
            case "end": return KeyEvent.KEYCODE_MOVE_END;
            case "pageup": return KeyEvent.KEYCODE_PAGE_UP;
            case "pagedown": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "arrowup": return KeyEvent.KEYCODE_DPAD_UP;
            case "arrowdown": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "arrowleft": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "arrowright": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "backquote": return KeyEvent.KEYCODE_GRAVE;
            case "minus": return KeyEvent.KEYCODE_MINUS;
            case "equal": return KeyEvent.KEYCODE_EQUALS;
            case "bracketleft": return KeyEvent.KEYCODE_LEFT_BRACKET;
            case "bracketright": return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case "backslash": return KeyEvent.KEYCODE_BACKSLASH;
            case "semicolon": return KeyEvent.KEYCODE_SEMICOLON;
            case "quote": return KeyEvent.KEYCODE_APOSTROPHE;
            case "comma": return KeyEvent.KEYCODE_COMMA;
            case "period": return KeyEvent.KEYCODE_PERIOD;
            case "slash": return KeyEvent.KEYCODE_SLASH;
            case "capslock": return KeyEvent.KEYCODE_CAPS_LOCK;
            case "numpaddivide": return KeyEvent.KEYCODE_NUMPAD_DIVIDE;
            case "numpadmultiply": return KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
            case "numpadsubtract": return KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
            case "numpadadd": return KeyEvent.KEYCODE_NUMPAD_ADD;
            case "numpaddecimal": return KeyEvent.KEYCODE_NUMPAD_DOT;
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

    private static void sendMouseScroll(short verticalAmount, short horizontalAmount) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseHighResScrollEvent(verticalAmount, horizontalAmount);
    }

    private static short clampShort(double value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
    }

    private static float normalizedAnalogDelta(float delta, double maxDelta, double sensitivity) {
        double safeMaxDelta = Math.max(1.0, maxDelta);
        double value = delta * sensitivity / safeMaxDelta;
        return (float) Math.max(-1.0, Math.min(1.0, value));
    }

    private static float applyAntiDeadzone(float value, float magnitude, float antiDeadzone) {
        if (antiDeadzone <= 0f || magnitude <= 0f) {
            return value;
        }

        float clampedAntiDeadzone = Math.max(0f, Math.min(1f, antiDeadzone));
        float outputMagnitude = clampedAntiDeadzone + magnitude * (1f - clampedAntiDeadzone);
        return value * Math.min(1f, outputMagnitude) / magnitude;
    }

    private static List<String> splitBindingValues(String value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        String[] parts = value.split("[,+]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<String> readBindingList(JSONObject binding, String arrayKey, String stringKey) {
        List<String> values = new ArrayList<>();
        if (binding == null) {
            return values;
        }

        JSONArray array = binding.optJSONArray(arrayKey);
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        if (values.isEmpty()) {
            values.addAll(splitBindingValues(binding.optString(stringKey, "")));
        }
        return values;
    }

    private static boolean isRightTrigger(String value, String label) {
        String target = normalize(value);
        return target.contains("right") || target.equals("rt") || target.equals("r2") ||
                normalize(label).equals("rt") || normalize(label).equals("r2");
    }

    private static boolean isTriggerBinding(String value) {
        String target = normalize(value);
        return target.equals("lt") || target.equals("l2") || target.equals("lefttrigger") ||
                target.equals("rt") || target.equals("r2") || target.equals("righttrigger");
    }

    private static abstract class WebElement extends VirtualControllerElement {
        protected final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected final RectF rect = new RectF();
        protected final String label;
        protected final String shape;
        protected int fillColor = Color.TRANSPARENT;
        private boolean forwardMousePosition;

        WebElement(VirtualController controller, Context context, int id, String label, String shape) {
            super(controller, context, id);
            this.label = label == null ? "" : label;
            this.shape = shape == null ? "circle" : shape;
        }

        void setWebStyle(int normalColor, int fillColor) {
            this.normalColor = normalColor;
            this.pressedColor = normalColor;
            this.fillColor = fillColor;
        }

        void setForwardMousePosition(boolean forwardMousePosition) {
            this.forwardMousePosition = forwardMousePosition;
        }

        protected void forwardMousePosition(MotionEvent event) {
            if (forwardMousePosition && Game.instance != null && Game.instance.connected) {
                Game.instance.updateMousePositionFromOverlay(this, event);
            }
        }

        protected void drawBody(Canvas canvas, boolean pressed) {
            canvas.drawColor(Color.TRANSPARENT);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            rect.set(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth() - paint.getStrokeWidth(), getHeight() - paint.getStrokeWidth());

            if (fillColor != Color.TRANSPARENT) {
                paint.setColor(fillColor);
                paint.setStyle(Paint.Style.FILL);
                drawShape(canvas);
            }

            paint.setColor(pressed ? pressedColor : getDefaultColor());
            paint.setStyle(Paint.Style.STROKE);
            drawShape(canvas);
        }

        private void drawShape(Canvas canvas) {
            if ("circle".equals(shape)) {
                canvas.drawOval(rect, paint);
            } else if ("pill".equals(shape)) {
                float radius = Math.min(rect.width(), rect.height()) / 2f;
                canvas.drawRoundRect(rect, radius, radius, paint);
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

    private static class WebMouseScroll extends WebElement {
        private static final long FRAME_DELAY_MS = 16;
        private static final double VELOCITY_SMOOTHING = 0.65;
        private static final double STOP_VELOCITY = 1.0;
        private static final double MOMENTUM_DECAY = 0.92;

        private final JSONObject runtime;
        private final String direction;
        private final double step;
        private final double sensitivity;
        private final boolean invert;
        private final boolean momentum;
        private final Runnable scrollFrameRunnable = new Runnable() {
            @Override
            public void run() {
                runScrollFrame();
            }
        };

        private VelocityTracker velocityTracker;
        private int activePointerId = -1;
        private boolean dragging;
        private boolean coasting;
        private double velocityX;
        private double velocityY;
        private double pendingVertical;
        private double pendingHorizontal;
        private long lastFrameTime;

        WebMouseScroll(VirtualController controller, Context context, int elementId,
                       JSONObject runtime, String label, String shape) {
            super(controller, context, elementId, label, shape);
            this.runtime = runtime;
            this.direction = normalize(runtime.optString("direction", "vertical"));
            this.step = Math.max(1.0, runtime.optDouble("step", 120.0));
            this.sensitivity = runtime.optDouble("sensitivity", 1.0);
            this.invert = runtime.optBoolean("invert", false);
            this.momentum = runtime.optBoolean("momentum", false);
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
            drawBody(canvas, dragging || coasting);
            drawLabel(canvas);
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startDrag(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateDrag(event);
                    return true;
                case MotionEvent.ACTION_SCROLL:
                    sendDirectWheel(event);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    endDrag();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            stopScrollLoop();
            recycleVelocityTracker();
            super.onDetachedFromWindow();
        }

        private void startDrag(MotionEvent event) {
            stopScrollLoop();
            recycleVelocityTracker();
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            activePointerId = event.getPointerId(0);
            dragging = true;
            coasting = false;
            velocityX = velocityY = 0;
            pendingVertical = pendingHorizontal = 0;
            lastFrameTime = event.getEventTime();
            virtualController.getHandler().post(scrollFrameRunnable);
            setPressed(true);
            invalidate();
        }

        private void updateDrag(MotionEvent event) {
            if (!dragging || velocityTracker == null || activePointerId < 0) {
                return;
            }

            int pointerIndex = event.findPointerIndex(activePointerId);
            if (pointerIndex < 0) {
                return;
            }

            velocityTracker.addMovement(event);
            velocityTracker.computeCurrentVelocity(1000);
            double newVelocityX = velocityTracker.getXVelocity(activePointerId);
            double newVelocityY = velocityTracker.getYVelocity(activePointerId);
            velocityX = velocityX * VELOCITY_SMOOTHING + newVelocityX * (1.0 - VELOCITY_SMOOTHING);
            velocityY = velocityY * VELOCITY_SMOOTHING + newVelocityY * (1.0 - VELOCITY_SMOOTHING);
        }

        private void endDrag() {
            dragging = false;
            recycleVelocityTracker();

            if (momentum && hasScrollVelocity()) {
                coasting = true;
                lastFrameTime = android.os.SystemClock.uptimeMillis();
                virtualController.getHandler().removeCallbacks(scrollFrameRunnable);
                virtualController.getHandler().post(scrollFrameRunnable);
            } else {
                stopScrollLoop();
            }

            setPressed(false);
            invalidate();
        }

        private void sendDirectWheel(MotionEvent event) {
            double multiplier = (invert ? -1.0 : 1.0) * sensitivity * step;
            short vertical = allowsVertical()
                    ? clampShort(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * multiplier)
                    : 0;
            short horizontal = allowsHorizontal()
                    ? clampShort(event.getAxisValue(MotionEvent.AXIS_HSCROLL) * multiplier)
                    : 0;
            if (vertical != 0 || horizontal != 0) {
                sendMouseScroll(vertical, horizontal);
            }
        }

        private void runScrollFrame() {
            if (!dragging && !coasting) {
                return;
            }

            long now = android.os.SystemClock.uptimeMillis();
            long elapsedMs = Math.max(1, Math.min(64, now - lastFrameTime));
            lastFrameTime = now;

            double sign = invert ? -1.0 : 1.0;
            if (allowsVertical()) {
                double axisSize = Math.max(1, getHeight());
                pendingVertical += (velocityY / axisSize) * step * sensitivity * sign * elapsedMs / 1000.0;
            }
            if (allowsHorizontal()) {
                double axisSize = Math.max(1, getWidth());
                pendingHorizontal += (-velocityX / axisSize) * step * sensitivity * sign * elapsedMs / 1000.0;
            }

            short vertical = clampShort((int) pendingVertical);
            short horizontal = clampShort((int) pendingHorizontal);
            if (vertical != 0 || horizontal != 0) {
                sendMouseScroll(vertical, horizontal);
                pendingVertical -= vertical;
                pendingHorizontal -= horizontal;
            }

            if (coasting) {
                velocityX *= MOMENTUM_DECAY;
                velocityY *= MOMENTUM_DECAY;
                if (!hasScrollVelocity()) {
                    stopScrollLoop();
                    invalidate();
                    return;
                }
            }

            virtualController.getHandler().postDelayed(scrollFrameRunnable, FRAME_DELAY_MS);
        }

        private boolean hasScrollVelocity() {
            return Math.abs(velocityX) > STOP_VELOCITY || Math.abs(velocityY) > STOP_VELOCITY;
        }

        private boolean allowsVertical() {
            return !"horizontal".equals(direction);
        }

        private boolean allowsHorizontal() {
            return "horizontal".equals(direction) || "both".equals(direction);
        }

        private void stopScrollLoop() {
            dragging = false;
            coasting = false;
            virtualController.getHandler().removeCallbacks(scrollFrameRunnable);
        }

        private void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            activePointerId = -1;
        }
    }

    private static class WebButton extends WebElement {
        private final String inputType;
        private final JSONObject runtime;
        private final boolean trigger;
        private final int foldChordBit;
        private final long foldChordToken;
        private boolean toggled;
        private boolean active;

        WebButton(VirtualController controller, Context context, int elementId, String inputType,
                  JSONObject runtime, String label, String shape, boolean trigger) {
            this(controller, context, elementId, inputType, runtime, label, shape, trigger, 0);
        }

        WebButton(VirtualController controller, Context context, int elementId, String inputType,
                  JSONObject runtime, String label, String shape, boolean trigger,
                  int foldChordBit) {
            super(controller, context, elementId, label, shape);
            this.inputType = inputType;
            this.runtime = runtime;
            this.trigger = trigger;
            this.foldChordBit = foldChordBit;
            this.foldChordToken = foldChordBit == 0 ? 0L : FoldChordSession.createInputToken();
            if (foldChordBit != 0) {
                setHapticFeedbackEnabled(true);
            }
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
                    forwardMousePosition(event);
                    handleDown(event.getEventTime());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    forwardMousePosition(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    forwardMousePosition(event);
                    handleUp(event.getEventTime());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    forwardMousePosition(event);
                    handleCancel(event.getEventTime());
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void handleDown(long eventTime) {
            if (foldChordBit != 0) {
                if (!active) {
                    active = true;
                    setPressed(true);
                    FoldChordSession.press(foldChordToken, foldChordBit, eventTime);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                return;
            }

            String behavior = runtime.optString("behavior", "hold");
            if ("toggle".equals(behavior)) {
                toggled = !toggled;
                setPressed(toggled);
                apply(toggled);
            } else if ("tap".equals(behavior)) {
                setPressed(true);
                apply(true);
                active = true;
                virtualController.getHandler().postDelayed(() -> {
                    if (active) {
                        active = false;
                        setPressed(false);
                        apply(false);
                        invalidate();
                    }
                }, 35);
            } else {
                active = true;
                setPressed(true);
                apply(true);
            }
        }

        private void handleUp(long eventTime) {
            if (foldChordBit != 0) {
                if (active) {
                    FoldChordSession.release(foldChordToken, eventTime);
                }
                active = false;
                setPressed(false);
                return;
            }

            String behavior = runtime.optString("behavior", "hold");
            if ("toggle".equals(behavior)) {
                return;
            }
            if (active) {
                apply(false);
            }
            active = false;
            setPressed(false);
        }

        private void handleCancel(long eventTime) {
            if (foldChordBit != 0 && active) {
                FoldChordSession.cancel(foldChordToken);
                active = false;
                setPressed(false);
            } else {
                handleUp(eventTime);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (foldChordBit != 0 && active) {
                FoldChordSession.cancel(foldChordToken);
                active = false;
                setPressed(false);
            }
            super.onDetachedFromWindow();
        }

        private void apply(boolean down) {
            JSONObject binding = runtime.optJSONObject("binding");
            if ("keyboard".equals(inputType)) {
                List<String> keys = readBindingList(binding, "keys", "key");
                if (keys.isEmpty()) {
                    keys.add(runtime.optString("binding"));
                }
                sendKeys(keys, down);
            } else if ("mouse".equals(inputType)) {
                List<String> buttons = readBindingList(binding, "buttons", "button");
                if (buttons.isEmpty()) {
                    buttons.add(runtime.optString("binding"));
                }
                sendMouseButtons(buttons, down);
            } else {
                VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
                String target = binding != null ?
                        binding.optString("axis", binding.optString("trigger", binding.optString("button"))) :
                            runtime.optString("binding");
                if (trigger || isTriggerBinding(target)) {
                    boolean right = isRightTrigger(target, label);
                    if (right) {
                        inputContext.rightTrigger = (byte) (down ? 0xFF : 0x00);
                    } else {
                        inputContext.leftTrigger = (byte) (down ? 0xFF : 0x00);
                    }
                } else {
                    int flag = 0;
                    List<String> buttons = readBindingList(binding, "buttons", "button");
                    if (buttons.isEmpty()) {
                        buttons.add(runtime.optString("binding"));
                    }
                    for (String button : buttons) {
                        flag |= parseControllerFlag(button, label);
                    }
                    if (down) {
                        inputContext.inputMap |= flag;
                    } else {
                        inputContext.inputMap &= ~flag;
                    }
                }
                virtualController.sendControllerInputContext();
            }
        }

        private void sendKeys(List<String> keys, boolean down) {
            if (down) {
                for (String key : keys) {
                    sendKeyboard(parseAndroidKey(key), true);
                }
            } else {
                for (int i = keys.size() - 1; i >= 0; i--) {
                    sendKeyboard(parseAndroidKey(keys.get(i)), false);
                }
            }
        }

        private void sendMouseButtons(List<String> buttons, boolean down) {
            if (down) {
                for (String button : buttons) {
                    int parsed = parseMouseButton(button);
                    if (parsed != 0) {
                        sendMouseButton(parsed, true);
                    }
                }
            } else {
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    int parsed = parseMouseButton(buttons.get(i));
                    if (parsed != 0) {
                        sendMouseButton(parsed, false);
                    }
                }
            }
        }
    }

    private static class WebDpad extends WebElement {
        private final String inputType;
        private final JSONObject runtime;
        private int direction;
        private final List<Integer> activeKeys = new ArrayList<>();
        private int activeControllerFlags;

        WebDpad(VirtualController controller, Context context, int elementId, String inputType,
                JSONObject runtime, String label, String shape) {
            super(controller, context, elementId, label, shape);
            this.inputType = inputType;
            this.runtime = runtime;
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
            drawBody(canvas, direction != 0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(getDefaultColor());
            float left = getWidth() * 0.33f;
            float right = getWidth() * 0.66f;
            float top = getHeight() * 0.33f;
            float bottom = getHeight() * 0.66f;
            canvas.drawLine(left, 0, left, getHeight(), paint);
            canvas.drawLine(right, 0, right, getHeight(), paint);
            canvas.drawLine(0, top, getWidth(), top, paint);
            canvas.drawLine(0, bottom, getWidth(), bottom, paint);
            drawLabel(canvas);
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    forwardMousePosition(event);
                    apply(directionFor(event.getX(), event.getY()));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    forwardMousePosition(event);
                    apply(0);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private int directionFor(float x, float y) {
            int result = 0;
            if (x < getWidth() * 0.33f) {
                result |= DigitalPad.DIGITAL_PAD_DIRECTION_LEFT;
            }
            if (x > getWidth() * 0.66f) {
                result |= DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT;
            }
            if (y < getHeight() * 0.33f) {
                result |= DigitalPad.DIGITAL_PAD_DIRECTION_UP;
            }
            if (y > getHeight() * 0.66f) {
                result |= DigitalPad.DIGITAL_PAD_DIRECTION_DOWN;
            }

            if (!"8way".equals(runtime.optString("mode", "4way")) &&
                    Integer.bitCount(result) > 1) {
                float dx = x - getWidth() / 2f;
                float dy = y - getHeight() / 2f;
                if (Math.abs(dx) > Math.abs(dy)) {
                    result &= dx < 0 ? DigitalPad.DIGITAL_PAD_DIRECTION_LEFT :
                            DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT;
                } else {
                    result &= dy < 0 ? DigitalPad.DIGITAL_PAD_DIRECTION_UP :
                            DigitalPad.DIGITAL_PAD_DIRECTION_DOWN;
                }
            }
            return result;
        }

        private void apply(int newDirection) {
            if (newDirection == direction) {
                return;
            }

            if ("keyboard".equals(inputType)) {
                for (int key : activeKeys) {
                    sendKeyboard(key, false);
                }
                activeKeys.clear();
                for (String value : bindingsFor(newDirection)) {
                    for (String key : splitBindingValues(value)) {
                        int parsed = parseAndroidKey(key);
                        if (parsed != KeyEvent.KEYCODE_UNKNOWN) {
                            activeKeys.add(parsed);
                            sendKeyboard(parsed, true);
                        }
                    }
                }
            } else {
                VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
                inputContext.inputMap &= ~activeControllerFlags;
                activeControllerFlags = 0;
                for (String value : bindingsFor(newDirection)) {
                    for (String button : splitBindingValues(value)) {
                        activeControllerFlags |= parseControllerFlag(button, "");
                    }
                }
                inputContext.inputMap |= activeControllerFlags;
                virtualController.sendControllerInputContext(10, 0x22);
            }

            direction = newDirection;
            setPressed(direction != 0);
        }

        private List<String> bindingsFor(int currentDirection) {
            List<String> values = new ArrayList<>();
            JSONObject bindings = runtime.optJSONObject("bindings");
            if (bindings == null || currentDirection == 0) {
                return values;
            }

            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0 &&
                    (currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0 &&
                    bindings.has("upLeft")) {
                values.add(bindings.optString("upLeft"));
                return values;
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0 &&
                    (currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0 &&
                    bindings.has("upRight")) {
                values.add(bindings.optString("upRight"));
                return values;
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0 &&
                    (currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0 &&
                    bindings.has("downLeft")) {
                values.add(bindings.optString("downLeft"));
                return values;
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0 &&
                    (currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0 &&
                    bindings.has("downRight")) {
                values.add(bindings.optString("downRight"));
                return values;
            }

            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                values.add(bindings.optString("up"));
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                values.add(bindings.optString("down"));
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                values.add(bindings.optString("left"));
            }
            if ((currentDirection & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                values.add(bindings.optString("right"));
            }
            return values;
        }
    }

    private static class WebTouchpadAnalogStick extends WebElement {
        private final JSONObject runtime;
        private final double sensitivity;
        private final boolean invertY;
        private final double maxDelta;
        private final float deadzone;
        private final float antiDeadzone;
        private final int pressFlag;
        private int activePointerId = -1;
        private float lastX;
        private float lastY;

        WebTouchpadAnalogStick(VirtualController controller, Context context, int elementId,
                               JSONObject runtime, String label, String shape, float antiDeadzone) {
            super(controller, context, elementId, label, shape);
            this.runtime = runtime;
            this.sensitivity = runtime.optDouble("sensitivity", 1.0);
            this.invertY = runtime.optBoolean("invertY", false);
            this.maxDelta = runtime.optDouble("maxDelta", 24.0);
            this.deadzone = (float) runtime.optDouble("deadzone", 0.15);
            this.antiDeadzone = antiDeadzone;

            JSONObject binding = runtime.optJSONObject("binding");
            this.pressFlag = binding != null ? parseControllerFlag(binding.optString("press", ""), "") : 0;
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
            drawBody(canvas, isPressed());
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(isPressed() ? pressedColor : getDefaultColor());
            float inset = Math.min(getWidth(), getHeight()) * 0.18f;
            canvas.drawLine(inset, getHeight() / 2f, getWidth() - inset, getHeight() / 2f, paint);
            canvas.drawLine(getWidth() / 2f, inset, getWidth() / 2f, getHeight() - inset, paint);
            drawLabel(canvas);
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startDrag(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateDrag(event);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    endDrag(event);
                    return true;
                default:
                    return true;
            }
        }

        private void startDrag(MotionEvent event) {
            forwardMousePosition(event);
            activePointerId = event.getPointerId(0);
            lastX = event.getX(0);
            lastY = event.getY(0);
            setPressed(true);
            applyPress(true);
            invalidate();
        }

        private void updateDrag(MotionEvent event) {
            if (activePointerId < 0) {
                return;
            }

            int pointerIndex = event.findPointerIndex(activePointerId);
            if (pointerIndex < 0) {
                return;
            }

            forwardMousePosition(event);
            float x = event.getX(pointerIndex);
            float y = event.getY(pointerIndex);
            float stickX = normalizedAnalogDelta(x - lastX, maxDelta, sensitivity);
            float stickY = normalizedAnalogDelta(y - lastY, maxDelta, sensitivity);
            if (invertY) {
                stickY = -stickY;
            }

            lastX = x;
            lastY = y;
            applyStick(stickX, stickY);
            invalidate();
        }

        private void endDrag(MotionEvent event) {
            forwardMousePosition(event);
            activePointerId = -1;
            setPressed(false);
            applyStick(0, 0);
            applyPress(false);
            invalidate();
        }

        private void applyStick(float x, float y) {
            float magnitude = magnitude(x, y);
            if (magnitude < deadzone) {
                x = 0;
                y = 0;
            } else if (magnitude > 1f) {
                x /= magnitude;
                y /= magnitude;
                magnitude = 1f;
            }

            if (x != 0 || y != 0) {
                magnitude = magnitude(x, y);
                x = applyAntiDeadzone(x, magnitude, antiDeadzone);
                y = applyAntiDeadzone(y, magnitude, antiDeadzone);
            }

            JSONObject binding = runtime.optJSONObject("binding");
            String axisX = binding != null ? binding.optString("axisX") : "";
            boolean right = normalize(axisX).contains("right");
            VirtualController.ControllerInputContext inputContext =
                    virtualController.getControllerInputContext();
            if (right) {
                inputContext.rightStickX = (short) (x * 0x7FFE);
                inputContext.rightStickY = (short) (-y * 0x7FFE);
            } else {
                inputContext.leftStickX = (short) (x * 0x7FFE);
                inputContext.leftStickY = (short) (-y * 0x7FFE);
            }
            virtualController.sendControllerInputContext(10, 0x11);
        }

        private void applyPress(boolean down) {
            if (pressFlag == 0) {
                return;
            }

            VirtualController.ControllerInputContext inputContext =
                    virtualController.getControllerInputContext();
            if (down) {
                inputContext.inputMap |= pressFlag;
            } else {
                inputContext.inputMap &= ~pressFlag;
            }
            virtualController.sendControllerInputContext();
        }

        private float magnitude(float x, float y) {
            return (float) Math.sqrt(x * x + y * y);
        }
    }

    private static class WebStick extends WebElement {
        private static final float DIAGONAL_THRESHOLD = 0.41421356f; // tan(22.5 degrees)

        private final String inputType;
        private final JSONObject runtime;
        private final float deadzone;
        private final float antiDeadzone;
        private int lastHorizontalKey = KeyEvent.KEYCODE_UNKNOWN;
        private int lastVerticalKey = KeyEvent.KEYCODE_UNKNOWN;
        private float normalizedStickX;
        private float normalizedStickY;

        WebStick(VirtualController controller, Context context, int elementId, String inputType,
                 JSONObject runtime, String label, String shape, float antiDeadzone) {
            super(controller, context, elementId, label, shape);
            this.inputType = inputType;
            this.runtime = runtime;
            this.deadzone = (float) runtime.optDouble("deadzone", 0.15);
            this.antiDeadzone = antiDeadzone;
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
            normalizeStickPosition(
                    (event.getX() - getWidth() / 2f) / (getWidth() / 2f),
                    (event.getY() - getHeight() / 2f) / (getHeight() / 2f));

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    forwardMousePosition(event);
                    setPressed(true);
                    apply(normalizedStickX, normalizedStickY);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    forwardMousePosition(event);
                    setPressed(false);
                    apply(0, 0);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void apply(float x, float y) {
            float magnitude = magnitude(x, y);
            if (magnitude < deadzone) {
                x = 0;
                y = 0;
            }

            if ("controller".equals(inputType) && (x != 0 || y != 0)) {
                magnitude = magnitude(x, y);
                x = applyAntiDeadzone(x, magnitude, antiDeadzone);
                y = applyAntiDeadzone(y, magnitude, antiDeadzone);
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

            int horizontal = KeyEvent.KEYCODE_UNKNOWN;
            int vertical = KeyEvent.KEYCODE_UNKNOWN;
            float absX = Math.abs(x);
            float absY = Math.abs(y);

            if (absX > 0 || absY > 0) {
                boolean eightWay = !"4way".equals(runtime.optString("mode", "8way"));
                boolean horizontalDominant = absX > absY;
                boolean diagonal = eightWay && Math.min(absX, absY) >= Math.max(absX, absY) * DIAGONAL_THRESHOLD;

                if (diagonal || horizontalDominant) {
                    horizontal = x < 0 ? parseAndroidKey(bindings.optString("left")) :
                            parseAndroidKey(bindings.optString("right"));
                }
                if (diagonal || !horizontalDominant) {
                    vertical = y < 0 ? parseAndroidKey(bindings.optString("up")) :
                            parseAndroidKey(bindings.optString("down"));
                }
            }

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

        private void normalizeStickPosition(float x, float y) {
            x = clamp(x);
            y = clamp(y);

            float magnitude = magnitude(x, y);
            if (magnitude > 1f) {
                x /= magnitude;
                y /= magnitude;
            }

            normalizedStickX = x;
            normalizedStickY = y;
        }

        private float magnitude(float x, float y) {
            return (float) Math.sqrt(x * x + y * y);
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
