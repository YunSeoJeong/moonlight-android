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
    public void layoutContainsExactly68ImageKeys() {
        Map<LogicalKey, KeySpec> specs = specsByKey();

        assertEquals(SplitKeyboardLayout.KEY_COUNT, countKeys());
        assertFalse(specs.containsKey(LogicalKey.INSERT));
        assertFalse(specs.containsKey(LogicalKey.END));
        assertFalse(specs.containsKey(LogicalKey.GRAVE));
        assertTrue(specs.containsKey(LogicalKey.HOME));
        assertTrue(specs.containsKey(LogicalKey.DELETE));
        assertTrue(specs.containsKey(LogicalKey.PAGE_UP));
        assertTrue(specs.containsKey(LogicalKey.PAGE_DOWN));
        assertEquals(LogicalKey.GRAVE, specs.get(LogicalKey.ESCAPE).fnMappedKey);
        assertEquals(LogicalKey.END, specs.get(LogicalKey.HOME).fnMappedKey);
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
