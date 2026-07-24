package com.foldchord.mvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects rear-key chords while keeping TL/TR as persistent controller keys.
 * Event times from MotionEvent share the same uptime clock across both displays.
 */
public final class ChordInputCoordinator {
    public static final long SYSTEM_LAYER_HOLD_MS = 200L;

    private static final long MIN_WINDOW_MS = 55L;
    private static final long MAX_WINDOW_MS = 130L;

    private final Map<Long, Press> activePresses = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    private long collectionWindowMs = 85L;
    private int generation;
    private int fingerMask;
    private int thumbSnapshot;
    private boolean systemLayerSnapshot;
    private boolean cleanStart;
    private long fingerStartedAt;
    private long lastFingerPressedAt;
    private long firstFingerReleasedAt = -1L;

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setCollectionWindowMs(long collectionWindowMs) {
        this.collectionWindowMs = Math.max(MIN_WINDOW_MS,
                Math.min(MAX_WINDOW_MS, collectionWindowMs));
    }

    public long getCollectionWindowMs() {
        return collectionWindowMs;
    }

    public int getCurrentMask() {
        return activeMask();
    }

    public void press(long pointerToken, int bit, long eventTime) {
        if (bit == 0 || activePresses.containsKey(pointerToken)) {
            return;
        }
        bit &= 0xFF;
        if ((bit & ChordMapper.THUMB_MASK) != 0) {
            pressThumb(pointerToken, bit, eventTime);
        } else if ((bit & ChordMapper.FINGER_MASK) != 0) {
            pressFinger(pointerToken, bit, eventTime);
        }
    }

    public void release(long pointerToken) {
        release(pointerToken, Math.max(fingerStartedAt, 0L));
    }

    public void release(long pointerToken, long eventTime) {
        Press press = activePresses.remove(pointerToken);
        if (press == null) {
            return;
        }
        if (press.isThumb()) {
            releaseThumb(press, eventTime);
        } else {
            releaseFinger(press, eventTime);
        }
    }

    public void cancelSource(int sourceId) {
        boolean affectsCurrentFingerChord = false;
        List<Long> tokensToRemove = new ArrayList<>();
        for (Map.Entry<Long, Press> entry : activePresses.entrySet()) {
            Press press = entry.getValue();
            if (press.sourceId == sourceId) {
                tokensToRemove.add(entry.getKey());
                affectsCurrentFingerChord |= !press.isThumb() && press.generation == generation;
            }
        }
        for (Long token : tokensToRemove) {
            activePresses.remove(token);
        }
        if (affectsCurrentFingerChord) {
            cancelCurrentFingerChord();
        }
        notifyChanged();
    }

    public void cancelAll() {
        activePresses.clear();
        cancelCurrentFingerChord();
        notifyChanged();
    }

    public static long pointerToken(int sourceId, int pointerId) {
        return ((long) sourceId << 32) | (pointerId & 0xFFFFFFFFL);
    }

    private void pressThumb(long pointerToken, int bit, long eventTime) {
        Press press = new Press((int) (pointerToken >>> 32), bit, 0, eventTime);
        if (fingerMask != 0 || activeThumbMask() != 0) {
            press.used = true;
            markActiveThumbsUsed();
        }
        activePresses.put(pointerToken, press);
        notifyChanged();
    }

    private void pressFinger(long pointerToken, int bit, long eventTime) {
        boolean rearWasAlreadyDown = hasAnyActiveFinger();
        if (fingerMask == 0) {
            beginFingerChord(eventTime, !rearWasAlreadyDown);
        } else if (eventTime - fingerStartedAt > collectionWindowMs) {
            commitCurrentFingerChord(eventTime);
            beginFingerChord(eventTime, false);
        }

        activePresses.put(pointerToken,
                new Press((int) (pointerToken >>> 32), bit, generation, eventTime));
        fingerMask |= bit;
        lastFingerPressedAt = eventTime;
        markActiveThumbsUsed();
        notifyChanged();
    }

    private void releaseThumb(Press press, long eventTime) {
        if (!press.used) {
            ChordEvent event = ChordEvent.thumbTap(press.bit,
                    Math.max(0L, eventTime - press.pressedAt), eventTime);
            notifyCommitted(event);
        }
        notifyChanged();
    }

    private void releaseFinger(Press press, long eventTime) {
        if (press.generation == generation && fingerMask != 0) {
            if (firstFingerReleasedAt < 0) {
                firstFingerReleasedAt = eventTime;
            }
            if (!hasActiveFingerInGeneration(generation)) {
                commitCurrentFingerChord(eventTime);
            }
        }
        notifyChanged();
    }

    private void beginFingerChord(long eventTime, boolean clean) {
        generation++;
        fingerMask = 0;
        thumbSnapshot = activeThumbMask();
        systemLayerSnapshot = thumbSnapshot == ChordMapper.THUMB_MASK
                && thumbsHeldBefore(eventTime) >= SYSTEM_LAYER_HOLD_MS;
        cleanStart = clean;
        fingerStartedAt = eventTime;
        lastFingerPressedAt = eventTime;
        firstFingerReleasedAt = -1L;
    }

    private void commitCurrentFingerChord(long eventTime) {
        if (fingerMask == 0) {
            return;
        }
        long stableEnd = firstFingerReleasedAt < 0 ? eventTime : firstFingerReleasedAt;
        long stableDuration = Math.max(0L, stableEnd - lastFingerPressedAt);
        long heldDuration = Math.max(0L, stableEnd - fingerStartedAt);
        long arrivalSpan = Math.max(0L, lastFingerPressedAt - fingerStartedAt);
        ChordEvent event = new ChordEvent(thumbSnapshot | fingerMask,
                heldDuration, arrivalSpan, stableDuration, cleanStart,
                systemLayerSnapshot, false, eventTime);
        fingerMask = 0;
        thumbSnapshot = 0;
        systemLayerSnapshot = false;
        firstFingerReleasedAt = -1L;
        notifyCommitted(event);
    }

    private void cancelCurrentFingerChord() {
        fingerMask = 0;
        thumbSnapshot = 0;
        systemLayerSnapshot = false;
        firstFingerReleasedAt = -1L;
    }

    private void notifyCommitted(ChordEvent event) {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onChordCommitted(event);
        }
    }

    private void notifyChanged() {
        int mask = activeMask();
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onChordChanged(mask);
        }
    }

    private int activeMask() {
        int mask = 0;
        for (Press press : activePresses.values()) {
            mask |= press.bit;
        }
        return mask;
    }

    private int activeThumbMask() {
        return activeMask() & ChordMapper.THUMB_MASK;
    }

    private boolean hasAnyActiveFinger() {
        for (Press press : activePresses.values()) {
            if (!press.isThumb()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveFingerInGeneration(int targetGeneration) {
        for (Press press : activePresses.values()) {
            if (!press.isThumb() && press.generation == targetGeneration) {
                return true;
            }
        }
        return false;
    }

    private void markActiveThumbsUsed() {
        for (Press press : activePresses.values()) {
            if (press.isThumb()) {
                press.used = true;
            }
        }
    }

    private long thumbsHeldBefore(long eventTime) {
        long latestThumbDown = Long.MIN_VALUE;
        int mask = 0;
        for (Press press : activePresses.values()) {
            if (press.isThumb()) {
                mask |= press.bit;
                latestThumbDown = Math.max(latestThumbDown, press.pressedAt);
            }
        }
        return mask == ChordMapper.THUMB_MASK
                ? Math.max(0L, eventTime - latestThumbDown)
                : 0L;
    }

    public interface Listener {
        void onChordChanged(int mask);

        default void onChordCommitted(ChordEvent event) {
            onChordCommitted(event.mask, event.heldDurationMs);
        }

        default void onChordCommitted(int mask) {
        }

        default void onChordCommitted(int mask, long heldDurationMs) {
            onChordCommitted(mask);
        }
    }

    /** Timing and layer snapshot captured for one committed rear code or thumb tap. */
    public static final class ChordEvent {
        public final int mask;
        public final long heldDurationMs;
        public final long arrivalSpanMs;
        public final long stableDurationMs;
        public final boolean cleanStart;
        public final boolean systemLayer;
        public final boolean thumbTap;
        public final long endedAtMs;

        public ChordEvent(int mask, long heldDurationMs,
                          long arrivalSpanMs, long stableDurationMs,
                          boolean cleanStart, boolean systemLayer,
                          boolean thumbTap, long endedAtMs) {
            this.mask = mask & 0xFF;
            this.heldDurationMs = Math.max(0L, heldDurationMs);
            this.arrivalSpanMs = Math.max(0L, arrivalSpanMs);
            this.stableDurationMs = Math.max(0L, stableDurationMs);
            this.cleanStart = cleanStart;
            this.systemLayer = systemLayer;
            this.thumbTap = thumbTap;
            this.endedAtMs = endedAtMs;
        }

        public static ChordEvent thumbTap(int bit, long heldDurationMs, long endedAtMs) {
            return new ChordEvent(bit, heldDurationMs, 0L, heldDurationMs,
                    true, false, true, endedAtMs);
        }

        public static ChordEvent cleanCode(int mask, long stableDurationMs,
                                           long endedAtMs) {
            return new ChordEvent(mask, stableDurationMs, 0L,
                    stableDurationMs, true, false, false, endedAtMs);
        }
    }

    private static final class Press {
        final int sourceId;
        final int bit;
        final int generation;
        final long pressedAt;
        boolean used;

        Press(int sourceId, int bit, int generation, long pressedAt) {
            this.sourceId = sourceId;
            this.bit = bit;
            this.generation = generation;
            this.pressedAt = pressedAt;
        }

        boolean isThumb() {
            return (bit & ChordMapper.THUMB_MASK) != 0;
        }
    }
}
