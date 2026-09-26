package com.limelight;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SplitKeyboardPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class})
public class GameOrientationTest {
    private Game game;
    private PreferenceConfiguration preferences;

    @Before
    public void setUp() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit().clear().commit();
        // No onCreate(): orientation policy must work before starting a network session.
        game = Robolectric.buildActivity(Game.class).get();
        preferences = new PreferenceConfiguration();
        java.lang.reflect.Field field = Game.class.getDeclaredField("prefConfig");
        field.setAccessible(true);
        field.set(game, preferences);
    }

    private void applyOrientation() throws Exception {
        java.lang.reflect.Method method = Game.class.getDeclaredMethod("setPreferredOrientationForActivity");
        method.setAccessible(true);
        method.invoke(game);
    }

    @Test
    public void splitKeyboardStartsInLandscapeEvenWhenAutoOrientationIsOff() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(game).edit()
                .putBoolean(SplitKeyboardPreferences.KEY_VISIBLE, true).commit();
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, game.getRequestedOrientation());
        // A later configuration callback must not relock it to portrait.
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, game.getRequestedOrientation());
    }

    @Test
    public void autoOrientationRemainsSensorDrivenAfterAConfigurationChange() throws Exception {
        preferences.autoOrientation = true;
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, game.getRequestedOrientation());
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, game.getRequestedOrientation());
    }

    @Test
    public void hostRotationOptionStillAllowsInitialSplitKeyboardLandscape() throws Exception {
        preferences.hostResolutionRotation = true;
        PreferenceManager.getDefaultSharedPreferences(game).edit()
                .putBoolean(SplitKeyboardPreferences.KEY_VISIBLE, true).commit();
        game.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, game.getRequestedOrientation());
    }

    @Test
    public void explicitHostRotationIsNotOverridden() throws Exception {
        preferences.hostResolutionRotation = true;
        game.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyOrientation();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, game.getRequestedOrientation());
    }
}
