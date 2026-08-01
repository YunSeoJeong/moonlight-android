package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.profiles.ProfilesManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SplitKeyboardPreferences {
    public static final String KEY_VISIBLE = "split_keyboard_visible";
    public static final String KEY_SUB_DISPLAY_MOUSE_CONTROLS =
            "split_keyboard_sub_display_mouse_controls";
    public static final String KEY_HIDE_SUB_DISPLAY_CONTROLS_UI =
            "split_keyboard_hide_sub_display_controls_ui";
    public static final String KEY_HANGUL_LABELS = "split_keyboard_hangul_labels";
    public static final String KEY_SHIFT_HANGUL_LABELS = "split_keyboard_shift_hangul_labels";
    public static final String KEY_HAPTIC = "split_keyboard_haptic";
    public static final String KEY_HAPTIC_STRENGTH = "split_keyboard_haptic_strength";
    public static final String KEY_CLICK_SOUND = "split_keyboard_click_sound";
    public static final String KEY_REPEAT_DELAY = "split_keyboard_repeat_delay";
    public static final String KEY_REPEAT_INTERVAL = "split_keyboard_repeat_interval";
    public static final String KEY_MODIFIER_MODE = "split_keyboard_modifier_mode";
    public static final String KEY_ONE_SHOT_MODIFIERS = "split_keyboard_one_shot_modifiers";
    public static final String KEY_FN_TAP_LOCK = "split_keyboard_fn_tap_lock";
    public static final String KEY_HANGUL_MODE = "split_keyboard_hangul_mode";
    public static final String KEY_HANGUL_CUSTOM = "split_keyboard_hangul_custom";
    public static final String KEY_KEY_GAP_DP = "split_keyboard_key_gap_dp";
    public static final String KEY_CENTER_GAP_PERCENT = "split_keyboard_center_gap_percent";
    public static final String KEY_OPACITY = "split_keyboard_opacity";
    public static final String KEY_HEIGHT_CORRECTION_DP = "split_keyboard_height_correction_dp";

    public enum ModifierMode {
        TOGGLE_AND_HOLD,
        MOMENTARY_ONLY;

        static ModifierMode parse(String value) {
            if (value != null) {
                try {
                    return valueOf(value);
                }
                catch (IllegalArgumentException ignored) {
                }
            }
            return TOGGLE_AND_HOLD;
        }
    }

    public final boolean visible;
    public final boolean subDisplayMouseControls;
    public final boolean hideSubDisplayControlsUi;
    public final boolean showHangulLabels;
    public final boolean showShiftHangulLabels;
    public final boolean hapticEnabled;
    public final int hapticStrength;
    public final boolean clickSoundEnabled;
    public final int repeatDelayMs;
    public final int repeatIntervalMs;
    public final ModifierMode modifierMode;
    public final boolean oneShotModifiers;
    public final boolean fnTapLockEnabled;
    public final HangulKeyMode hangulKeyMode;
    public final List<LogicalKey> customHangulShortcut;
    public final int keyGapDp;
    public final int centerGapPercent;
    public final int opacityPercent;
    public final int keyboardHeightCorrectionDp;

    public SplitKeyboardPreferences(Context context) {
        SharedPreferences preferences =
                ProfilesManager.getInstance().getOverlayingSharedPreferences(context);
        visible = preferences.getBoolean(KEY_VISIBLE, false);
        subDisplayMouseControls = preferences.getBoolean(
                KEY_SUB_DISPLAY_MOUSE_CONTROLS, false);
        hideSubDisplayControlsUi = preferences.getBoolean(
                KEY_HIDE_SUB_DISPLAY_CONTROLS_UI, false);
        showHangulLabels = preferences.getBoolean(KEY_HANGUL_LABELS, true);
        showShiftHangulLabels = preferences.getBoolean(KEY_SHIFT_HANGUL_LABELS, true);
        hapticEnabled = preferences.getBoolean(KEY_HAPTIC, true);
        hapticStrength = clamp(preferences.getInt(KEY_HAPTIC_STRENGTH, 96), 1, 255);
        clickSoundEnabled = preferences.getBoolean(KEY_CLICK_SOUND, false);
        repeatDelayMs = clamp(preferences.getInt(KEY_REPEAT_DELAY, 400), 250, 700);
        repeatIntervalMs = clamp(preferences.getInt(KEY_REPEAT_INTERVAL, 45), 25, 120);
        modifierMode = ModifierMode.parse(preferences.getString(
                KEY_MODIFIER_MODE, ModifierMode.TOGGLE_AND_HOLD.name()));
        oneShotModifiers = preferences.getBoolean(KEY_ONE_SHOT_MODIFIERS, true);
        fnTapLockEnabled = preferences.getBoolean(KEY_FN_TAP_LOCK, true);
        hangulKeyMode = HangulKeyMode.parse(preferences.getString(
                KEY_HANGUL_MODE, HangulKeyMode.NATIVE_HANGUL_KEY.name()));
        customHangulShortcut = parseShortcut(preferences.getString(
                KEY_HANGUL_CUSTOM, "CTRL+ALT+SPACE"));
        keyGapDp = clamp(preferences.getInt(KEY_KEY_GAP_DP, 2), 0, 8);
        centerGapPercent = clamp(preferences.getInt(KEY_CENTER_GAP_PERCENT, 20), 12, 30);
        opacityPercent = clamp(preferences.getInt(KEY_OPACITY, 100), 55, 100);
        keyboardHeightCorrectionDp = clamp(
                preferences.getInt(KEY_HEIGHT_CORRECTION_DP, 0), 0, 16);
    }

    private static List<LogicalKey> parseShortcut(String shortcut) {
        if (shortcut == null || shortcut.trim().isEmpty()) {
            return Collections.singletonList(LogicalKey.SPACE);
        }

        List<LogicalKey> keys = new ArrayList<>();
        for (String token : shortcut.toUpperCase(Locale.ROOT).split("\\+")) {
            LogicalKey key = LogicalKey.fromShortcutToken(token);
            if (key != null && !key.localOnly && key.transportSupported && !keys.contains(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            keys.add(LogicalKey.SPACE);
        }
        return Collections.unmodifiableList(keys);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
