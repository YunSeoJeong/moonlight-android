package com.limelight.binding.input.virtual_controller.splitkeyboard;

public enum HangulKeyMode {
    NATIVE_HANGUL_KEY,
    RIGHT_ALT,
    SHIFT_SPACE,
    CUSTOM_SHORTCUT;

    public static HangulKeyMode parse(String value) {
        if (value != null) {
            try {
                return valueOf(value);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
        return NATIVE_HANGUL_KEY;
    }
}
