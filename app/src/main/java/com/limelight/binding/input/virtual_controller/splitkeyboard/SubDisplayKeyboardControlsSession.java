package com.limelight.binding.input.virtual_controller.splitkeyboard;

import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;

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
    private static final int CURSOR_SYNC_POINTER_ID = 0x5A000001;
    private static final float TRACKPAD_SCROLL_FACTOR = 5f;
    private static final int MIN_SENSITIVITY_PERCENT = 10;
    private static final int MAX_SENSITIVITY_PERCENT = 300;

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
    private boolean touchCompatibilityEnabled;
    private int cursorWidth;
    private int cursorHeight;
    private float cursorX = 0.5f;
    private float cursorY = 0.5f;
    private boolean scrollCursorSynced;
    private int mouseSensitivityPercent = 100;
    private int compatibilityMouseSensitivityPercent = 100;
    private int scrollSensitivityPercent = 100;
    private float mouseRemainderX;
    private float mouseRemainderY;
    private float scrollRemainderX;
    private float scrollRemainderY;

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

    public void setTouchCompatibilityEnabled(boolean enabled) {
        if (touchCompatibilityEnabled == enabled) {
            return;
        }
        reset();
        touchCompatibilityEnabled = enabled;
        notifyListeners();
    }

    public boolean isTouchCompatibilityEnabled() {
        return touchCompatibilityEnabled;
    }

    public void setInputSensitivities(int mouseSensitivityPercent,
                                      int compatibilityMouseSensitivityPercent,
                                      int scrollSensitivityPercent) {
        this.mouseSensitivityPercent = clamp(mouseSensitivityPercent,
                MIN_SENSITIVITY_PERCENT, MAX_SENSITIVITY_PERCENT);
        this.compatibilityMouseSensitivityPercent = clamp(
                compatibilityMouseSensitivityPercent,
                MIN_SENSITIVITY_PERCENT, MAX_SENSITIVITY_PERCENT);
        this.scrollSensitivityPercent = clamp(scrollSensitivityPercent,
                MIN_SENSITIVITY_PERCENT, MAX_SENSITIVITY_PERCENT);
        resetScaledDeltaRemainders();
    }

    public void setCursorBounds(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        cursorWidth = width;
        cursorHeight = height;
        notifyListeners();
    }

    public float getCursorX() {
        return cursorX * cursorWidth;
    }

    public float getCursorY() {
        return cursorY * cursorHeight;
    }

    public boolean pointerDown(Object owner, int pointerId, Control control) {
        if (owner == null || control == null || findPress(owner, pointerId) != null
                || !transport.isConnected()) {
            return false;
        }

        if (isMouseButton(control) && !isPressed(control)
                && !sendButton(control, true)) {
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
            sendButton(press.control, false);
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
            sendButton(Control.LB, false);
        }
        if (hadRight && !isPressed(Control.LT)) {
            sendButton(Control.LT, false);
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
        scrollCursorSynced = false;
        resetScaledDeltaRemainders();
        if (hadLeft) {
            sendButton(Control.LB, false);
        }
        if (hadRight) {
            sendButton(Control.LT, false);
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
        float sensitivityScale = (touchCompatibilityEnabled
                ? compatibilityMouseSensitivityPercent
                : mouseSensitivityPercent) / 100f;
        if (touchCompatibilityEnabled) {
            if (!hasCursorBounds()) {
                return false;
            }
            cursorX = clamp01(cursorX
                    + deltaX * sensitivityScale / cursorWidth);
            cursorY = clamp01(cursorY
                    + deltaY * sensitivityScale / cursorHeight);
            notifyListeners();
            return true;
        }

        mouseRemainderX += deltaX * sensitivityScale;
        mouseRemainderY += deltaY * sensitivityScale;
        int scaledDeltaX = (int) mouseRemainderX;
        int scaledDeltaY = (int) mouseRemainderY;
        mouseRemainderX -= scaledDeltaX;
        mouseRemainderY -= scaledDeltaY;
        return (scaledDeltaX == 0 && scaledDeltaY == 0)
                || transport.sendMouseMove(scaledDeltaX, scaledDeltaY);
    }

    /** Sends raw pad deltas, mapping rightward swipes to leftward content scrolling. */
    public boolean sendTrackpadScroll(int deltaX, int deltaY) {
        if (trackpadMode != TrackpadMode.SCROLL || !transport.isConnected()) {
            return false;
        }
        float sensitivityScale = scrollSensitivityPercent / 100f;
        scrollRemainderY += deltaY * TRACKPAD_SCROLL_FACTOR * sensitivityScale;
        scrollRemainderX -= deltaX * TRACKPAD_SCROLL_FACTOR * sensitivityScale;
        int verticalAmount = (int) scrollRemainderY;
        int horizontalAmount = (int) scrollRemainderX;
        scrollRemainderY -= verticalAmount;
        scrollRemainderX -= horizontalAmount;
        if (verticalAmount == 0 && horizontalAmount == 0) {
            return true;
        }
        if (touchCompatibilityEnabled) {
            if (!scrollCursorSynced) {
                prepareForRemoteInput();
                scrollCursorSynced = true;
            }
        }
        return transport.sendScroll(verticalAmount, horizontalAmount);
    }

    /** Synchronizes the host cursor before a separate mouse or keyboard packet is sent. */
    public void prepareForRemoteInput() {
        if (touchCompatibilityEnabled && transport.isConnected() && hasCursorBounds()) {
            syncRemoteCursorWithTouch();
        }
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
        TrackpadMode oldMode = trackpadMode;
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
        if (oldMode != trackpadMode) {
            scrollCursorSynced = false;
            resetScaledDeltaRemainders();
        }
    }

    private void resetScaledDeltaRemainders() {
        mouseRemainderX = 0f;
        mouseRemainderY = 0f;
        scrollRemainderX = 0f;
        scrollRemainderY = 0f;
    }

    private boolean sendButton(Control control, boolean down) {
        if (down) {
            prepareForRemoteInput();
        }
        return transport.sendMouseButton(mouseButtonFor(control), down);
    }

    private void syncRemoteCursorWithTouch() {
        if (!sendTouch(MoonBridge.LI_TOUCH_EVENT_DOWN,
                CURSOR_SYNC_POINTER_ID, cursorX, cursorY)) {
            return;
        }
        // Cancel instead of lifting so this touch only updates the absolute
        // cursor position and cannot complete as a tap or drag action.
        sendTouch(MoonBridge.LI_TOUCH_EVENT_CANCEL,
                CURSOR_SYNC_POINTER_ID, 0, 0);
    }

    private boolean sendTouch(byte eventType, int pointerId, float normalizedX,
                              float normalizedY) {
        return transport.sendTouchEvent(eventType, pointerId,
                clamp01(normalizedX), clamp01(normalizedY));
    }

    private boolean hasCursorBounds() {
        return cursorWidth > 0 && cursorHeight > 0;
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
