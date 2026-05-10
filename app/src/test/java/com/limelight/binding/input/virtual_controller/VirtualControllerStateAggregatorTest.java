package com.limelight.binding.input.virtual_controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.input.ControllerPacket;

import org.junit.Test;

public class VirtualControllerStateAggregatorTest {
    @Test
    public void mergesMainAndSubStates() {
        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        VirtualControllerStateAggregator aggregator =
                new VirtualControllerStateAggregator(controllerHandler);
        VirtualController.ControllerInputContext main =
                new VirtualController.ControllerInputContext();
        VirtualController.ControllerInputContext sub =
                new VirtualController.ControllerInputContext();

        main.leftStickX = 1234;
        main.leftStickY = -5678;
        aggregator.reportState(VirtualController.DISPLAY_TARGET_MAIN, main);
        clearInvocations(controllerHandler);

        sub.inputMap = ControllerPacket.A_FLAG;
        sub.rightTrigger = (byte) 0xFF;
        aggregator.reportState(VirtualController.DISPLAY_TARGET_SUB, sub);

        verify(controllerHandler).reportOscState(
                eq(ControllerPacket.A_FLAG),
                eq((short) 1234),
                eq((short) -5678),
                eq((short) 0),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0xFF));
    }

    @Test
    public void keepsButtonPressedWhenOnlyOneDisplayReleasesIt() {
        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        VirtualControllerStateAggregator aggregator =
                new VirtualControllerStateAggregator(controllerHandler);
        VirtualController.ControllerInputContext main =
                new VirtualController.ControllerInputContext();
        VirtualController.ControllerInputContext sub =
                new VirtualController.ControllerInputContext();

        main.inputMap = ControllerPacket.A_FLAG;
        sub.inputMap = ControllerPacket.A_FLAG;
        aggregator.reportState(VirtualController.DISPLAY_TARGET_MAIN, main);
        aggregator.reportState(VirtualController.DISPLAY_TARGET_SUB, sub);
        clearInvocations(controllerHandler);

        main.inputMap = 0;
        aggregator.reportState(VirtualController.DISPLAY_TARGET_MAIN, main);

        verify(controllerHandler).reportOscState(
                eq(ControllerPacket.A_FLAG),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0));
    }

    @Test
    public void resetDisplayClearsOnlyThatDisplay() {
        ControllerHandler controllerHandler = mock(ControllerHandler.class);
        VirtualControllerStateAggregator aggregator =
                new VirtualControllerStateAggregator(controllerHandler);
        VirtualController.ControllerInputContext main =
                new VirtualController.ControllerInputContext();
        VirtualController.ControllerInputContext sub =
                new VirtualController.ControllerInputContext();

        main.inputMap = ControllerPacket.A_FLAG;
        sub.inputMap = ControllerPacket.B_FLAG;
        aggregator.reportState(VirtualController.DISPLAY_TARGET_MAIN, main);
        aggregator.reportState(VirtualController.DISPLAY_TARGET_SUB, sub);
        clearInvocations(controllerHandler);

        aggregator.resetDisplay(VirtualController.DISPLAY_TARGET_SUB);

        verify(controllerHandler).reportOscState(
                eq(ControllerPacket.A_FLAG),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((short) 0),
                eq((byte) 0),
                eq((byte) 0));
    }
}
