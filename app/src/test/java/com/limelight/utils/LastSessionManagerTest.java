package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.Game;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = 33)
@RunWith(RobolectricTestRunner.class)
public class LastSessionManagerTest {
    private Context context;
    private SharedPreferences sessionPreferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        sessionPreferences = context.getSharedPreferences(
                LastSessionManager.PREFS_NAME, Context.MODE_PRIVATE);
        sessionPreferences.edit().clear().commit();
    }

    @Test
    public void buildReconnectIntentRestoresMouseState() {
        sessionPreferences.edit()
                .putString(LastSessionManager.KEY_HOST, "192.0.2.1")
                .putInt(LastSessionManager.KEY_MOUSE_MODE, 3)
                .putBoolean(LastSessionManager.KEY_MOUSE_CURSOR_VISIBLE, true)
                .commit();

        Intent intent = LastSessionManager.buildReconnectIntent(context);

        assertTrue(intent.hasExtra(Game.EXTRA_MOUSE_MODE));
        assertEquals(3, intent.getIntExtra(Game.EXTRA_MOUSE_MODE, -1));
        assertTrue(intent.hasExtra(Game.EXTRA_MOUSE_CURSOR_VISIBLE));
        assertTrue(intent.getBooleanExtra(Game.EXTRA_MOUSE_CURSOR_VISIBLE, false));
    }

    @Test
    public void buildReconnectIntentOmitsMouseStateWhenNotSaved() {
        sessionPreferences.edit()
                .putString(LastSessionManager.KEY_HOST, "192.0.2.1")
                .commit();

        Intent intent = LastSessionManager.buildReconnectIntent(context);

        assertFalse(intent.hasExtra(Game.EXTRA_MOUSE_MODE));
        assertFalse(intent.hasExtra(Game.EXTRA_MOUSE_CURSOR_VISIBLE));
    }
}
