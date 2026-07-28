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
