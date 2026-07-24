package com.limelight.profiles;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;
import com.limelight.binding.input.virtual_controller.WebGamepadLayoutLoader;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Config(sdk = {33}, shadows = {
        com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class
})
@RunWith(RobolectricTestRunner.class)
public class ProfileVirtualGamepadSelectionTest {
    private static final String LAYOUT_JSON =
            "{\"resolutions\":[{\"width\":100,\"height\":100,\"components\":[]}]}";

    private Context context;
    private ProfilesManager profilesManager;
    private String firstLayoutId;
    private String secondLayoutId;

    @BeforeClass
    public static void suppressLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        ProfilesManager.instance = null;
        deleteRecursively(new File(context.getFilesDir(), "profiles"));
        profilesManager = ProfilesManager.getInstance();
        profilesManager.load(context);

        firstLayoutId = WebGamepadLayoutLoader
                .saveImportedLayout(context, LAYOUT_JSON, "profile-pad-one")
                .id;
        secondLayoutId = WebGamepadLayoutLoader
                .saveImportedLayout(context, LAYOUT_JSON, "profile-pad-two")
                .id;
    }

    @After
    public void tearDown() {
        profilesManager.setActive(null);
        WebGamepadLayoutLoader.deleteImportedLayout(context, firstLayoutId);
        WebGamepadLayoutLoader.deleteImportedLayout(context, secondLayoutId);
        WebGamepadLayoutLoader.clearActiveLayout(context);
        deleteRecursively(new File(context.getFilesDir(), "profiles"));
        ProfilesManager.instance = null;
    }

    @Test
    public void activeProfileOverridesGlobalVirtualGamepadSelection() {
        Map<String, Object> firstOptions = new HashMap<>();
        firstOptions.put(WebGamepadLayoutLoader.PROFILE_LAYOUT_PREF, firstLayoutId);
        SettingsProfile firstProfile = new SettingsProfile(
                UUID.randomUUID(), "First", 0, 0, firstOptions);

        Map<String, Object> secondOptions = new HashMap<>();
        secondOptions.put(WebGamepadLayoutLoader.PROFILE_LAYOUT_PREF, secondLayoutId);
        SettingsProfile secondProfile = new SettingsProfile(
                UUID.randomUUID(), "Second", 0, 0, secondOptions);

        profilesManager.add(firstProfile);
        profilesManager.add(secondProfile);

        profilesManager.setActive(firstProfile.getUuid());
        assertEquals(firstLayoutId, WebGamepadLayoutLoader.getSelectedLayoutId(context));

        profilesManager.setActive(secondProfile.getUuid());
        assertEquals(secondLayoutId, WebGamepadLayoutLoader.getSelectedLayoutId(context));

        ProfilesManager.instance = null;
        profilesManager = ProfilesManager.getInstance();
        profilesManager.load(context);
        assertEquals(secondLayoutId, WebGamepadLayoutLoader.getSelectedLayoutId(context));
    }

    @Test
    public void profileCanExplicitlySelectBuiltInGamepad() {
        Map<String, Object> options = new HashMap<>();
        options.put(WebGamepadLayoutLoader.PROFILE_LAYOUT_PREF,
                WebGamepadLayoutLoader.BUILT_IN_LAYOUT_ID);
        SettingsProfile profile = new SettingsProfile(
                UUID.randomUUID(), "Built-in", 0, 0, options);
        profilesManager.add(profile);
        profilesManager.setActive(profile.getUuid());

        assertEquals(WebGamepadLayoutLoader.BUILT_IN_LAYOUT_ID,
                WebGamepadLayoutLoader.getSelectedLayoutId(context));
    }

    @Test
    public void noActiveProfileUsesGlobalVirtualGamepadSelection() {
        profilesManager.setActive(null);

        assertEquals(secondLayoutId, WebGamepadLayoutLoader.getSelectedLayoutId(context));
    }

    @Test
    public void importingForProfileDoesNotChangeGlobalSelection() throws Exception {
        WebGamepadLayoutLoader.ImportedLayout profileOnlyLayout =
                WebGamepadLayoutLoader.saveImportedLayout(
                        context, LAYOUT_JSON, "profile-only-pad", false);
        try {
            assertEquals(secondLayoutId,
                    WebGamepadLayoutLoader.getGlobalSelectedLayoutId(context));
        } finally {
            WebGamepadLayoutLoader.deleteImportedLayout(context, profileOnlyLayout.id);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
