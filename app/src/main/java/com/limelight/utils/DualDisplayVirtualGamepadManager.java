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

    private static WindowAreaControllerCallbackAdapter windowAreaController;
    private static Consumer<List<WindowArea>> windowAreaListener;
    private static WindowAreaSessionPresenter windowAreaSession;
    private static boolean windowAreaPresentationRequested;

    private DualDisplayVirtualGamepadManager() {
    }

    public static void start(Game game) {
        closeWindowAreaPresentation();
        if (Game.instance != null) {
            Game.instance.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }

        if (tryStartOnSecondaryDisplay(game)) {
            return;
        }

        ExternalDisplayControlActivity.closeExternalDisplayControl();

        if (!tryStartWindowAreaPresentation(game)) {
            game.runOnUiThread(() ->
                    android.widget.Toast.makeText(game,
                            R.string.dual_screen_virtual_gamepad_unavailable,
                            android.widget.Toast.LENGTH_SHORT).show());
        }
    }

    public static void close() {
        ExternalDisplayControlActivity.closeExternalDisplayControl();
        if (Game.instance != null) {
            Game.instance.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }

        closeWindowAreaPresentation();
    }

    private static void closeWindowAreaPresentation() {
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
            return false;
        }

        Display targetDisplay = findSecondaryDisplay(game);
        if (targetDisplay == null) {
            return false;
        }

        try {
            Intent intent = new Intent(game, ExternalDisplayControlActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplay.getDisplayId());
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
            return null;
        }

        int gameDisplayId = getGameDisplayId(game);
        for (Display display : displayManager.getDisplays()) {
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
    private static boolean tryStartWindowAreaPresentation(Game game) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }

        try {
            windowAreaController =
                    new WindowAreaControllerCallbackAdapter(WindowAreaController.getOrCreate());
            windowAreaPresentationRequested = false;
            windowAreaListener = windowAreas -> onWindowAreasChanged(game, windowAreas);
            windowAreaController.addWindowAreasListener(MAIN_EXECUTOR, windowAreaListener);
            return true;
        } catch (Throwable t) {
            LimeLog.warning("Unable to check WindowArea dual-screen support: " + t.getMessage());
            return false;
        }
    }

    @ExperimentalWindowApi
    private static void onWindowAreasChanged(Game game, List<WindowArea> windowAreas) {
        if (windowAreaPresentationRequested || windowAreaSession != null) {
            return;
        }

        WindowArea targetArea = findPresentationArea(windowAreas);
        if (targetArea == null) {
            LimeLog.info("WindowArea dual-screen presentation is not available.");
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
                    createPresentationCallback());
        } catch (Throwable t) {
            LimeLog.warning("Unable to start WindowArea dual-screen presentation: "
                    + t.getMessage());
            close();
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

    private static WindowAreaPresentationSessionCallback createPresentationCallback() {
        return new WindowAreaPresentationSessionCallback() {
            @Override
            public void onSessionStarted(WindowAreaSessionPresenter session) {
                windowAreaSession = session;
                try {
                    session.setContentView(createSubVirtualGamepadView(session.getContext()));
                    LimeLog.info("WindowArea virtual gamepad presentation started.");
                } catch (Throwable t) {
                    LimeLog.warning("Unable to attach WindowArea virtual gamepad: "
                            + t.getMessage());
                    close();
                }
            }

            @Override
            public void onSessionEnded(Throwable t) {
                if (t != null) {
                    LimeLog.warning("WindowArea virtual gamepad presentation ended: "
                            + t.getMessage());
                }
                windowAreaSession = null;
                windowAreaPresentationRequested = false;
                if (Game.instance != null) {
                    Game.instance.resetVirtualControllerInputState(
                            VirtualController.DISPLAY_TARGET_SUB);
                }
            }

            @Override
            public void onContainerVisibilityChanged(boolean isVisible) {
            }
        };
    }

    public static View createSubVirtualGamepadView(Context context) {
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

        return root;
    }
}
