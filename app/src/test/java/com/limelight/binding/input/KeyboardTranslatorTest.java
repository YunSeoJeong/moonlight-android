package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {32})
public class KeyboardTranslatorTest {
    @Test
    public void rightModifiersAreRecognizedAndVirtualCtrlRetainsVkIntent() {
        assertEquals(com.limelight.nvstream.input.KeyboardPacket.MODIFIER_CTRL,
                KeyboardTranslator.getModifier((short) 0xA3));
        assertEquals(com.limelight.nvstream.input.KeyboardPacket.MODIFIER_SHIFT,
                KeyboardTranslator.getModifier((short) 0xA1));
        assertEquals(com.limelight.nvstream.input.KeyboardPacket.MODIFIER_ALT,
                KeyboardTranslator.getModifier((short) 0xA5));
        assertEquals(com.limelight.nvstream.input.KeyboardPacket.MODIFIER_META,
                KeyboardTranslator.getModifier((short) 0x5C));
        KeyboardTranslator translator = new KeyboardTranslator(new PreferenceConfiguration());
        assertEquals((short) 0x80A3, translator.translate(KeyEvent.KEYCODE_CTRL_RIGHT, 0, -1));
        assertEquals(com.limelight.nvstream.jni.MoonBridge.SS_KBE_FLAG_NON_NORMALIZED,
                KeyboardTranslator.getVirtualKeyFlags(KeyEvent.KEYCODE_CTRL_RIGHT));
        assertEquals(0, KeyboardTranslator.getVirtualKeyFlags(KeyEvent.KEYCODE_A));
    }

    @Test
    public void deviceVolumeButtonsStayLocalEvenWhenTheyHaveLinuxScanCodes() {
        KeyboardTranslator translator =
                new KeyboardTranslator(new PreferenceConfiguration());

        assertEquals(0, translator.translate(
                KeyEvent.KEYCODE_VOLUME_DOWN, 114, -1));
        assertEquals(0, translator.translate(
                KeyEvent.KEYCODE_VOLUME_UP, 115, -1));
        assertEquals(0, translator.translate(
                KeyEvent.KEYCODE_VOLUME_MUTE, 113, -1));
    }
}
