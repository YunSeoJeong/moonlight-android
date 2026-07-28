package com.limelight.binding.input.virtual_controller.splitkeyboard;

public interface RemoteKeyboardTransport {
    boolean isConnected();

    boolean sendKeyDown(LogicalKey key, byte activeModifiers);

    boolean sendKeyUp(LogicalKey key, byte activeModifiers);

    void resetModifierState();
}
