package com.limelight;

import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class LimeLog {
    private static final Logger LOGGER = Logger.getLogger(LimeLog.class.getName());
    private static final String TAG = "LimeLog";

    private static final int MAX_ENTRIES = 300;
    private static final ArrayDeque<String> logBuffer = new ArrayDeque<>();
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static void append(String level, String msg) {
        String entry = TIME_FMT.format(new Date()) + " [" + level + "] " + msg;
        synchronized (logBuffer) {
            if (logBuffer.size() >= MAX_ENTRIES) {
                logBuffer.pollFirst();
            }
            logBuffer.addLast(entry);
        }
    }

    public static void info(String msg) {
        LOGGER.info(msg);
        Log.i(TAG, msg);
        append("I", msg);
    }

    public static void warning(String msg) {
        LOGGER.warning(msg);
        Log.w(TAG, msg);
        append("W", msg);
    }

    public static void severe(String msg) {
        LOGGER.severe(msg);
        Log.e(TAG, msg);
        append("E", msg);
    }

    public static void setFileHandler(String fileName) throws IOException {
        LOGGER.addHandler(new FileHandler(fileName));
    }

    /** Returns a snapshot of all buffered log entries, oldest first. */
    public static List<String> getRecentLogs() {
        synchronized (logBuffer) {
            return new ArrayList<>(logBuffer);
        }
    }

    /** Clears the in-memory log buffer. */
    public static void clearLogs() {
        synchronized (logBuffer) {
            logBuffer.clear();
        }
    }
}
