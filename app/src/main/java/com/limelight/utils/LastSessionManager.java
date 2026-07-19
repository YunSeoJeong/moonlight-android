package com.limelight.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.NvHTTP;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Manages session persistence for automatic reconnection after the user
 * backgrounds the app. Game.java calls saveLastSession() on backgrounding
 * using the KEY_* constants here; this class handles loading and clearing.
 *
 * Extension guide — to add a new field (e.g. mouse sensitivity):
 *   1. Declare: public static final String KEY_MOUSE_SENSITIVITY = "ui_mouse_sensitivity";
 *   2. In Game.saveLastSession(): ed.putFloat(KEY_MOUSE_SENSITIVITY, prefConfig.mouseSensitivity);
 *   3. In buildReconnectIntent() or a separate getUiState(): p.getFloat(KEY_MOUSE_SENSITIVITY, 1.0f);
 *
 * Namespace prefixes:
 *   "stream_" — connection parameters needed to rebuild the Game Intent
 *   "ui_"     — UI/input state (mouse settings, popup toggles, etc.)
 */
public class LastSessionManager {

    public static final String PREFS_NAME = "LastSession";

    // ── Streaming connection parameters ──────────────────────────────────────
    public static final String KEY_HOST            = "stream_host";
    public static final String KEY_PORT            = "stream_port";
    public static final String KEY_HTTPS_PORT      = "stream_https_port";
    public static final String KEY_APP_NAME        = "stream_app_name";
    public static final String KEY_APP_UUID        = "stream_app_uuid";
    public static final String KEY_APP_ID          = "stream_app_id";
    public static final String KEY_PC_UUID         = "stream_pc_uuid";
    public static final String KEY_PC_NAME         = "stream_pc_name";
    public static final String KEY_APP_HDR         = "stream_app_hdr";
    public static final String KEY_UNIQUE_ID       = "stream_unique_id";
    public static final String KEY_VDISPLAY        = "stream_vdisplay";
    public static final String KEY_DISPLAY_ID      = "stream_display_id";
    public static final String KEY_SERVER_CERT     = "stream_server_cert";      // Base64 DER
    public static final String KEY_SERVER_COMMAND_IDS = "stream_server_command_ids";  // JSON array
    public static final String KEY_SERVER_COMMANDS = "stream_server_commands";  // JSON array

    // ── UI / input state (future extension) ──────────────────────────────────
    public static final String KEY_MOUSE_MODE = "ui_mouse_mode";
    public static final String KEY_MOUSE_CURSOR_VISIBLE = "ui_mouse_cursor_visible";
    // public static final String KEY_MOUSE_SENSITIVITY = "ui_mouse_sensitivity";
    // public static final String KEY_POPUP_VISIBLE     = "ui_popup_visible";

    /**
     * Returns an Intent that relaunches Game.class with the saved session
     * parameters, or null if no session has been saved.
     */
    public static Intent buildReconnectIntent(Context context) {
        SharedPreferences p = prefs(context);
        String host = p.getString(KEY_HOST, null);
        if (host == null) {
            LimeLog.info("LastSessionManager: no saved session found");
            return null;
        }
        LimeLog.info("LastSessionManager: restoring session host=" + host
                + " appId=" + p.getInt(KEY_APP_ID, -1)
                + " appName=" + p.getString(KEY_APP_NAME, "?"));

        Intent intent = new Intent(context, Game.class);
        intent.putExtra(Game.EXTRA_HOST,       host);
        intent.putExtra(Game.EXTRA_PORT,       p.getInt(KEY_PORT,       NvHTTP.DEFAULT_HTTP_PORT));
        intent.putExtra(Game.EXTRA_HTTPS_PORT, p.getInt(KEY_HTTPS_PORT, 0));
        intent.putExtra(Game.EXTRA_APP_NAME,   p.getString(KEY_APP_NAME,  ""));
        intent.putExtra(Game.EXTRA_APP_UUID,   p.getString(KEY_APP_UUID,  ""));
        intent.putExtra(Game.EXTRA_APP_ID,     p.getInt(KEY_APP_ID, StreamConfiguration.INVALID_APP_ID));
        intent.putExtra(Game.EXTRA_PC_UUID,    p.getString(KEY_PC_UUID,   ""));
        intent.putExtra(Game.EXTRA_PC_NAME,    p.getString(KEY_PC_NAME,   ""));
        intent.putExtra(Game.EXTRA_APP_HDR,    p.getBoolean(KEY_APP_HDR,  false));
        intent.putExtra(Game.EXTRA_UNIQUEID,   p.getString(KEY_UNIQUE_ID, ""));
        intent.putExtra(Game.EXTRA_VDISPLAY,   p.getBoolean(KEY_VDISPLAY, false));
        intent.putExtra(Game.EXTRA_DISPLAY_ID, p.getInt(KEY_DISPLAY_ID,   0));

        // UI state is optional so sessions saved by an older app version continue to
        // fall back to the user's normal preferences instead of being overwritten.
        if (p.contains(KEY_MOUSE_MODE)) {
            intent.putExtra(Game.EXTRA_MOUSE_MODE, p.getInt(KEY_MOUSE_MODE, 0));
        }
        if (p.contains(KEY_MOUSE_CURSOR_VISIBLE)) {
            intent.putExtra(Game.EXTRA_MOUSE_CURSOR_VISIBLE,
                    p.getBoolean(KEY_MOUSE_CURSOR_VISIBLE, false));
        }

        String certBase64 = p.getString(KEY_SERVER_CERT, null);
        if (certBase64 != null) {
            intent.putExtra(Game.EXTRA_SERVER_CERT,
                    Base64.decode(certBase64, Base64.DEFAULT));
        }

        String commandIdsJson = p.getString(KEY_SERVER_COMMAND_IDS, null);
        if (commandIdsJson != null) {
            try {
                JSONArray json = new JSONArray(commandIdsJson);
                ArrayList<String> cmdIds = new ArrayList<>();
                for (int i = 0; i < json.length(); i++) cmdIds.add(json.getString(i));
                intent.putStringArrayListExtra(Game.EXTRA_SERVER_COMMAND_IDS, cmdIds);
            } catch (JSONException ignored) {}
        }

        String commandsJson = p.getString(KEY_SERVER_COMMANDS, null);
        if (commandsJson != null) {
            try {
                JSONArray json = new JSONArray(commandsJson);
                ArrayList<String> cmds = new ArrayList<>();
                for (int i = 0; i < json.length(); i++) cmds.add(json.getString(i));
                intent.putStringArrayListExtra(Game.EXTRA_SERVER_COMMANDS, cmds);
            } catch (JSONException ignored) {}
        }

        return intent;
    }

    /** Returns true if a saved session exists. Cheaper than buildReconnectIntent(). */
    public static boolean hasSession(Context context) {
        return prefs(context).getString(KEY_HOST, null) != null;
    }

    /** Removes the saved session. Call on explicit disconnect, quit, or back. */
    public static void clear(Context context) {
        clear(context, "unspecified");
    }

    /** Removes the saved session with a reason for lifecycle diagnostics. */
    public static void clear(Context context, String reason) {
        LimeLog.info("LastSessionManager: session cleared reason=" + reason);
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
