package com.limelight.binding.input.virtual_controller.splitkeyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Split adaptation of the compact 68-key layout.
 *
 * <p>The function row is folded into the number/symbol row. Esc also owns
 * grave/tilde, Home owns End, and Delete owns Insert through the Fn layer.</p>
 */
public final class SplitKeyboardLayout {
    public static final int KEY_COUNT = 68;

    private SplitKeyboardLayout() {
    }

    public static List<KeyboardRowSpec> createRows() {
        List<KeyboardRowSpec> rows = new ArrayList<>();

        rows.add(row(1f,
                keys(
                        fnKey(LogicalKey.ESCAPE, "Esc", 4f,
                                LogicalKey.GRAVE, "Escape, Fn grave or tilde"),
                        fnSymbol(LogicalKey.NUMBER_1, "1", "!", LogicalKey.F1),
                        fnSymbol(LogicalKey.NUMBER_2, "2", "@", LogicalKey.F2),
                        fnSymbol(LogicalKey.NUMBER_3, "3", "#", LogicalKey.F3),
                        fnSymbol(LogicalKey.NUMBER_4, "4", "$", LogicalKey.F4),
                        fnSymbol(LogicalKey.NUMBER_5, "5", "%", LogicalKey.F5)
                ),
                keys(
                        fnSymbol(LogicalKey.NUMBER_6, "6", "^", LogicalKey.F6),
                        fnSymbol(LogicalKey.NUMBER_7, "7", "&", LogicalKey.F7),
                        fnSymbol(LogicalKey.NUMBER_8, "8", "*", LogicalKey.F8),
                        fnSymbol(LogicalKey.NUMBER_9, "9", "(", LogicalKey.F9),
                        fnSymbol(LogicalKey.NUMBER_0, "0", ")", LogicalKey.F10),
                        fnSymbol(LogicalKey.MINUS, "-", "_", LogicalKey.F11),
                        fnSymbol(LogicalKey.EQUALS, "=", "+", LogicalKey.F12),
                        key(LogicalKey.BACKSPACE, "Backspace", 2f, "백스페이스"),
                        fnKey(LogicalKey.HOME, "Home", 1f,
                                LogicalKey.END, "Home, Fn End")
                )));

        rows.add(row(1f,
                keys(
                        key(LogicalKey.TAB, "Tab", 3.5f, "탭"),
                        letter(LogicalKey.KEY_Q, "Q", "ㅂ", "ㅃ"),
                        letter(LogicalKey.KEY_W, "W", "ㅈ", "ㅉ"),
                        letter(LogicalKey.KEY_E, "E", "ㄷ", "ㄸ"),
                        letter(LogicalKey.KEY_R, "R", "ㄱ", "ㄲ"),
                        letter(LogicalKey.KEY_T, "T", "ㅅ", "ㅆ")
                ),
                keys(
                        letter(LogicalKey.KEY_Y, "Y", "ㅛ", null),
                        letter(LogicalKey.KEY_U, "U", "ㅕ", null),
                        letter(LogicalKey.KEY_I, "I", "ㅑ", null),
                        letter(LogicalKey.KEY_O, "O", "ㅐ", "ㅒ"),
                        letter(LogicalKey.KEY_P, "P", "ㅔ", "ㅖ"),
                        symbol(LogicalKey.LEFT_BRACKET, "[", "{"),
                        symbol(LogicalKey.RIGHT_BRACKET, "]", "}"),
                        symbol(LogicalKey.BACKSLASH, "\\", "|", 1.5f),
                        fnKey(LogicalKey.DELETE, "Del", 1f,
                                LogicalKey.INSERT, "Delete, Fn Insert")
                )));

        rows.add(row(1f,
                keys(
                        key(LogicalKey.CAPS_LOCK, "Caps Lock", 4f, "Caps Lock"),
                        letter(LogicalKey.KEY_A, "A", "ㅁ", null),
                        letter(LogicalKey.KEY_S, "S", "ㄴ", null),
                        letter(LogicalKey.KEY_D, "D", "ㅇ", null),
                        letter(LogicalKey.KEY_F, "F", "ㄹ", null),
                        letter(LogicalKey.KEY_G, "G", "ㅎ", null)
                ),
                keys(
                        letter(LogicalKey.KEY_H, "H", "ㅗ", null),
                        letter(LogicalKey.KEY_J, "J", "ㅓ", null),
                        letter(LogicalKey.KEY_K, "K", "ㅏ", null),
                        letter(LogicalKey.KEY_L, "L", "ㅣ", null),
                        symbol(LogicalKey.SEMICOLON, ";", ":"),
                        symbol(LogicalKey.APOSTROPHE, "'", "\""),
                        key(LogicalKey.ENTER, "Enter", 2.25f, "엔터"),
                        key(LogicalKey.PAGE_UP, "PgUp", 1f, "Page Up")
                )));

        rows.add(row(1f,
                keys(
                        key(LogicalKey.LEFT_SHIFT, "Shift", 4.5f, "왼쪽 Shift"),
                        letter(LogicalKey.KEY_Z, "Z", "ㅋ", null),
                        letter(LogicalKey.KEY_X, "X", "ㅌ", null),
                        letter(LogicalKey.KEY_C, "C", "ㅊ", null),
                        letter(LogicalKey.KEY_V, "V", "ㅍ", null),
                        letter(LogicalKey.KEY_B, "B", "ㅠ", null)
                ),
                keys(
                        letter(LogicalKey.KEY_N, "N", "ㅜ", null),
                        letter(LogicalKey.KEY_M, "M", "ㅡ", null),
                        symbol(LogicalKey.COMMA, ",", "<"),
                        symbol(LogicalKey.PERIOD, ".", ">"),
                        symbol(LogicalKey.SLASH, "/", "?"),
                        key(LogicalKey.RIGHT_SHIFT, "Shift", 1.75f, "오른쪽 Shift"),
                        key(LogicalKey.ARROW_UP, "↑", 1f, "위쪽 화살표"),
                        key(LogicalKey.PAGE_DOWN, "PgDn", 1f, "Page Down")
                )));

        rows.add(rowWithCenter(1.08f,
                keys(
                        key(LogicalKey.LEFT_CTRL, "Ctrl", 2f, "왼쪽 컨트롤"),
                        key(LogicalKey.LEFT_META, "Win", 2f, "왼쪽 Windows"),
                        key(LogicalKey.LEFT_ALT, "Alt", 2f, "왼쪽 Alt")
                ),
                keys(
                        fnKey(LogicalKey.RIGHT_ALT, "Alt", 1f,
                                LogicalKey.HANGUL_TOGGLE, "오른쪽 Alt, Fn 한영 전환"),
                        key(LogicalKey.FN, "Fn", 1f, "펑션 레이어"),
                        key(LogicalKey.RIGHT_CTRL, "Ctrl", 1f, "오른쪽 컨트롤"),
                        key(LogicalKey.ARROW_LEFT, "←", 1f, "왼쪽 화살표"),
                        key(LogicalKey.ARROW_DOWN, "↓", 1f, "아래쪽 화살표"),
                        key(LogicalKey.ARROW_RIGHT, "→", 1f, "오른쪽 화살표")
                ),
                key(LogicalKey.SPACE, "", 6.25f, "스페이스")));

        if (countKeys(rows) != KEY_COUNT) {
            throw new IllegalStateException("Expected " + KEY_COUNT
                    + " keyboard keys, got " + countKeys(rows));
        }
        return Collections.unmodifiableList(rows);
    }

    public static String labelFor(LogicalKey key) {
        switch (key) {
            case GRAVE:
                return "` ~";
            case INSERT:
                return "Ins";
            case DELETE:
                return "Del";
            case HOME:
                return "Home";
            case END:
                return "End";
            case PAGE_UP:
                return "PgUp";
            case PAGE_DOWN:
                return "PgDn";
            case HANGUL_TOGGLE:
                return "한/영";
            default:
                return key.name();
        }
    }

    private static int countKeys(List<KeyboardRowSpec> rows) {
        int count = 0;
        for (KeyboardRowSpec row : rows) {
            count += row.leftKeys.size();
            count += row.rightKeys.size();
            count += row.navigationKeys.size();
            if (row.centerKey != null) {
                count++;
            }
        }
        return count;
    }

    private static KeyboardRowSpec row(float height, List<KeySpec> left,
                                       List<KeySpec> right) {
        return new KeyboardRowSpec(height, left, right, Collections.emptyList());
    }

    private static KeyboardRowSpec rowWithCenter(float height, List<KeySpec> left,
                                                 List<KeySpec> right,
                                                 KeySpec centerKey) {
        return new KeyboardRowSpec(height, left, right, Collections.emptyList(),
                0, centerKey);
    }

    private static List<KeySpec> keys(KeySpec... specs) {
        return Arrays.asList(specs);
    }

    private static KeySpec fnSymbol(LogicalKey key, String base, String shifted,
                                    LogicalKey fnKey) {
        return new KeySpec(key, base, shifted, null, 1f, true,
                null, fnKey, base + ", Shift " + shifted + ", Fn "
                + fnKey.name(), false);
    }

    private static KeySpec fnKey(LogicalKey key, String label, float width,
                                 LogicalKey fnKey, String accessibility) {
        return new KeySpec(key, label, null, null, width, key.repeatable,
                key.modifierType, fnKey, accessibility, false);
    }

    private static KeySpec letter(LogicalKey key, String english, String hangul,
                                  String shiftedHangul) {
        return new KeySpec(key, english, hangul, shiftedHangul, 1f, true,
                null, null, english + " 또는 한글 " + hangul, true);
    }

    private static KeySpec symbol(LogicalKey key, String base, String shifted) {
        return symbol(key, base, shifted, 1f);
    }

    private static KeySpec symbol(LogicalKey key, String base, String shifted,
                                  float width) {
        return new KeySpec(key, base, shifted, null, width, true,
                null, null, base + " 또는 Shift " + shifted, false);
    }

    private static KeySpec key(LogicalKey key, String label) {
        return key(key, label, 1f, label);
    }

    private static KeySpec key(LogicalKey key, String label, float width,
                               String accessibility) {
        return new KeySpec(key, label, null, null, width, key.repeatable,
                key.modifierType, null, accessibility, false);
    }
}
