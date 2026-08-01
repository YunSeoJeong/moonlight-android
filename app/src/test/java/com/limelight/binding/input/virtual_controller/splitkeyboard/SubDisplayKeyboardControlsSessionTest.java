package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.Control;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.TrackpadMode;
import com.limelight.nvstream.input.MouseButtonPacket;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubDisplayKeyboardControlsSessionTest {
    private FakeMouseTransport transport;
    private SubDisplayKeyboardControlsSession session;
    private Object owner;

    @Before
    public void setUp() {
        transport = new FakeMouseTransport();
        session = new SubDisplayKeyboardControlsSession(transport);
        owner = new Object();
    }

    @Test
    public void lbAndLtMapToLeftAndRightMouseButtons() {
        assertTrue(session.pointerDown(owner, 1, Control.LB));
        session.pointerUp(owner, 1);
        assertTrue(session.pointerDown(owner, 2, Control.LT));
        session.pointerUp(owner, 2);

        assertEquals("B:" + MouseButtonPacket.BUTTON_LEFT + ":true", transport.events.get(0));
        assertEquals("B:" + MouseButtonPacket.BUTTON_LEFT + ":false", transport.events.get(1));
        assertEquals("B:" + MouseButtonPacket.BUTTON_RIGHT + ":true", transport.events.get(2));
        assertEquals("B:" + MouseButtonPacket.BUTTON_RIGHT + ":false", transport.events.get(3));
    }

    @Test
    public void latestHeldTriggerSelectsTrackpadModeAndFallsBackOnRelease() {
        assertTrue(session.pointerDown(owner, 1, Control.RB));
        assertEquals(TrackpadMode.SCROLL, session.getTrackpadMode());

        assertTrue(session.pointerDown(owner, 2, Control.RT));
        assertEquals(TrackpadMode.MOUSE, session.getTrackpadMode());

        session.pointerUp(owner, 2);
        assertEquals(TrackpadMode.SCROLL, session.getTrackpadMode());
        session.pointerUp(owner, 1);
        assertEquals(TrackpadMode.NONE, session.getTrackpadMode());
    }

    @Test
    public void trackpadEventsOnlyUseTheActiveMode() {
        session.pointerDown(owner, 1, Control.RT);
        assertTrue(session.sendTrackpadMove(4, -3));
        assertFalse(session.sendTrackpadScroll(5, 2));
        session.pointerUp(owner, 1);

        session.pointerDown(owner, 2, Control.RB);
        assertFalse(session.sendTrackpadMove(1, 1));
        assertTrue(session.sendTrackpadScroll(-10, 15));

        assertEquals("M:4:-3", transport.events.get(0));
        assertEquals("S:-10:15", transport.events.get(1));
    }

    @Test
    public void resetReleasesButtonsAndClearsModeForReconnect() {
        session.pointerDown(owner, 1, Control.LB);
        session.pointerDown(owner, 2, Control.RT);

        session.reset();

        assertEquals(TrackpadMode.NONE, session.getTrackpadMode());
        assertFalse(session.isPressed(Control.LB));
        assertEquals("B:" + MouseButtonPacket.BUTTON_LEFT + ":false",
                transport.events.get(1));
    }

    private static final class FakeMouseTransport implements RemoteMouseTransport {
        final List<String> events = new ArrayList<>();
        boolean connected = true;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean sendMouseButton(byte button, boolean down) {
            events.add("B:" + button + ":" + down);
            return connected;
        }

        @Override
        public boolean sendMouseMove(int deltaX, int deltaY) {
            events.add("M:" + deltaX + ":" + deltaY);
            return connected;
        }

        @Override
        public boolean sendScroll(int verticalAmount, int horizontalAmount) {
            events.add("S:" + verticalAmount + ":" + horizontalAmount);
            return connected;
        }
    }
}
