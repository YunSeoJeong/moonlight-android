package com.limelight.binding.input.virtual_controller.splitkeyboard;

import com.limelight.Game;

public final class RemoteKeyboardTransportAdapter implements RemoteKeyboardTransport {
    private final Game game;

    public RemoteKeyboardTransportAdapter(Game game) {
        this.game = game;
    }

    @Override
    public boolean isConnected() {
        return game.isVirtualKeyboardTransportConnected();
    }

    @Override
    public void prepareForKeyInput() {
        SubDisplayKeyboardControlsSession.getInstance().prepareForRemoteInput();
    }

    @Override
    public boolean sendKeyDown(LogicalKey key, byte activeModifiers) {
        return game.sendVirtualKeyboardEvent(key.androidKeyCode, true, activeModifiers);
    }

    @Override
    public boolean sendKeyUp(LogicalKey key, byte activeModifiers) {
        return game.sendVirtualKeyboardEvent(key.androidKeyCode, false, activeModifiers);
    }

    @Override
    public void resetModifierState() {
        game.resetVirtualKeyboardModifiers();
    }
}
