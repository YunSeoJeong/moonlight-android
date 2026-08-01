package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.os.Handler;
import android.os.Looper;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KeyboardStateController {
    public interface Listener {
        void onKeyboardStateChanged();
    }

    private static final long HOLD_THRESHOLD_MS = 350;
    private static final long DOUBLE_TAP_THRESHOLD_MS = 300;

    private static final class PointerPress {
        final int pointerId;
        final KeySpec spec;
        final LogicalKey effectiveKey;
        final long downTime;
        final boolean modifierWasLocked;
        final boolean modifierWasOneShot;
        boolean consumesOneShot;
        boolean consumesFnOneShot;

        PointerPress(int pointerId, KeySpec spec, LogicalKey effectiveKey, long downTime,
                     boolean modifierWasLocked) {
            this(pointerId, spec, effectiveKey, downTime, modifierWasLocked, false);
        }

        PointerPress(int pointerId, KeySpec spec, LogicalKey effectiveKey, long downTime,
                     boolean modifierWasLocked, boolean modifierWasOneShot) {
            this.pointerId = pointerId;
            this.spec = spec;
            this.effectiveKey = effectiveKey;
            this.downTime = downTime;
            this.modifierWasLocked = modifierWasLocked;
            this.modifierWasOneShot = modifierWasOneShot;
        }
    }

    private final RemoteKeyboardTransport transport;
    private final SplitKeyboardPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, PointerPress> pointers = new HashMap<>();
    private final Map<Integer, Runnable> repeatRunnables = new HashMap<>();
    private final Map<Integer, LogicalKey> momentaryModifierPointers = new HashMap<>();
    private final Map<LogicalKey, Integer> holdCounts = new EnumMap<>(LogicalKey.class);
    private final Set<LogicalKey> remoteDown = EnumSet.noneOf(LogicalKey.class);
    private final Set<LogicalKey> lockedModifiers = EnumSet.noneOf(LogicalKey.class);
    private final Set<LogicalKey> oneShotModifiers = EnumSet.noneOf(LogicalKey.class);
    private final Map<LogicalKey, Long> oneShotTapTimes = new EnumMap<>(LogicalKey.class);
    private final Set<Integer> fnPointers = new java.util.HashSet<>();

    private Listener listener;
    private boolean fnLocked;
    private boolean fnOneShot;
    private long fnOneShotTapTime;
    private Integer oneShotConsumerPointer;
    private Integer fnOneShotConsumerPointer;

    public KeyboardStateController(RemoteKeyboardTransport transport,
                                   SplitKeyboardPreferences preferences) {
        this.transport = transport;
        this.preferences = preferences;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean pointerDown(int pointerId, KeySpec spec, long eventTime) {
        if (spec == null || pointers.containsKey(pointerId)) {
            return false;
        }

        if (spec.logicalKey == LogicalKey.FN) {
            boolean wasLocked = fnLocked;
            fnPointers.add(pointerId);
            pointers.put(pointerId, new PointerPress(pointerId, spec, LogicalKey.FN,
                    eventTime, wasLocked, fnOneShot));
            notifyStateChanged();
            return true;
        }

        if (!transport.isConnected()) {
            return false;
        }

        LogicalKey effectiveKey = spec.effectiveKey(isFnActive());
        if (effectiveKey.localOnly || !effectiveKey.transportSupported) {
            return false;
        }

        if (effectiveKey == LogicalKey.HANGUL_TOGGLE) {
            PointerPress press = new PointerPress(pointerId, spec,
                    LogicalKey.HANGUL_TOGGLE, eventTime, false);
            if (fnOneShot && fnOneShotConsumerPointer == null) {
                press.consumesFnOneShot = true;
                fnOneShotConsumerPointer = pointerId;
            }
            pointers.put(pointerId, press);
            sendHangulToggle();
            notifyStateChanged();
            return true;
        }

        if (effectiveKey.isModifier()) {
            boolean wasLocked = lockedModifiers.contains(effectiveKey);
            PointerPress press = new PointerPress(pointerId, spec, effectiveKey,
                    eventTime, wasLocked, oneShotModifiers.contains(effectiveKey));
            pointers.put(pointerId, press);
            if (!wasLocked) {
                momentaryModifierPointers.put(pointerId, effectiveKey);
                ensureKeyDown(effectiveKey);
            }
            notifyStateChanged();
            return true;
        }

        PointerPress press = new PointerPress(pointerId, spec, effectiveKey,
                eventTime, false);
        if (!oneShotModifiers.isEmpty() && oneShotConsumerPointer == null) {
            press.consumesOneShot = true;
            oneShotConsumerPointer = pointerId;
        }
        if (fnOneShot && fnOneShotConsumerPointer == null) {
            press.consumesFnOneShot = true;
            fnOneShotConsumerPointer = pointerId;
        }
        pointers.put(pointerId, press);

        int holdCount = holdCounts.containsKey(effectiveKey)
                ? holdCounts.get(effectiveKey) : 0;
        holdCounts.put(effectiveKey, holdCount + 1);
        if (holdCount == 0) {
            if (!ensureKeyDown(effectiveKey)) {
                pointers.remove(pointerId);
                holdCounts.remove(effectiveKey);
                return false;
            }
            if (spec.repeatable && effectiveKey.repeatable) {
                scheduleRepeat(pointerId, effectiveKey);
            }
        }
        notifyStateChanged();
        return true;
    }

    public void pointerUp(int pointerId, long eventTime) {
        PointerPress press = pointers.remove(pointerId);
        cancelRepeat(pointerId);
        if (press == null) {
            return;
        }

        if (press.effectiveKey == LogicalKey.FN) {
            handleFnPointerUp(press, eventTime);
            notifyStateChanged();
            return;
        }

        if (press.effectiveKey == LogicalKey.HANGUL_TOGGLE) {
            if (press.consumesFnOneShot) {
                releaseFnOneShot();
            }
            notifyStateChanged();
            return;
        }

        if (press.effectiveKey.isModifier()) {
            handleModifierPointerUp(press, eventTime);
            notifyStateChanged();
            return;
        }

        int holdCount = holdCounts.containsKey(press.effectiveKey)
                ? holdCounts.get(press.effectiveKey) : 0;
        if (holdCount <= 1) {
            holdCounts.remove(press.effectiveKey);
            ensureKeyUp(press.effectiveKey);
        }
        else {
            holdCounts.put(press.effectiveKey, holdCount - 1);
        }

        if (press.consumesOneShot) {
            releaseOneShotModifiers();
            oneShotConsumerPointer = null;
        }
        if (press.consumesFnOneShot) {
            releaseFnOneShot();
        }
        notifyStateChanged();
    }

    public void accessibilityTap(KeySpec spec) {
        int syntheticPointerId = Integer.MIN_VALUE;
        if (pointerDown(syntheticPointerId, spec, android.os.SystemClock.uptimeMillis())) {
            pointerUp(syntheticPointerId, android.os.SystemClock.uptimeMillis());
        }
    }

    public void releaseAllPressedKeys(ReleaseReason reason) {
        for (Runnable runnable : repeatRunnables.values()) {
            handler.removeCallbacks(runnable);
        }
        repeatRunnables.clear();
        pointers.clear();
        holdCounts.clear();
        momentaryModifierPointers.clear();
        fnPointers.clear();
        fnLocked = false;
        fnOneShot = false;
        fnOneShotTapTime = 0;
        oneShotConsumerPointer = null;
        fnOneShotConsumerPointer = null;
        lockedModifiers.clear();
        oneShotModifiers.clear();
        oneShotTapTimes.clear();

        if (transport.isConnected()) {
            List<LogicalKey> keys = new ArrayList<>(remoteDown);
            for (LogicalKey key : keys) {
                if (!key.isModifier()) {
                    remoteDown.remove(key);
                    transport.sendKeyUp(key, activeModifierMask());
                }
            }
            keys = new ArrayList<>(remoteDown);
            Collections.reverse(keys);
            for (LogicalKey key : keys) {
                remoteDown.remove(key);
                transport.sendKeyUp(key, activeModifierMask());
            }
        }
        remoteDown.clear();
        transport.resetModifierState();
        notifyStateChanged();
    }

    public void releasePressedKeys(Set<KeySpec> specs, ReleaseReason reason) {
        if (specs == null || specs.isEmpty()) {
            return;
        }

        List<Integer> pointerIds = new ArrayList<>();
        for (Map.Entry<Integer, PointerPress> entry : pointers.entrySet()) {
            if (specs.contains(entry.getValue().spec)) {
                pointerIds.add(entry.getKey());
            }
        }
        for (Integer pointerId : pointerIds) {
            cancelPointer(pointerId);
        }
        if (!pointerIds.isEmpty()) {
            notifyStateChanged();
        }
    }

    public boolean isFnActive() {
        return fnLocked || !fnPointers.isEmpty();
    }

    public boolean isShiftActive() {
        return remoteDown.contains(LogicalKey.LEFT_SHIFT)
                || remoteDown.contains(LogicalKey.RIGHT_SHIFT);
    }

    public boolean isLocked(LogicalKey key) {
        return lockedModifiers.contains(key)
                || (key == LogicalKey.FN && fnLocked);
    }

    public boolean isOneShot(LogicalKey key) {
        return oneShotModifiers.contains(key)
                || (key == LogicalKey.FN && fnOneShot);
    }

    public boolean isPointerHolding(KeySpec spec) {
        for (PointerPress press : pointers.values()) {
            if (press.spec == spec) {
                return true;
            }
        }
        return false;
    }

    public boolean isTransportConnected() {
        return transport.isConnected();
    }

    public Set<LogicalKey> getPressedKeysForTesting() {
        return Collections.unmodifiableSet(EnumSet.copyOf(remoteDown));
    }

    private void handleModifierPointerUp(PointerPress press, long eventTime) {
        boolean isShortTap = eventTime - press.downTime < HOLD_THRESHOLD_MS;
        LogicalKey key = press.effectiveKey;

        // Right Alt also hosts the Fn Korean/English key in this layout. Keep
        // this particular Alt key momentary so a tap can never leave Alt locked.
        if (press.spec.logicalKey == LogicalKey.RIGHT_ALT
                && press.spec.fnMappedKey == LogicalKey.HANGUL_TOGGLE) {
            momentaryModifierPointers.remove(press.pointerId);
            lockedModifiers.remove(key);
            oneShotModifiers.remove(key);
            oneShotTapTimes.remove(key);
            if (!hasMomentaryPointerFor(key)) {
                ensureKeyUp(key);
            }
            return;
        }

        if (isShortTap
                && preferences.modifierMode == SplitKeyboardPreferences.ModifierMode.TOGGLE_AND_HOLD) {
            if (press.modifierWasLocked) {
                Long firstTapTime = oneShotTapTimes.get(key);
                boolean promotesToLock = preferences.oneShotModifiers
                        && press.modifierWasOneShot
                        && firstTapTime != null
                        && eventTime - firstTapTime <= DOUBLE_TAP_THRESHOLD_MS;
                if (promotesToLock) {
                    oneShotModifiers.remove(key);
                    oneShotTapTimes.remove(key);
                }
                else {
                    lockedModifiers.remove(key);
                    oneShotModifiers.remove(key);
                    oneShotTapTimes.remove(key);
                    if (!hasMomentaryPointerFor(key)) {
                        ensureKeyUp(key);
                    }
                }
            }
            else {
                momentaryModifierPointers.remove(press.pointerId);
                lockedModifiers.add(key);
                if (preferences.oneShotModifiers) {
                    oneShotModifiers.add(key);
                    oneShotTapTimes.put(key, eventTime);
                }
            }
        }
        else if (!press.modifierWasLocked) {
            momentaryModifierPointers.remove(press.pointerId);
            if (!lockedModifiers.contains(key) && !hasMomentaryPointerFor(key)) {
                ensureKeyUp(key);
            }
        }
    }

    private void handleFnPointerUp(PointerPress press, long eventTime) {
        fnPointers.remove(press.pointerId);
        boolean isShortTap = eventTime - press.downTime < HOLD_THRESHOLD_MS;
        if (isShortTap && preferences.fnTapLockEnabled) {
            if (press.modifierWasLocked) {
                boolean promotesToLock = preferences.oneShotModifiers
                        && press.modifierWasOneShot
                        && eventTime - fnOneShotTapTime <= DOUBLE_TAP_THRESHOLD_MS;
                if (promotesToLock) {
                    fnOneShot = false;
                    fnOneShotTapTime = 0;
                }
                else {
                    fnLocked = false;
                    fnOneShot = false;
                    fnOneShotTapTime = 0;
                }
            }
            else {
                fnLocked = true;
                if (preferences.oneShotModifiers) {
                    fnOneShot = true;
                    fnOneShotTapTime = eventTime;
                }
            }
        }
    }

    private boolean hasMomentaryPointerFor(LogicalKey key) {
        return momentaryModifierPointers.containsValue(key);
    }

    private void cancelPointer(int pointerId) {
        PointerPress press = pointers.remove(pointerId);
        cancelRepeat(pointerId);
        if (press == null) {
            return;
        }

        if (press.effectiveKey == LogicalKey.FN) {
            fnPointers.remove(pointerId);
            return;
        }
        if (press.effectiveKey == LogicalKey.HANGUL_TOGGLE) {
            if (press.consumesFnOneShot) {
                releaseFnOneShot();
            }
            return;
        }
        if (press.effectiveKey.isModifier()) {
            momentaryModifierPointers.remove(pointerId);
            if (!press.modifierWasLocked && !lockedModifiers.contains(press.effectiveKey)
                    && !hasMomentaryPointerFor(press.effectiveKey)) {
                ensureKeyUp(press.effectiveKey);
            }
            return;
        }

        int holdCount = holdCounts.containsKey(press.effectiveKey)
                ? holdCounts.get(press.effectiveKey) : 0;
        if (holdCount <= 1) {
            holdCounts.remove(press.effectiveKey);
            ensureKeyUp(press.effectiveKey);
        }
        else {
            holdCounts.put(press.effectiveKey, holdCount - 1);
        }
        if (press.consumesOneShot) {
            releaseOneShotModifiers();
            oneShotConsumerPointer = null;
        }
        if (press.consumesFnOneShot) {
            releaseFnOneShot();
        }
    }

    private void releaseOneShotModifiers() {
        List<LogicalKey> release = new ArrayList<>(oneShotModifiers);
        oneShotModifiers.clear();
        for (LogicalKey key : release) {
            oneShotTapTimes.remove(key);
            lockedModifiers.remove(key);
            if (!hasMomentaryPointerFor(key)) {
                ensureKeyUp(key);
            }
        }
    }

    private void releaseFnOneShot() {
        fnLocked = false;
        fnOneShot = false;
        fnOneShotTapTime = 0;
        fnOneShotConsumerPointer = null;
    }

    private void scheduleRepeat(int pointerId, LogicalKey key) {
        Runnable repeat = new Runnable() {
            @Override
            public void run() {
                PointerPress current = pointers.get(pointerId);
                if (current == null || current.effectiveKey != key || !remoteDown.contains(key)) {
                    repeatRunnables.remove(pointerId);
                    return;
                }
                if (!transport.isConnected()) {
                    clearLocalStateAfterTransportFailure();
                    return;
                }

                // Moonlight's keyboard packet has KEY_DOWN/KEY_UP only. Repeated
                // input is represented as ordered up/down pairs, followed by the
                // final KEY_UP when the pointer is released.
                if (!transport.sendKeyUp(key, activeModifierMask())
                        || !transport.sendKeyDown(key, activeModifierMask())) {
                    clearLocalStateAfterTransportFailure();
                    return;
                }
                handler.postDelayed(this, preferences.repeatIntervalMs);
            }
        };
        repeatRunnables.put(pointerId, repeat);
        handler.postDelayed(repeat, preferences.repeatDelayMs);
    }

    private void cancelRepeat(int pointerId) {
        Runnable runnable = repeatRunnables.remove(pointerId);
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    private boolean ensureKeyDown(LogicalKey key) {
        if (remoteDown.contains(key)) {
            return true;
        }
        remoteDown.add(key);
        if (!transport.sendKeyDown(key, activeModifierMask())) {
            remoteDown.remove(key);
            clearLocalStateAfterTransportFailure();
            return false;
        }
        return true;
    }

    private boolean ensureKeyUp(LogicalKey key) {
        if (!remoteDown.remove(key)) {
            return true;
        }
        if (!transport.isConnected()) {
            clearLocalStateAfterTransportFailure();
            return false;
        }
        if (!transport.sendKeyUp(key, activeModifierMask())) {
            clearLocalStateAfterTransportFailure();
            return false;
        }
        return true;
    }

    private void sendHangulToggle() {
        List<LogicalKey> chord;
        switch (preferences.hangulKeyMode) {
            case RIGHT_ALT:
                chord = Collections.singletonList(LogicalKey.RIGHT_ALT);
                break;
            case SHIFT_SPACE:
                chord = new ArrayList<>();
                chord.add(LogicalKey.LEFT_SHIFT);
                chord.add(LogicalKey.SPACE);
                break;
            case CUSTOM_SHORTCUT:
                chord = preferences.customHangulShortcut;
                break;
            case NATIVE_HANGUL_KEY:
            default:
                chord = Collections.singletonList(LogicalKey.HANGUL_TOGGLE);
                break;
        }
        sendTapChord(chord);
    }

    private void sendTapChord(List<LogicalKey> chord) {
        List<LogicalKey> added = new ArrayList<>();
        byte modifiers = activeModifierMask();
        for (LogicalKey key : chord) {
            if (remoteDown.contains(key) || key.localOnly || !key.transportSupported) {
                continue;
            }
            modifiers = addModifier(modifiers, key);
            if (!transport.sendKeyDown(key, modifiers)) {
                break;
            }
            added.add(key);
        }

        for (int i = added.size() - 1; i >= 0; i--) {
            LogicalKey key = added.get(i);
            modifiers = removeModifier(modifiers, key);
            transport.sendKeyUp(key, modifiers);
        }
    }

    private byte activeModifierMask() {
        byte mask = 0;
        for (LogicalKey key : remoteDown) {
            mask = addModifier(mask, key);
        }
        return mask;
    }

    private static byte addModifier(byte mask, LogicalKey key) {
        if (key.modifierType == null) {
            return mask;
        }
        switch (key.modifierType) {
            case SHIFT:
                return (byte) (mask | KeyboardPacket.MODIFIER_SHIFT);
            case CTRL:
                return (byte) (mask | KeyboardPacket.MODIFIER_CTRL);
            case ALT:
                return (byte) (mask | KeyboardPacket.MODIFIER_ALT);
            case META:
                return (byte) (mask | KeyboardPacket.MODIFIER_META);
            default:
                return mask;
        }
    }

    private static byte removeModifier(byte mask, LogicalKey key) {
        if (key.modifierType == null) {
            return mask;
        }
        int bit;
        switch (key.modifierType) {
            case SHIFT:
                bit = KeyboardPacket.MODIFIER_SHIFT;
                break;
            case CTRL:
                bit = KeyboardPacket.MODIFIER_CTRL;
                break;
            case ALT:
                bit = KeyboardPacket.MODIFIER_ALT;
                break;
            case META:
                bit = KeyboardPacket.MODIFIER_META;
                break;
            default:
                bit = 0;
        }
        return (byte) (mask & ~bit);
    }

    private void clearLocalStateAfterTransportFailure() {
        for (Runnable runnable : repeatRunnables.values()) {
            handler.removeCallbacks(runnable);
        }
        repeatRunnables.clear();
        pointers.clear();
        holdCounts.clear();
        momentaryModifierPointers.clear();
        lockedModifiers.clear();
        oneShotModifiers.clear();
        oneShotTapTimes.clear();
        remoteDown.clear();
        fnPointers.clear();
        fnLocked = false;
        fnOneShot = false;
        fnOneShotTapTime = 0;
        oneShotConsumerPointer = null;
        fnOneShotConsumerPointer = null;
        transport.resetModifierState();
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        if (listener != null) {
            listener.onKeyboardStateChanged();
        }
    }
}
