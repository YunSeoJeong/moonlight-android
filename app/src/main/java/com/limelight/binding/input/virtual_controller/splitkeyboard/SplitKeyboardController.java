package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.ui.StreamContainer;

public final class SplitKeyboardController {
    private final FrameLayout root;
    private final StreamContainer streamContainer;
    private final View backgroundTouchView;
    private final SplitKeyboardPreferences preferences;
    private final RemoteKeyboardLayoutCalculator layoutCalculator =
            new RemoteKeyboardLayoutCalculator();
    private final KeyboardStateController stateController;
    private final SplitKeyboardView keyboardView;
    private final Runnable overlayLayoutChanged;
    private final View.OnLayoutChangeListener layoutChangeListener;
    private final View.OnLayoutChangeListener streamLayoutChangeListener;
    private final float density;

    private boolean destroyed;
    private boolean refreshOverlaysAfterStreamLayout;

    public SplitKeyboardController(Game game,
                                   FrameLayout root,
                                   StreamContainer streamContainer,
                                   View backgroundTouchView,
                                   Runnable overlayLayoutChanged) {
        this.root = root;
        this.streamContainer = streamContainer;
        this.backgroundTouchView = backgroundTouchView;
        this.overlayLayoutChanged = overlayLayoutChanged;
        this.preferences = new SplitKeyboardPreferences(game);
        this.density = game.getResources().getDisplayMetrics().density;
        this.stateController = new KeyboardStateController(
                new RemoteKeyboardTransportAdapter(game), preferences);
        this.keyboardView = new SplitKeyboardView(game);
        this.keyboardView.bind(stateController, preferences);
        this.keyboardView.setVisibility(View.GONE);

        root.addView(keyboardView, new FrameLayout.LayoutParams(1, 1));

        this.layoutChangeListener = (view, left, top, right, bottom,
                                     oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyLayout();
            }
        };
        this.streamLayoutChangeListener = (view, left, top, right, bottom,
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
        stateController.releaseAllPressedKeys(ReleaseReason.NEW_SESSION);
        keyboardView.refreshConnectionState();
    }

    public void onConnectionLost() {
        stateController.releaseAllPressedKeys(ReleaseReason.CONNECTION_LOST);
        keyboardView.refreshConnectionState();
    }

    public void onAppBackgrounded() {
        stateController.releaseAllPressedKeys(ReleaseReason.APP_BACKGROUND);
    }

    public void onScreenGeometryChanged() {
        stateController.releaseAllPressedKeys(ReleaseReason.SCREEN_ROTATION);
        root.post(this::applyLayout);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        stateController.releaseAllPressedKeys(ReleaseReason.VIEW_DETACHED);
        root.removeOnLayoutChangeListener(layoutChangeListener);
        streamContainer.removeOnLayoutChangeListener(streamLayoutChangeListener);
        root.removeView(keyboardView);
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

        if (!bounds.keyboardRect.isEmpty()) {
            applyFrame(keyboardView, bounds.keyboardRect.left, bounds.keyboardRect.top,
                    bounds.keyboardRect.width(), bounds.keyboardRect.height());
            keyboardView.setVisibility(View.VISIBLE);
            keyboardView.bringToFront();
        }

        streamContainer.requestLayout();
        backgroundTouchView.requestLayout();
        root.requestLayout();
    }

    private void refreshCoexistingOverlays() {
        if (destroyed || overlayLayoutChanged == null) {
            return;
        }
        overlayLayoutChanged.run();
        // The keyboard is added after virtual-gamepad elements, so keep its
        // reserved input area above those overlays.
        keyboardView.bringToFront();
    }

    private void syncPointerLayerToStreamBounds() {
        if (destroyed || streamContainer.getWidth() <= 0 || streamContainer.getHeight() <= 0) {
            return;
        }

        // StreamContainer may measure smaller than its reserved 16:9 frame when
        // the negotiated stream uses another aspect ratio. Match the pointer
        // layer to the measured aspect-fit video so letterbox taps cannot be
        // normalized into an unrelated remote coordinate.
        applyFrame(backgroundTouchView,
                streamContainer.getLeft(), streamContainer.getTop(),
                streamContainer.getWidth(), streamContainer.getHeight());
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
