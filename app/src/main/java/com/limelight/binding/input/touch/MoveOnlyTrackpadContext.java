package com.limelight.binding.input.touch;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;

public class MoveOnlyTrackpadContext implements TouchContext {
    private int lastTouchX;
    private int lastTouchY;
    private boolean cancelled;
    private int pointerCount;
    private double pendingDeltaX;
    private double pendingDeltaY;
    private double velocityX;
    private double velocityY;
    private long lastMoveTime;
    private boolean isFlicking;

    private final NvConnection conn;
    private final int actionIndex;
    private final View targetView;
    private final PreferenceConfiguration prefConfig;
    private final Handler handler;

    private static final double ACCELERATION_THRESHOLD = 8.0;
    private static final double FLICK_FRICTION = 0.93;
    private static final double FLICK_THRESHOLD = 0.8;
    private static final int MOMENTUM_FRAME_INTERVAL_MS = 10;
    private static final int FLICK_VELOCITY_DECAY_TIMEOUT_MS = 50;

    public MoveOnlyTrackpadContext(NvConnection conn, int actionIndex, View view, PreferenceConfiguration prefConfig) {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.targetView = view;
        this.prefConfig = prefConfig;
        this.handler = new Handler(Looper.getMainLooper());
    }

    private final Runnable momentumRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFlicking) {
                return;
            }

            pendingDeltaX += velocityX * MOMENTUM_FRAME_INTERVAL_MS;
            pendingDeltaY += velocityY * MOMENTUM_FRAME_INTERVAL_MS;

            short sendDeltaX = (short) pendingDeltaX;
            short sendDeltaY = (short) pendingDeltaY;

            if (sendDeltaX != 0 || sendDeltaY != 0) {
                conn.sendMouseMove(sendDeltaX, sendDeltaY);
                pendingDeltaX -= sendDeltaX;
                pendingDeltaY -= sendDeltaY;
            }

            velocityX *= FLICK_FRICTION;
            velocityY *= FLICK_FRICTION;

            if (Math.sqrt(velocityX * velocityX + velocityY * velocityY) * MOMENTUM_FRAME_INTERVAL_MS < 0.5) {
                isFlicking = false;
            }

            if (isFlicking) {
                handler.postDelayed(this, MOMENTUM_FRAME_INTERVAL_MS);
            }
        }
    };

    @Override
    public int getActionIndex() {
        return actionIndex;
    }

    @Override
    public void setPointerCount(int pointerCount) {
        this.pointerCount = pointerCount;
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger) {
        if (isFlicking) {
            isFlicking = false;
            handler.removeCallbacks(momentumRunnable);
        }

        lastTouchX = eventX;
        lastTouchY = eventY;
        pendingDeltaX = 0;
        pendingDeltaY = 0;
        if (isNewFinger) {
            cancelled = false;
            velocityX = 0;
            velocityY = 0;
            lastMoveTime = eventTime;
        }
        return true;
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        if (cancelled) {
            return true;
        }

        if (actionIndex != 0 || pointerCount != 1 || eventX == lastTouchX && eventY == lastTouchY) {
            lastTouchX = eventX;
            lastTouchY = eventY;
            return true;
        }

        if (targetView.getWidth() == 0 || targetView.getHeight() == 0) {
            lastTouchX = eventX;
            lastTouchY = eventY;
            return true;
        }

        long deltaTime = eventTime - lastMoveTime;
        int rawDeltaX = eventX - lastTouchX;
        int rawDeltaY = eventY - lastTouchY;
        double deltaX;
        double deltaY;
        double magnitude = Math.sqrt(rawDeltaX * rawDeltaX + rawDeltaY * rawDeltaY);
        double precisionMultiplier = prefConfig.trackpadAcceleration ?
                Math.cbrt(magnitude / ACCELERATION_THRESHOLD) : 1.0;

        if (prefConfig.trackpadSwapAxis) {
            deltaX = rawDeltaY;
            deltaY = rawDeltaX;
        } else {
            deltaX = rawDeltaX;
            deltaY = rawDeltaY;
        }

        deltaX *= precisionMultiplier;
        deltaY *= precisionMultiplier;
        deltaX *= prefConfig.trackpadSensitivityX * 0.01f;
        deltaY *= prefConfig.trackpadSensitivityY * 0.01f;

        if (prefConfig.trackpadInertia && deltaTime > 0) {
            double currentVelocityX = deltaX / deltaTime;
            double currentVelocityY = deltaY / deltaTime;
            if (velocityX == 0 && velocityY == 0) {
                velocityX = currentVelocityX;
                velocityY = currentVelocityY;
            } else {
                velocityX = velocityX * 0.8 + currentVelocityX * 0.2;
                velocityY = velocityY * 0.8 + currentVelocityY * 0.2;
            }
        }

        lastMoveTime = eventTime;
        pendingDeltaX += deltaX;
        pendingDeltaY += deltaY;

        short sendDeltaX = (short) pendingDeltaX;
        short sendDeltaY = (short) pendingDeltaY;

        if (sendDeltaX != 0 || sendDeltaY != 0) {
            conn.sendMouseMove(sendDeltaX, sendDeltaY);
            pendingDeltaX -= sendDeltaX;
            pendingDeltaY -= sendDeltaY;
        }

        lastTouchX = eventX;
        lastTouchY = eventY;
        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
        if (cancelled || actionIndex != 0 || !prefConfig.trackpadInertia) {
            return;
        }

        long timeSinceLastMove = eventTime - lastMoveTime;
        if (timeSinceLastMove > 0) {
            double decay = Math.max(0.0, 1.0 - (double) timeSinceLastMove / FLICK_VELOCITY_DECAY_TIMEOUT_MS);
            velocityX *= decay;
            velocityY *= decay;
        }

        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (speed > FLICK_THRESHOLD) {
            isFlicking = true;
            handler.post(momentumRunnable);
        }
    }

    @Override
    public void cancelTouch() {
        cancelled = true;
        isFlicking = false;
        handler.removeCallbacks(momentumRunnable);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
