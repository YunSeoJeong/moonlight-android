package com.limelight.binding.input.virtual_controller;

import com.limelight.binding.input.ControllerHandler;

public class VirtualControllerStateAggregator implements VirtualController.InputStateSink {
    private final ControllerHandler controllerHandler;
    private final VirtualController.ControllerInputContext mainState =
            new VirtualController.ControllerInputContext();
    private final VirtualController.ControllerInputContext subState =
            new VirtualController.ControllerInputContext();

    public VirtualControllerStateAggregator(ControllerHandler controllerHandler) {
        this.controllerHandler = controllerHandler;
    }

    @Override
    public synchronized void reportState(String displayTarget,
                                         VirtualController.ControllerInputContext inputContext) {
        if (VirtualController.DISPLAY_TARGET_SUB.equals(displayTarget)) {
            copyState(inputContext, subState);
        } else {
            copyState(inputContext, mainState);
        }

        reportMergedState();
    }

    public synchronized void reset() {
        clearState(mainState);
        clearState(subState);
        reportMergedState();
    }

    public synchronized void resetDisplay(String displayTarget) {
        if (VirtualController.DISPLAY_TARGET_SUB.equals(displayTarget)) {
            clearState(subState);
        } else {
            clearState(mainState);
        }

        reportMergedState();
    }

    private void reportMergedState() {
        if (controllerHandler == null) {
            return;
        }

        controllerHandler.reportOscState(
                mainState.inputMap | subState.inputMap,
                maxByMagnitude(mainState.leftStickX, subState.leftStickX),
                maxByMagnitude(mainState.leftStickY, subState.leftStickY),
                maxByMagnitude(mainState.rightStickX, subState.rightStickX),
                maxByMagnitude(mainState.rightStickY, subState.rightStickY),
                maxUnsigned(mainState.leftTrigger, subState.leftTrigger),
                maxUnsigned(mainState.rightTrigger, subState.rightTrigger));
    }

    private static void copyState(VirtualController.ControllerInputContext source,
                                  VirtualController.ControllerInputContext destination) {
        if (source == null) {
            clearState(destination);
            return;
        }

        destination.inputMap = source.inputMap;
        destination.leftTrigger = source.leftTrigger;
        destination.rightTrigger = source.rightTrigger;
        destination.leftStickX = source.leftStickX;
        destination.leftStickY = source.leftStickY;
        destination.rightStickX = source.rightStickX;
        destination.rightStickY = source.rightStickY;
    }

    private static void clearState(VirtualController.ControllerInputContext state) {
        state.inputMap = 0;
        state.leftTrigger = 0;
        state.rightTrigger = 0;
        state.leftStickX = 0;
        state.leftStickY = 0;
        state.rightStickX = 0;
        state.rightStickY = 0;
    }

    private static short maxByMagnitude(short first, short second) {
        return Math.abs(first) >= Math.abs(second) ? first : second;
    }

    private static byte maxUnsigned(byte first, byte second) {
        return (first & 0xFF) >= (second & 0xFF) ? first : second;
    }
}
