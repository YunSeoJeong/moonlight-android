package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.view.KeyEvent;

import java.util.Locale;

public enum LogicalKey {
    ESCAPE(KeyEvent.KEYCODE_ESCAPE),
    F1(KeyEvent.KEYCODE_F1), F2(KeyEvent.KEYCODE_F2), F3(KeyEvent.KEYCODE_F3),
    F4(KeyEvent.KEYCODE_F4), F5(KeyEvent.KEYCODE_F5), F6(KeyEvent.KEYCODE_F6),
    F7(KeyEvent.KEYCODE_F7), F8(KeyEvent.KEYCODE_F8), F9(KeyEvent.KEYCODE_F9),
    F10(KeyEvent.KEYCODE_F10), F11(KeyEvent.KEYCODE_F11), F12(KeyEvent.KEYCODE_F12),

    GRAVE(KeyEvent.KEYCODE_GRAVE, true),
    NUMBER_1(KeyEvent.KEYCODE_1, true), NUMBER_2(KeyEvent.KEYCODE_2, true),
    NUMBER_3(KeyEvent.KEYCODE_3, true), NUMBER_4(KeyEvent.KEYCODE_4, true),
    NUMBER_5(KeyEvent.KEYCODE_5, true), NUMBER_6(KeyEvent.KEYCODE_6, true),
    NUMBER_7(KeyEvent.KEYCODE_7, true), NUMBER_8(KeyEvent.KEYCODE_8, true),
    NUMBER_9(KeyEvent.KEYCODE_9, true), NUMBER_0(KeyEvent.KEYCODE_0, true),
    MINUS(KeyEvent.KEYCODE_MINUS, true), EQUALS(KeyEvent.KEYCODE_EQUALS, true),
    BACKSPACE(KeyEvent.KEYCODE_DEL, true),

    TAB(KeyEvent.KEYCODE_TAB),
    KEY_Q(KeyEvent.KEYCODE_Q, true), KEY_W(KeyEvent.KEYCODE_W, true),
    KEY_E(KeyEvent.KEYCODE_E, true), KEY_R(KeyEvent.KEYCODE_R, true),
    KEY_T(KeyEvent.KEYCODE_T, true), KEY_Y(KeyEvent.KEYCODE_Y, true),
    KEY_U(KeyEvent.KEYCODE_U, true), KEY_I(KeyEvent.KEYCODE_I, true),
    KEY_O(KeyEvent.KEYCODE_O, true), KEY_P(KeyEvent.KEYCODE_P, true),
    LEFT_BRACKET(KeyEvent.KEYCODE_LEFT_BRACKET, true),
    RIGHT_BRACKET(KeyEvent.KEYCODE_RIGHT_BRACKET, true),
    BACKSLASH(KeyEvent.KEYCODE_BACKSLASH, true),

    CAPS_LOCK(KeyEvent.KEYCODE_CAPS_LOCK),
    KEY_A(KeyEvent.KEYCODE_A, true), KEY_S(KeyEvent.KEYCODE_S, true),
    KEY_D(KeyEvent.KEYCODE_D, true), KEY_F(KeyEvent.KEYCODE_F, true),
    KEY_G(KeyEvent.KEYCODE_G, true), KEY_H(KeyEvent.KEYCODE_H, true),
    KEY_J(KeyEvent.KEYCODE_J, true), KEY_K(KeyEvent.KEYCODE_K, true),
    KEY_L(KeyEvent.KEYCODE_L, true),
    SEMICOLON(KeyEvent.KEYCODE_SEMICOLON, true),
    APOSTROPHE(KeyEvent.KEYCODE_APOSTROPHE, true),
    ENTER(KeyEvent.KEYCODE_ENTER),

    LEFT_SHIFT(KeyEvent.KEYCODE_SHIFT_LEFT, false, ModifierType.SHIFT),
    RIGHT_SHIFT(KeyEvent.KEYCODE_SHIFT_RIGHT, false, ModifierType.SHIFT),
    KEY_Z(KeyEvent.KEYCODE_Z, true), KEY_X(KeyEvent.KEYCODE_X, true),
    KEY_C(KeyEvent.KEYCODE_C, true), KEY_V(KeyEvent.KEYCODE_V, true),
    KEY_B(KeyEvent.KEYCODE_B, true), KEY_N(KeyEvent.KEYCODE_N, true),
    KEY_M(KeyEvent.KEYCODE_M, true),
    COMMA(KeyEvent.KEYCODE_COMMA, true), PERIOD(KeyEvent.KEYCODE_PERIOD, true),
    SLASH(KeyEvent.KEYCODE_SLASH, true),

    LEFT_CTRL(KeyEvent.KEYCODE_CTRL_LEFT, false, ModifierType.CTRL),
    RIGHT_CTRL(KeyEvent.KEYCODE_CTRL_RIGHT, false, ModifierType.CTRL),
    LEFT_ALT(KeyEvent.KEYCODE_ALT_LEFT, false, ModifierType.ALT),
    RIGHT_ALT(KeyEvent.KEYCODE_ALT_RIGHT, false, ModifierType.ALT),
    LEFT_META(KeyEvent.KEYCODE_META_LEFT, false, ModifierType.META),
    RIGHT_META(KeyEvent.KEYCODE_META_RIGHT, false, ModifierType.META),
    MENU(KeyEvent.KEYCODE_MENU),
    SPACE(KeyEvent.KEYCODE_SPACE, true),
    HANGUL_TOGGLE(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
    FN(KeyEvent.KEYCODE_UNKNOWN, false, null, true),
    KEYBOARD_HIDE(KeyEvent.KEYCODE_UNKNOWN, false, null, true),

    INSERT(KeyEvent.KEYCODE_INSERT, true),
    DELETE(KeyEvent.KEYCODE_FORWARD_DEL, true),
    HOME(KeyEvent.KEYCODE_MOVE_HOME, true),
    END(KeyEvent.KEYCODE_MOVE_END, true),
    PAGE_UP(KeyEvent.KEYCODE_PAGE_UP, true),
    PAGE_DOWN(KeyEvent.KEYCODE_PAGE_DOWN, true),
    ARROW_UP(KeyEvent.KEYCODE_DPAD_UP, true),
    ARROW_DOWN(KeyEvent.KEYCODE_DPAD_DOWN, true),
    ARROW_LEFT(KeyEvent.KEYCODE_DPAD_LEFT, true),
    ARROW_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, true),

    PRINT_SCREEN(KeyEvent.KEYCODE_SYSRQ),
    SCROLL_LOCK(KeyEvent.KEYCODE_SCROLL_LOCK),
    PAUSE(KeyEvent.KEYCODE_BREAK),
    VOLUME_MUTE(KeyEvent.KEYCODE_VOLUME_MUTE),
    VOLUME_DOWN(KeyEvent.KEYCODE_VOLUME_DOWN),
    VOLUME_UP(KeyEvent.KEYCODE_VOLUME_UP),
    MEDIA_PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
    MEDIA_PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    MEDIA_NEXT(KeyEvent.KEYCODE_MEDIA_NEXT),
    BRIGHTNESS_DOWN(KeyEvent.KEYCODE_BRIGHTNESS_DOWN, false, null, false, false),
    BRIGHTNESS_UP(KeyEvent.KEYCODE_BRIGHTNESS_UP, false, null, false, false);

    public final int androidKeyCode;
    public final boolean repeatable;
    public final ModifierType modifierType;
    public final boolean localOnly;
    public final boolean transportSupported;

    LogicalKey(int androidKeyCode) {
        this(androidKeyCode, false);
    }

    LogicalKey(int androidKeyCode, boolean repeatable) {
        this(androidKeyCode, repeatable, null);
    }

    LogicalKey(int androidKeyCode, boolean repeatable, ModifierType modifierType) {
        this(androidKeyCode, repeatable, modifierType, false);
    }

    LogicalKey(int androidKeyCode, boolean repeatable, ModifierType modifierType,
               boolean localOnly) {
        this(androidKeyCode, repeatable, modifierType, localOnly, true);
    }

    LogicalKey(int androidKeyCode, boolean repeatable, ModifierType modifierType,
               boolean localOnly, boolean transportSupported) {
        this.androidKeyCode = androidKeyCode;
        this.repeatable = repeatable;
        this.modifierType = modifierType;
        this.localOnly = localOnly;
        this.transportSupported = transportSupported;
    }

    public boolean isModifier() {
        return modifierType != null;
    }

    public static LogicalKey fromShortcutToken(String token) {
        if (token == null) {
            return null;
        }

        String normalized = token.trim().toUpperCase(Locale.ROOT)
                .replace("LEFT ", "LEFT_")
                .replace("RIGHT ", "RIGHT_");
        switch (normalized) {
            case "CTRL":
            case "CONTROL":
                return LEFT_CTRL;
            case "SHIFT":
                return LEFT_SHIFT;
            case "ALT":
                return LEFT_ALT;
            case "WIN":
            case "WINDOWS":
            case "META":
                return LEFT_META;
            case "SPACE":
                return SPACE;
            case "ENTER":
                return ENTER;
            case "TAB":
                return TAB;
            case "ESC":
                return ESCAPE;
            default:
                break;
        }

        if (normalized.length() == 1) {
            char value = normalized.charAt(0);
            if (value >= 'A' && value <= 'Z') {
                return valueOf("KEY_" + value);
            }
            if (value >= '0' && value <= '9') {
                return valueOf("NUMBER_" + value);
            }
        }

        try {
            return valueOf(normalized);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
