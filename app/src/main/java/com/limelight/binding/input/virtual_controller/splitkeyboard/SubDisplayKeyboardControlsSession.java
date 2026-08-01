package com.limelight.binding.input.virtual_controller.splitkeyboard;

import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Process-wide state shared by the split keyboard and its controls on another display.
 * The remote mouse transport resolves the current Game instance for each event, which
 * prevents a surviving secondary-display activity from retaining a stale connection.
 */
public final class SubDisplayKeyboardControlsSession {
    public enum Control {
        LB,
        LT,
        RB,
        RT
    }

    public enum TrackpadMode {
        NONE,
        MOUSE,
        SCROLL
    }

    public interface Listener {
        void onSubDisplayKeyboardControlsChanged();
    }

    private static final class Press {
        final Object owner;
        final int pointerId;
        final Control control;
        final long order;

        Press(Object owner, int pointerId, Control control, long order) {
            this.owner = owner;
            this.pointerId = pointerId;
            this.control = control;
            this.order = order;
        }
    }

    private static final SubDisplayKeyboardControlsSession INSTANCE =
            new SubDisplayKeyboardControlsSession(new RemoteMouseTransportAdapter());

    private final RemoteMouseTransport transport;
    private final List<Press> presses = new ArrayList<>();
    private final Set<Listener> listeners = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private long nextOrder;
    private TrackpadMode trackpadMode = TrackpadMode.NONE;

    public static SubDisplayKeyboardControlsSession getInstance() {
        return INSTANCE;
    }

    SubDisplayKeyboardControlsSession(RemoteMouseTransport transport) {
        this.transport = transport;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean pointerDown(Object owner, int pointerId, Control control) {
        if (owner == null || control == null || findPress(owner, pointerId) != null
                || !transport.isConnected()) {
            return false;
        }

        if (isMouseButton(control) && !isPressed(control)
                && !transport.sendMouseButton(mouseButtonFor(control), true)) {
            return false;
        }

        presses.add(new Press(owner, pointerId, control, ++nextOrder));
        updateTrackpadMode();
        notifyListeners();
        return true;
    }

    public void pointerUp(Object owner, int pointerId) {
        Press press = findPress(owner, pointerId);
        if (press == null) {
            return;
        }

        presses.remove(press);
        if (isMouseButton(press.control) && !isPressed(press.control)) {
            transport.sendMouseButton(mouseButtonFor(press.control), false);
        }
        updateTrackpadMode();
        notifyListeners();
    }

    public void releaseOwner(Object owner) {
        boolean changed = false;
        boolean hadLeft = isPressed(Control.LB);
        boolean hadRight = isPressed(Control.LT);
        for (int i = presses.size() - 1; i >= 0; i--) {
            if (presses.get(i).owner == owner) {
                presses.remove(i);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        if (hadLeft && !isPressed(Control.LB)) {
            transport.sendMouseButton(MouseButtonPacket.BUTTON_LEFT, false);
        }
        if (hadRight && !isPressed(Control.LT)) {
            transport.sendMouseButton(MouseButtonPacket.BUTTON_RIGHT, false);
        }
        updateTrackpadMode();
        notifyListeners();
    }

    /** Clears pressed buttons and pad mode without tearing down the display surface. */
    public void reset() {
        boolean hadLeft = isPressed(Control.LB);
        boolean hadRight = isPressed(Control.LT);
        boolean changed = !presses.isEmpty() || trackpadMode != TrackpadMode.NONE;
        presses.clear();
        trackpadMode = TrackpadMode.NONE;
        if (hadLeft) {
            transport.sendMouseButton(MouseButtonPacket.BUTTON_LEFT, false);
        }
        if (hadRight) {
            transport.sendMouseButton(MouseButtonPacket.BUTTON_RIGHT, false);
        }
        if (changed) {
            notifyListeners();
        }
    }

    public boolean isPressed(Control control) {
        for (Press press : presses) {
            if (press.control == control) {
                return true;
            }
        }
        return false;
    }

    public TrackpadMode getTrackpadMode() {
        return trackpadMode;
    }

    public boolean sendTrackpadMove(int deltaX, int deltaY) {
        if (trackpadMode != TrackpadMode.MOUSE || !transport.isConnected()) {
            return false;
        }
        return transport.sendMouseMove(deltaX, deltaY);
    }

    public boolean sendTrackpadScroll(int verticalAmount, int horizontalAmount) {
        if (trackpadMode != TrackpadMode.SCROLL || !transport.isConnected()) {
            return false;
        }
        return transport.sendScroll(verticalAmount, horizontalAmount);
    }

    private Press findPress(Object owner, int pointerId) {
        for (Press press : presses) {
            if (press.owner == owner && press.pointerId == pointerId) {
                return press;
            }
        }
        return null;
    }

    private void updateTrackpadMode() {
        Press newestTrigger = null;
        for (Press press : presses) {
            if ((press.control == Control.RB || press.control == Control.RT)
                    && (newestTrigger == null || press.order > newestTrigger.order)) {
                newestTrigger = press;
            }
        }
        trackpadMode = newestTrigger == null ? TrackpadMode.NONE
                : newestTrigger.control == Control.RT
                ? TrackpadMode.MOUSE : TrackpadMode.SCROLL;
    }

    private static boolean isMouseButton(Control control) {
        return control == Control.LB || control == Control.LT;
    }

    private static byte mouseButtonFor(Control control) {
        return control == Control.LB
                ? MouseButtonPacket.BUTTON_LEFT : MouseButtonPacket.BUTTON_RIGHT;
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onSubDisplayKeyboardControlsChanged();
        }
    }
}
