package com.limelight.binding.input.virtual_controller.splitkeyboard;

public interface RemoteMouseTransport {
    boolean isConnected();

    boolean sendMouseButton(byte button, boolean down);

    boolean sendMouseMove(int deltaX, int deltaY);

    boolean sendScroll(int verticalAmount, int horizontalAmount);

    boolean sendTouchEvent(byte eventType, int pointerId, float normalizedX,
                           float normalizedY);
}
