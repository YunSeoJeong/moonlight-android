package com.limelight.binding.input.virtual_controller.splitkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.Control;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.TrackpadMode;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;

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

    @Test
    public void touchCompatibilityMovesLocallyThenSyncsBeforeMouseButton() {
        session.setCursorBounds(1_000, 500);
        session.setTouchCompatibilityEnabled(true);
        session.pointerDown(owner, 1, Control.RT);

        assertTrue(session.sendTrackpadMove(100, -50));
        assertEquals(600f, session.getCursorX(), 0.01f);
        assertEquals(200f, session.getCursorY(), 0.01f);
        assertTrue(transport.events.isEmpty());

        assertTrue(session.pointerDown(owner, 2, Control.LB));
        int eventsAfterButtonDown = transport.events.size();
        assertTrue(session.sendTrackpadMove(10, 5));
        session.pointerUp(owner, 2);

        assertEquals(MoonBridge.LI_TOUCH_EVENT_DOWN, transport.touchTypes.get(0).byteValue());
        assertEquals(MoonBridge.LI_TOUCH_EVENT_CANCEL, transport.touchTypes.get(1).byteValue());
        assertEquals(2, transport.touchTypes.size());
        assertEquals(0.6f, transport.touchXs.get(0), 0.0001f);
        assertEquals(3, eventsAfterButtonDown);
        assertEquals("B:" + MouseButtonPacket.BUTTON_LEFT + ":true",
                transport.events.get(2));
        assertEquals("B:" + MouseButtonPacket.BUTTON_LEFT + ":false",
                transport.events.get(3));
        assertTrue(transport.events.stream().noneMatch(event -> event.startsWith("M:")));
    }

    @Test
    public void touchCompatibilitySyncsBeforeNativeRightClickAndScroll() {
        session.setCursorBounds(1_000, 500);
        session.setTouchCompatibilityEnabled(true);

        assertTrue(session.pointerDown(owner, 1, Control.LT));
        session.pointerUp(owner, 1);
        assertEquals(2, transport.touchTypes.size());
        assertEquals(MoonBridge.LI_TOUCH_EVENT_DOWN, transport.touchTypes.get(0).byteValue());
        assertEquals(MoonBridge.LI_TOUCH_EVENT_CANCEL, transport.touchTypes.get(1).byteValue());
        assertEquals("B:" + MouseButtonPacket.BUTTON_RIGHT + ":true",
                transport.events.get(2));
        assertEquals("B:" + MouseButtonPacket.BUTTON_RIGHT + ":false",
                transport.events.get(3));

        transport.events.clear();
        transport.touchTypes.clear();
        assertTrue(session.pointerDown(owner, 2, Control.RB));
        assertTrue(session.sendTrackpadScroll(20, -10));
        assertTrue(session.sendTrackpadScroll(5, 2));
        session.pointerUp(owner, 2);

        assertEquals(2, transport.touchTypes.size());
        assertEquals(MoonBridge.LI_TOUCH_EVENT_DOWN, transport.touchTypes.get(0).byteValue());
        assertEquals(MoonBridge.LI_TOUCH_EVENT_CANCEL, transport.touchTypes.get(1).byteValue());
        assertEquals("S:20:-10", transport.events.get(2));
        assertEquals("S:5:2", transport.events.get(3));
    }

    private static final class FakeMouseTransport implements RemoteMouseTransport {
        final List<String> events = new ArrayList<>();
        final List<Byte> touchTypes = new ArrayList<>();
        final List<Float> touchXs = new ArrayList<>();
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

        @Override
        public boolean sendTouchEvent(byte eventType, int pointerId,
                                      float normalizedX, float normalizedY) {
            touchTypes.add(eventType);
            touchXs.add(normalizedX);
            events.add("T:" + eventType + ":" + pointerId + ":"
                    + normalizedX + ":" + normalizedY);
            return connected;
        }
    }
}
