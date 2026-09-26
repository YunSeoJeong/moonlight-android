package com.limelight.binding.input.virtual_controller.lol;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.binding.input.virtual_controller.splitkeyboard.SplitKeyboardPreferences;
import com.limelight.profiles.ProfilesManager;

/** Session-scoped preferences for the built-in League of Legends control surface. */
public final class LolVirtualGamepadPreferences {
    public static final String KEY_VISIBLE = "lol_virtual_gamepad_visible";
    public static final String LAYOUT_ASSET_PATH = "config/lol_gamepad.json";

    public final boolean visible;
    public final int compatibilityMouseSensitivityPercent;
    public final int keyboardHeightCorrectionDp;

    public LolVirtualGamepadPreferences(Context context) {
        SharedPreferences preferences = ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(context);
        visible = preferences.getBoolean(KEY_VISIBLE, false);
        compatibilityMouseSensitivityPercent = clamp(preferences.getInt(
                SplitKeyboardPreferences.KEY_COMPATIBILITY_MOUSE_SENSITIVITY, 100),
                10, 300);
        keyboardHeightCorrectionDp = clamp(preferences.getInt(
                SplitKeyboardPreferences.KEY_HEIGHT_CORRECTION_DP, 0), 0, 16);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
