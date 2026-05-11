package com.limelight.utils;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import androidx.window.area.WindowArea;
import androidx.window.area.WindowAreaCapability;
import androidx.window.area.WindowAreaController;
import androidx.window.area.WindowAreaPresentationSessionCallback;
import androidx.window.area.WindowAreaSessionPresenter;
import androidx.core.util.Consumer;
import androidx.window.core.ExperimentalWindowApi;
import androidx.window.java.area.WindowAreaControllerCallbackAdapter;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.ui.ExternalControllerView;

import java.util.List;
import java.util.concurrent.Executor;

public final class DualDisplayVirtualGamepadManager {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Executor MAIN_EXECUTOR = command -> MAIN_HANDLER.post(command);
    private static final int WINDOW_AREA_RETRY_DELAY_MS = 500;
    private static final int MAX_WINDOW_AREA_RETRY_COUNT = 5;

    private static WindowAreaControllerCallbackAdapter windowAreaController;
    private static Consumer<List<WindowArea>> windowAreaListener;
    private static WindowAreaSessionPresenter windowAreaSession;
    private static boolean windowAreaPresentationRequested;
    private static int windowAreaGeneration;
    private static int windowAreaRetryCount;
    private static Runnable windowAreaRetryRunnable;

    private DualDisplayVirtualGamepadManager() {
    }

    public static void start(Game game) {
        int generation = ++windowAreaGeneration;
        windowAreaRetryCount = 0;
        cancelWindowAreaRetry();
        LimeLog.info("DualDisplayVirtualGamepadManager.start: gameDisplayId=" +
                getGameDisplayIdSafe(game) + " externalActivityActive=" +
                (ExternalDisplayControlActivity.instance != null) +
                " windowAreaSession=" + (windowAreaSession != null) +
                " presentationRequested=" + windowAreaPresentationRequested +
                " generation=" + generation);
        closeWindowAreaPresentation();
        if (Game.instance != null) {
            Game.instance.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }

        if (tryStartOnSecondaryDisplay(game)) {
            LimeLog.info("DualDisplayVirtualGamepadManager.start: launched sub virtual gamepad on secondary display");
            return;
        }

        ExternalDisplayControlActivity.closeExternalDisplayControl();

        if (!tryStartWindowAreaPresentation(game, generation)) {
            game.runOnUiThread(() ->
                    android.widget.Toast.makeText(game,
                            R.string.dual_screen_virtual_gamepad_unavailable,
                            android.widget.Toast.LENGTH_SHORT).show());
        }
    }

    public static void close() {
        windowAreaGeneration++;
        windowAreaRetryCount = 0;
        cancelWindowAreaRetry();
        LimeLog.info("DualDisplayVirtualGamepadManager.close: externalActivityActive=" +
                (ExternalDisplayControlActivity.instance != null) +
                " windowAreaSession=" + (windowAreaSession != null) +
                " presentationRequested=" + windowAreaPresentationRequested +
                " generation=" + windowAreaGeneration);
        ExternalDisplayControlActivity.closeExternalDisplayControl();
        if (Game.instance != null) {
            Game.instance.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }

        closeWindowAreaPresentation();
    }

    private static void closeWindowAreaPresentation() {
        LimeLog.info("DualDisplayVirtualGamepadManager.closeWindowAreaPresentation: controller=" +
                (windowAreaController != null) + " listener=" + (windowAreaListener != null) +
                " session=" + (windowAreaSession != null));
        if (windowAreaController != null && windowAreaListener != null) {
            try {
                windowAreaController.removeWindowAreasListener(windowAreaListener);
            } catch (Throwable ignored) {
            }
        }

        if (windowAreaSession != null) {
            try {
                windowAreaSession.close();
            } catch (Throwable ignored) {
            }
        }

        windowAreaListener = null;
        windowAreaController = null;
        windowAreaSession = null;
        windowAreaPresentationRequested = false;
    }

    private static boolean tryStartOnSecondaryDisplay(Game game) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartOnSecondaryDisplay: SDK too old");
            return false;
        }

        Display targetDisplay = findSecondaryDisplay(game);
        if (targetDisplay == null) {
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartOnSecondaryDisplay: no secondary display found");
            return false;
        }

        try {
            Intent intent = new Intent(game, ExternalDisplayControlActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplay.getDisplayId());
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartOnSecondaryDisplay: launching displayId=" +
                    targetDisplay.getDisplayId() + " " + getDisplaySummary(targetDisplay));
            game.startActivity(intent, options.toBundle());
            return true;
        } catch (Throwable t) {
            LimeLog.warning("Unable to launch dual-screen virtual gamepad on secondary display: "
                    + t.getMessage());
            return false;
        }
    }

    private static Display findSecondaryDisplay(Game game) {
        DisplayManager displayManager =
                (DisplayManager) game.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            LimeLog.warning("DualDisplayVirtualGamepadManager.findSecondaryDisplay: DisplayManager is null");
            return null;
        }

        int gameDisplayId = getGameDisplayId(game);
        Display[] displays = displayManager.getDisplays();
        LimeLog.info("DualDisplayVirtualGamepadManager.findSecondaryDisplay: gameDisplayId=" +
                gameDisplayId + " displayCount=" + displays.length);
        for (Display display : displays) {
            LimeLog.info("DualDisplayVirtualGamepadManager.findSecondaryDisplay: candidate displayId=" +
                    (display != null ? display.getDisplayId() : -1) + " " +
                    getDisplaySummary(display));
            if (display != null && display.getDisplayId() != gameDisplayId) {
                return display;
            }
        }
        return null;
    }

    private static int getGameDisplayId(Game game) {
        View decorView = game.getWindow().getDecorView();
        Display display = decorView != null ? decorView.getDisplay() : null;
        if (display != null) {
            return display.getDisplayId();
        }
        return game.getWindowManager().getDefaultDisplay().getDisplayId();
    }

    @ExperimentalWindowApi
    private static boolean tryStartWindowAreaPresentation(Game game, int generation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartWindowAreaPresentation: SDK too old");
            return false;
        }

        if (!isCurrentWindowAreaRequest(game, generation)) {
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartWindowAreaPresentation: stale request ignored generation=" +
                    generation + " current=" + windowAreaGeneration);
            return true;
        }

        try {
            windowAreaController =
                    new WindowAreaControllerCallbackAdapter(WindowAreaController.getOrCreate());
            windowAreaPresentationRequested = false;
            windowAreaListener = windowAreas -> onWindowAreasChanged(game, windowAreas, generation);
            windowAreaController.addWindowAreasListener(MAIN_EXECUTOR, windowAreaListener);
            LimeLog.info("DualDisplayVirtualGamepadManager.tryStartWindowAreaPresentation: listener registered generation=" +
                    generation);
            return true;
        } catch (Throwable t) {
            LimeLog.warning("Unable to check WindowArea dual-screen support: " + t.getMessage());
            return false;
        }
    }

    @ExperimentalWindowApi
    private static void onWindowAreasChanged(Game game, List<WindowArea> windowAreas,
                                             int generation) {
        LimeLog.info("DualDisplayVirtualGamepadManager.onWindowAreasChanged: count=" +
                (windowAreas != null ? windowAreas.size() : -1) +
                " presentationRequested=" + windowAreaPresentationRequested +
                " session=" + (windowAreaSession != null) +
                " generation=" + generation +
                " current=" + windowAreaGeneration +
                " retryCount=" + windowAreaRetryCount);
        if (!isCurrentWindowAreaRequest(game, generation)) {
            LimeLog.info("DualDisplayVirtualGamepadManager.onWindowAreasChanged: stale callback ignored");
            return;
        }
        if (windowAreaPresentationRequested || windowAreaSession != null) {
            return;
        }

        WindowArea targetArea = findPresentationArea(windowAreas);
        if (targetArea == null) {
            LimeLog.info("WindowArea dual-screen presentation is not available.");
            scheduleWindowAreaRetry(game, generation, "presentation area unavailable");
            return;
        }

        try {
            windowAreaPresentationRequested = true;
            if (windowAreaController != null && windowAreaListener != null) {
                windowAreaController.removeWindowAreasListener(windowAreaListener);
            }
            windowAreaController.presentContentOnWindowArea(
                    targetArea.getToken(),
                    game,
                    MAIN_EXECUTOR,
                    createPresentationCallback(game, generation));
        } catch (Throwable t) {
            LimeLog.warning("Unable to start WindowArea dual-screen presentation: "
                    + t.getMessage());
            windowAreaPresentationRequested = false;
            scheduleWindowAreaRetry(game, generation, "presentContentOnWindowArea failed");
        }
    }

    private static WindowArea findPresentationArea(List<WindowArea> windowAreas) {
        if (windowAreas == null) {
            return null;
        }

        for (WindowArea windowArea : windowAreas) {
            if (windowArea == null) {
                continue;
            }

            try {
                WindowAreaCapability capability = windowArea.getCapability(
                        WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA);
                WindowAreaCapability.Status status = capability.getStatus();
                if (WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE.equals(status) ||
                        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE.equals(status)) {
                    return windowArea;
                }
            } catch (Throwable t) {
                LimeLog.warning("Unable to read WindowArea presentation capability: "
                        + t.getMessage());
            }
        }

        return null;
    }

    private static WindowAreaPresentationSessionCallback createPresentationCallback(Game game,
                                                                                   int generation) {
        return new WindowAreaPresentationSessionCallback() {
            @Override
            public void onSessionStarted(WindowAreaSessionPresenter session) {
                if (!isCurrentWindowAreaRequest(game, generation)) {
                    LimeLog.info("WindowArea virtual gamepad stale session started; closing generation=" +
                            generation + " current=" + windowAreaGeneration);
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                    }
                    return;
                }

                windowAreaSession = session;
                windowAreaPresentationRequested = false;
                windowAreaRetryCount = 0;
                cancelWindowAreaRetry();
                try {
                    session.setContentView(createSubVirtualGamepadView(session.getContext()));
                    LimeLog.info("WindowArea virtual gamepad presentation started. generation=" +
                            generation);
                } catch (Throwable t) {
                    LimeLog.warning("Unable to attach WindowArea virtual gamepad: "
                            + t.getMessage());
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                    }
                    windowAreaSession = null;
                    scheduleWindowAreaRetry(game, generation, "setContentView failed");
                }
            }

            @Override
            public void onSessionEnded(Throwable t) {
                if (!isCurrentWindowAreaRequest(game, generation)) {
                    LimeLog.info("WindowArea virtual gamepad stale session ended; ignored generation=" +
                            generation + " current=" + windowAreaGeneration);
                    return;
                }

                if (t != null) {
                    LimeLog.warning("WindowArea virtual gamepad presentation ended: "
                            + t.getMessage());
                } else {
                    LimeLog.info("WindowArea virtual gamepad presentation ended.");
                }
                windowAreaSession = null;
                windowAreaPresentationRequested = false;
                if (Game.instance != null) {
                    Game.instance.resetVirtualControllerInputState(
                            VirtualController.DISPLAY_TARGET_SUB);
                }
                scheduleWindowAreaRetry(game, generation, "session ended");
            }

            @Override
            public void onContainerVisibilityChanged(boolean isVisible) {
                if (!isCurrentWindowAreaRequest(game, generation)) {
                    return;
                }
                LimeLog.info("WindowArea virtual gamepad container visibility changed: " +
                        isVisible);
            }
        };
    }

    private static void scheduleWindowAreaRetry(Game game, int generation, String reason) {
        if (!isCurrentWindowAreaRequest(game, generation)) {
            LimeLog.info("DualDisplayVirtualGamepadManager.scheduleWindowAreaRetry: stale request ignored reason=" +
                    reason + " generation=" + generation + " current=" + windowAreaGeneration);
            return;
        }

        if (windowAreaSession != null || windowAreaPresentationRequested) {
            return;
        }

        if (windowAreaRetryRunnable != null) {
            LimeLog.info("WindowArea virtual gamepad retry already scheduled; keeping existing retry. reason=" +
                    reason + " generation=" + generation);
            return;
        }

        if (windowAreaRetryCount >= MAX_WINDOW_AREA_RETRY_COUNT) {
            LimeLog.warning("WindowArea virtual gamepad retry limit reached; giving up. reason=" +
                    reason + " generation=" + generation);
            return;
        }

        int retryNumber = ++windowAreaRetryCount;
        windowAreaRetryRunnable = () -> {
            windowAreaRetryRunnable = null;
            if (!isCurrentWindowAreaRequest(game, generation)) {
                LimeLog.info("WindowArea virtual gamepad retry skipped for stale generation=" +
                        generation + " current=" + windowAreaGeneration);
                return;
            }

            LimeLog.info("Retrying WindowArea virtual gamepad presentation attempt=" +
                    retryNumber + "/" + MAX_WINDOW_AREA_RETRY_COUNT +
                    " reason=" + reason + " generation=" + generation);
            closeWindowAreaPresentation();
            tryStartWindowAreaPresentation(game, generation);
        };
        MAIN_HANDLER.postDelayed(windowAreaRetryRunnable, WINDOW_AREA_RETRY_DELAY_MS);
    }

    private static void cancelWindowAreaRetry() {
        if (windowAreaRetryRunnable != null) {
            MAIN_HANDLER.removeCallbacks(windowAreaRetryRunnable);
            windowAreaRetryRunnable = null;
        }
    }

    private static boolean isCurrentWindowAreaRequest(Game game, int generation) {
        if (generation != windowAreaGeneration || game == null || Game.instance != game ||
                game.isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !game.isDestroyed();
    }

    public static View createSubVirtualGamepadView(Context context) {
        LimeLog.info("DualDisplayVirtualGamepadManager.createSubVirtualGamepadView: hasGame=" +
                (Game.instance != null));
        ExternalControllerView root = new ExternalControllerView(context);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setFocusable(true);

        Game game = Game.instance;
        VirtualController virtualController = new VirtualController(
                game != null ? game.getControllerHandler() : null,
                root,
                null,
                context,
                VirtualController.DISPLAY_TARGET_SUB,
                false,
                game != null ? game.getVirtualControllerInputStateSink() : null);
        virtualController.refreshLayout();
        virtualController.show();
        LimeLog.info("DualDisplayVirtualGamepadManager.createSubVirtualGamepadView: sub elements=" +
                virtualController.getElements().size());
        root.post(() -> LimeLog.info(
                "DualDisplayVirtualGamepadManager.createSubVirtualGamepadView post-layout: root=" +
                        root.getWidth() + "x" + root.getHeight() +
                        " subElements=" + virtualController.getElements().size()));

        return root;
    }

    private static int getGameDisplayIdSafe(Game game) {
        try {
            return getGameDisplayId(game);
        } catch (RuntimeException e) {
            LimeLog.warning("DualDisplayVirtualGamepadManager: unable to query Game display id: " +
                    e.getMessage());
            return -1;
        }
    }

    private static String getDisplaySummary(Display display) {
        if (display == null) {
            return "display=null";
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display.Mode mode = display.getMode();
                return "mode=" + mode.getPhysicalWidth() + "x" +
                        mode.getPhysicalHeight() + "@" + mode.getRefreshRate();
            }
            return "refreshRate=" + display.getRefreshRate();
        } catch (RuntimeException e) {
            return "displaySummaryError=" + e.getMessage();
        }
    }
}
