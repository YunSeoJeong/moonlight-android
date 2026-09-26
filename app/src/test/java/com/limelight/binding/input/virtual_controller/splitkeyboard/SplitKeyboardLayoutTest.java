package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

public class SplitKeyboardLayoutTest {
    @Test
    public void layoutKeepsOnlyRequestedUtilityKeys() {
        Map<LogicalKey, KeySpec> specs = specsByKey();

        assertEquals(SplitKeyboardLayout.KEY_COUNT, countKeys());
        assertFalse(specs.containsKey(LogicalKey.INSERT));
        assertFalse(specs.containsKey(LogicalKey.END));
        assertFalse(specs.containsKey(LogicalKey.GRAVE));
        assertFalse(specs.containsKey(LogicalKey.HOME));
        assertTrue(specs.containsKey(LogicalKey.DELETE));
        assertFalse(specs.containsKey(LogicalKey.PAGE_UP));
        assertFalse(specs.containsKey(LogicalKey.PAGE_DOWN));
        assertEquals(LogicalKey.GRAVE, specs.get(LogicalKey.ESCAPE).fnMappedKey);
        assertFalse(specs.containsKey(LogicalKey.LEFT_ALT));
        assertFalse(specs.containsKey(LogicalKey.ARROW_UP));
        assertFalse(specs.containsKey(LogicalKey.ARROW_DOWN));
        assertFalse(specs.containsKey(LogicalKey.ARROW_LEFT));
        assertFalse(specs.containsKey(LogicalKey.ARROW_RIGHT));
        assertTrue(specs.containsKey(LogicalKey.NAV));
        assertEquals(LogicalKey.INSERT, specs.get(LogicalKey.DELETE).fnMappedKey);
    }

    @Test
    public void numberAndSymbolKeysOwnFunctionLayer() {
        Map<LogicalKey, KeySpec> specs = specsByKey();
        LogicalKey[] baseKeys = {
                LogicalKey.NUMBER_1, LogicalKey.NUMBER_2, LogicalKey.NUMBER_3,
                LogicalKey.NUMBER_4, LogicalKey.NUMBER_5, LogicalKey.NUMBER_6,
                LogicalKey.NUMBER_7, LogicalKey.NUMBER_8, LogicalKey.NUMBER_9,
                LogicalKey.NUMBER_0, LogicalKey.MINUS, LogicalKey.EQUALS
        };
        LogicalKey[] functionKeys = {
                LogicalKey.F1, LogicalKey.F2, LogicalKey.F3, LogicalKey.F4,
                LogicalKey.F5, LogicalKey.F6, LogicalKey.F7, LogicalKey.F8,
                LogicalKey.F9, LogicalKey.F10, LogicalKey.F11, LogicalKey.F12
        };

        for (int i = 0; i < baseKeys.length; i++) {
            assertEquals(functionKeys[i], specs.get(baseKeys[i]).fnMappedKey);
            assertNull(specs.get(functionKeys[i]));
        }
    }

    @Test
    public void balancedFunctionRowAndBottomRightDelete() {
        java.util.List<KeyboardRowSpec> rows = SplitKeyboardLayout.createRows();
        assertEquals(LogicalKey.F6, rows.get(0).leftKeys.get(6).fnMappedKey);
        assertEquals(LogicalKey.F7, rows.get(0).rightKeys.get(0).fnMappedKey);
        java.util.List<KeySpec> bottom = rows.get(4).rightKeys;
        assertEquals(LogicalKey.DELETE, bottom.get(bottom.size() - 1).logicalKey);
        assertEquals(LogicalKey.SPACE, bottom.get(0).logicalKey);
        assertTrue(rows.get(4).leftKeys.stream().anyMatch(k -> k.logicalKey == LogicalKey.SPACE));
        assertNull(rows.get(4).centerKey);
    }

    @Test
    public void navigationMappingDoesNotChangeNormalLabelsOrFnLayer() {
        Map<LogicalKey, KeySpec> specs = specsByKey();
        LogicalKey[] letters = {LogicalKey.KEY_I, LogicalKey.KEY_J, LogicalKey.KEY_K, LogicalKey.KEY_L};
        LogicalKey[] arrows = {LogicalKey.ARROW_UP, LogicalKey.ARROW_LEFT, LogicalKey.ARROW_DOWN, LogicalKey.ARROW_RIGHT};
        for (int i = 0; i < letters.length; i++) {
            KeySpec spec = specs.get(letters[i]);
            assertEquals(letters[i], spec.effectiveKey(false, false));
            assertEquals(arrows[i], spec.effectiveKey(false, true));
            assertTrue(spec.hangulLabels);
            assertTrue(spec.secondaryLabel != null);
        }
        assertEquals(LogicalKey.F1, specs.get(LogicalKey.NUMBER_1).effectiveKey(true, true));
    }

    private static int countKeys() {
        int count = 0;
        for (KeyboardRowSpec row : SplitKeyboardLayout.createRows()) {
            count += row.leftKeys.size() + row.rightKeys.size() + row.navigationKeys.size();
            if (row.centerKey != null) {
                count++;
            }
        }
        return count;
    }

    private static Map<LogicalKey, KeySpec> specsByKey() {
        Map<LogicalKey, KeySpec> specs = new EnumMap<>(LogicalKey.class);
        for (KeyboardRowSpec row : SplitKeyboardLayout.createRows()) {
            for (KeySpec spec : row.leftKeys) {
                specs.put(spec.logicalKey, spec);
            }
            for (KeySpec spec : row.rightKeys) {
                specs.put(spec.logicalKey, spec);
            }
            for (KeySpec spec : row.navigationKeys) {
                specs.put(spec.logicalKey, spec);
            }
            if (row.centerKey != null) {
                specs.put(row.centerKey.logicalKey, row.centerKey);
            }
        }
        return specs;
    }
}
