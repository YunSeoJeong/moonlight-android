package com.limelight.binding.input.virtual_controller.splitkeyboard;

import com.limelight.Game;

/** Resolves Game.instance for every event so the cover controls survive reconnects. */
public final class RemoteMouseTransportAdapter implements RemoteMouseTransport {
    @Override
    public boolean isConnected() {
        Game game = Game.instance;
        return game != null && game.isSplitKeyboardMouseTransportConnected();
    }

    @Override
    public boolean sendMouseButton(byte button, boolean down) {
        Game game = Game.instance;
        return game != null && game.sendSplitKeyboardMouseButton(button, down);
    }

    @Override
    public boolean sendMouseMove(int deltaX, int deltaY) {
        Game game = Game.instance;
        return game != null && game.sendSplitKeyboardMouseMove(deltaX, deltaY);
    }

    @Override
    public boolean sendScroll(int verticalAmount, int horizontalAmount) {
        Game game = Game.instance;
        return game != null && game.sendSplitKeyboardMouseScroll(
                verticalAmount, horizontalAmount);
    }

    @Override
    public boolean sendTouchEvent(byte eventType, int pointerId, float normalizedX,
                                  float normalizedY) {
        Game game = Game.instance;
        return game != null && game.sendSplitKeyboardTouchEvent(
                eventType, pointerId, normalizedX, normalizedY);
    }
}
