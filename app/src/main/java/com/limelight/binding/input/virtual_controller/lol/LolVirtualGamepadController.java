package com.limelight.binding.input.virtual_controller.lol;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.splitkeyboard.RemoteKeyboardLayoutCalculator;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SplitKeyboardCursorView;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession;
import com.limelight.ui.StreamContainer;

/** Hosts the built-in LoL controls in the same reserved area as the split keyboard. */
public final class LolVirtualGamepadController {
    private static final int CONTROL_SURFACE_COLOR = 0xFFE3E4E8;

    private final FrameLayout root;
    private final StreamContainer streamContainer;
    private final View backgroundTouchView;
    private final LolVirtualGamepadPreferences preferences;
    private final RemoteKeyboardLayoutCalculator layoutCalculator =
            new RemoteKeyboardLayoutCalculator();
    private final FrameLayout gamepadView;
    private final SplitKeyboardCursorView cursorView;
    private final VirtualController virtualController;
    private final Runnable overlayLayoutChanged;
    private final View.OnLayoutChangeListener layoutChangeListener;
    private final View.OnLayoutChangeListener streamLayoutChangeListener;
    private final float density;

    private boolean destroyed;
    private boolean refreshOverlaysAfterStreamLayout;

    public LolVirtualGamepadController(Game game,
                                       FrameLayout root,
                                       StreamContainer streamContainer,
                                       View backgroundTouchView,
                                       Runnable overlayLayoutChanged) {
        this.root = root;
        this.streamContainer = streamContainer;
        this.backgroundTouchView = backgroundTouchView;
        this.overlayLayoutChanged = overlayLayoutChanged;
        this.preferences = new LolVirtualGamepadPreferences(game);
        this.density = game.getResources().getDisplayMetrics().density;

        configureCompatibilitySession();

        gamepadView = new FrameLayout(game);
        gamepadView.setBackgroundColor(CONTROL_SURFACE_COLOR);
        gamepadView.setMotionEventSplittingEnabled(true);
        gamepadView.setFocusable(false);
        gamepadView.setFocusableInTouchMode(false);
        root.addView(gamepadView, new FrameLayout.LayoutParams(1, 1));

        cursorView = new SplitKeyboardCursorView(game);
        root.addView(cursorView, new FrameLayout.LayoutParams(1, 1));

        virtualController = new VirtualController(
                game.getControllerHandler(),
                gamepadView,
                null,
                game,
                VirtualController.DISPLAY_TARGET_MAIN,
                false,
                game.getVirtualControllerInputStateSink(),
                LolVirtualGamepadPreferences.LAYOUT_ASSET_PATH,
                false);
        virtualController.refreshLayout();
        virtualController.show();

        layoutChangeListener = (view, left, top, right, bottom,
                                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyLayout();
            }
        };
        streamLayoutChangeListener = (view, left, top, right, bottom,
                                      oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                syncPointerLayerToStreamBounds();
                if (refreshOverlaysAfterStreamLayout) {
                    refreshOverlaysAfterStreamLayout = false;
                    refreshCoexistingOverlays();
                }
            }
        };
        root.addOnLayoutChangeListener(layoutChangeListener);
        streamContainer.addOnLayoutChangeListener(streamLayoutChangeListener);
        root.post(this::applyLayout);
    }

    public void onConnectionStarted() {
        configureCompatibilitySession();
        virtualController.show();
    }

    public void onConnectionLost() {
        SubDisplayKeyboardControlsSession.getInstance().reset();
        resetControllerInput();
    }

    public void onAppBackgrounded() {
        SubDisplayKeyboardControlsSession.getInstance().reset();
        resetControllerInput();
    }

    public void onScreenGeometryChanged() {
        SubDisplayKeyboardControlsSession.getInstance().reset();
        resetControllerInput();
        root.post(this::applyLayout);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        SubDisplayKeyboardControlsSession session =
                SubDisplayKeyboardControlsSession.getInstance();
        session.reset();
        session.setTouchCompatibilityEnabled(false);
        resetControllerInput();
        root.removeOnLayoutChangeListener(layoutChangeListener);
        streamContainer.removeOnLayoutChangeListener(streamLayoutChangeListener);
        virtualController.removeElements();
        root.removeView(gamepadView);
        root.removeView(cursorView);
    }

    private void configureCompatibilitySession() {
        SubDisplayKeyboardControlsSession session =
                SubDisplayKeyboardControlsSession.getInstance();
        session.setInputSensitivities(
                100,
                preferences.compatibilityMouseSensitivityPercent,
                100);
        session.setTouchCompatibilityEnabled(true);
    }

    private void resetControllerInput() {
        virtualController.resetInputState();
    }

    private void applyLayout() {
        if (destroyed || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return;
        }

        int correctionPx = Math.round(
                preferences.keyboardHeightCorrectionDp * density);
        RemoteKeyboardLayoutCalculator.Bounds bounds = layoutCalculator.calculate(
                root.getWidth(), root.getHeight(), true,
                RemoteKeyboardLayoutCalculator.DEFAULT_REMOTE_ASPECT_RATIO,
                correctionPx);

        refreshOverlaysAfterStreamLayout = true;
        applyStreamFrame(bounds.remoteRect.top,
                bounds.remoteRect.width(), bounds.remoteRect.height());
        applyFrame(backgroundTouchView, bounds.remoteRect.left, bounds.remoteRect.top,
                bounds.remoteRect.width(), bounds.remoteRect.height());
        applyFrame(cursorView, bounds.remoteRect.left, bounds.remoteRect.top,
                bounds.remoteRect.width(), bounds.remoteRect.height());

        if (!bounds.keyboardRect.isEmpty()) {
            applyFrame(gamepadView, bounds.keyboardRect.left, bounds.keyboardRect.top,
                    bounds.keyboardRect.width(), bounds.keyboardRect.height());
            gamepadView.setVisibility(View.VISIBLE);
            gamepadView.post(() -> {
                if (!destroyed) {
                    virtualController.refreshLayout();
                    virtualController.show();
                }
            });
            cursorView.bringToFront();
            gamepadView.bringToFront();
        }

        streamContainer.requestLayout();
        backgroundTouchView.requestLayout();
        root.requestLayout();
    }

    private void refreshCoexistingOverlays() {
        if (destroyed) {
            return;
        }
        if (overlayLayoutChanged != null) {
            overlayLayoutChanged.run();
        }
        cursorView.bringToFront();
        gamepadView.bringToFront();
    }

    private void syncPointerLayerToStreamBounds() {
        if (destroyed || streamContainer.getWidth() <= 0 || streamContainer.getHeight() <= 0) {
            return;
        }

        applyFrame(backgroundTouchView,
                streamContainer.getLeft(), streamContainer.getTop(),
                streamContainer.getWidth(), streamContainer.getHeight());
        applyFrame(cursorView,
                streamContainer.getLeft(), streamContainer.getTop(),
                streamContainer.getWidth(), streamContainer.getHeight());
        cursorView.bringToFront();
        gamepadView.bringToFront();
    }

    private void applyFrame(View view, int left, int top, int width, int height) {
        FrameLayout.LayoutParams params;
        ViewGroup.LayoutParams existing = view.getLayoutParams();
        if (existing instanceof FrameLayout.LayoutParams) {
            params = (FrameLayout.LayoutParams) existing;
        }
        else {
            params = new FrameLayout.LayoutParams(width, height);
        }
        params.width = Math.max(0, width);
        params.height = Math.max(0, height);
        params.leftMargin = left;
        params.topMargin = top;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        params.gravity = Gravity.START | Gravity.TOP;
        view.setLayoutParams(params);
    }

    private void applyStreamFrame(int top, int width, int height) {
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) streamContainer.getLayoutParams();
        params.width = Math.max(0, width);
        params.height = Math.max(0, height);
        params.leftMargin = 0;
        params.topMargin = top;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        streamContainer.setLayoutParams(params);
    }
}
