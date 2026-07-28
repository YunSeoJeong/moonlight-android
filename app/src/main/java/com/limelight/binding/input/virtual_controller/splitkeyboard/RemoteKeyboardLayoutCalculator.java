package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.graphics.Rect;

public final class RemoteKeyboardLayoutCalculator {
    public static final float DEFAULT_REMOTE_ASPECT_RATIO = 16f / 9f;
    private static final float MIN_KEYBOARD_HEIGHT_FRACTION = 0.27f;
    private static final float FALLBACK_KEYBOARD_HEIGHT_FRACTION = 0.38f;
    private static final float MAX_HEIGHT_CORRECTION_FRACTION = 0.02f;

    public static final class Bounds {
        public final Rect remoteRect;
        public final Rect keyboardRect;

        public Bounds(Rect remoteRect, Rect keyboardRect) {
            this.remoteRect = remoteRect;
            this.keyboardRect = keyboardRect;
        }
    }

    public Bounds calculate(int availableWidth, int availableHeight, boolean keyboardVisible) {
        return calculate(availableWidth, availableHeight, keyboardVisible,
                DEFAULT_REMOTE_ASPECT_RATIO, 0);
    }

    public Bounds calculate(int availableWidth, int availableHeight, boolean keyboardVisible,
                            float remoteAspectRatio, int keyboardHeightCorrectionPx) {
        int width = Math.max(0, availableWidth);
        int height = Math.max(0, availableHeight);
        if (width == 0 || height == 0) {
            return new Bounds(new Rect(), new Rect());
        }

        if (!keyboardVisible || remoteAspectRatio <= 0f) {
            return new Bounds(new Rect(0, 0, width, height), new Rect());
        }

        int fullWidthRemoteHeight = Math.round(width / remoteAspectRatio);
        int correctionLimit = Math.round(height * MAX_HEIGHT_CORRECTION_FRACTION);
        int boundedCorrection = clamp(keyboardHeightCorrectionPx,
                -correctionLimit, correctionLimit);

        int remoteHeight;
        int naturalKeyboardHeight = height - fullWidthRemoteHeight;
        if (fullWidthRemoteHeight <= height
                && naturalKeyboardHeight >= Math.round(height * MIN_KEYBOARD_HEIGHT_FRACTION)) {
            remoteHeight = fullWidthRemoteHeight - boundedCorrection;
        }
        else {
            int keyboardHeight = Math.round(height * FALLBACK_KEYBOARD_HEIGHT_FRACTION);
            remoteHeight = height - keyboardHeight - boundedCorrection;
        }

        remoteHeight = clamp(remoteHeight, 1, height);
        int remoteWidth = Math.min(width, Math.round(remoteHeight * remoteAspectRatio));

        // If the correction tried to make the remote area wider than the content bounds,
        // retain exact aspect ratio and use the full-width height.
        if (remoteWidth == width) {
            remoteHeight = Math.min(height, Math.round(width / remoteAspectRatio));
        }

        int remoteLeft = (width - remoteWidth) / 2;
        Rect remote = new Rect(remoteLeft, 0, remoteLeft + remoteWidth, remoteHeight);
        Rect keyboard = remoteHeight < height
                ? new Rect(0, remoteHeight, width, height)
                : new Rect();
        return new Bounds(remote, keyboard);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
