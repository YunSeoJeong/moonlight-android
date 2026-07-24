package com.limelight.binding.input.virtual_controller;

import android.view.KeyEvent;

import com.foldchord.mvp.ChordInputCoordinator;
import com.foldchord.mvp.ChordMapper;
import com.foldchord.mvp.ChordStateMachine;
import com.foldchord.mvp.HangulComposer;
import com.limelight.Game;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide FoldChord state shared by virtual-controller elements on the main and sub displays.
 */
public final class FoldChordSession implements ChordInputCoordinator.Listener {
    private static final AtomicInteger NEXT_SOURCE_ID = new AtomicInteger(1);
    private static final FoldChordSession INSTANCE = new FoldChordSession(new HostOutput());

    private final ChordInputCoordinator coordinator = new ChordInputCoordinator();
    private final ChordStateMachine inputState = new ChordStateMachine();
    private final HangulComposer hangulComposer = new HangulComposer();
    private final Output output;

    private String composingText = "";

    FoldChordSession(Output output) {
        this.output = output;
        coordinator.addListener(this);
    }

    public static long createInputToken() {
        return ChordInputCoordinator.pointerToken(
                NEXT_SOURCE_ID.getAndIncrement(), 0);
    }

    public static void press(long token, int bit, long eventTime) {
        INSTANCE.coordinator.press(token, bit, eventTime);
    }

    public static void release(long token, long eventTime) {
        INSTANCE.coordinator.release(token, eventTime);
    }

    public static void cancel(long token) {
        INSTANCE.coordinator.cancelSource((int) (token >>> 32));
    }

    public static void reset() {
        INSTANCE.resetState();
    }

    public static int parseBinding(String value) {
        String normalized = normalize(value);
        switch (normalized) {
            case "tl":
            case "leftthumb":
            case "thumbleft":
                return ChordMapper.TL;
            case "lr":
            case "leftring":
            case "leftrear":
                return ChordMapper.LR;
            case "lm":
            case "leftmiddle":
                return ChordMapper.LM;
            case "li":
            case "leftindex":
                return ChordMapper.LI;
            case "ri":
            case "rightindex":
                return ChordMapper.RI;
            case "rm":
            case "rightmiddle":
                return ChordMapper.RM;
            case "rr":
            case "rightring":
            case "rightrear":
                return ChordMapper.RR;
            case "tr":
            case "rightthumb":
            case "thumbright":
                return ChordMapper.TR;
            default:
                return parseNumericBinding(normalized);
        }
    }

    @Override
    public void onChordChanged(int mask) {
        // Each layout element owns its pressed rendering. No additional UI is required here.
    }

    @Override
    public void onChordCommitted(ChordInputCoordinator.ChordEvent event) {
        handleResult(inputState.process(event));
    }

    private void handleResult(ChordMapper.Result result) {
        switch (result.action) {
            case INSERT:
                finishHangulComposition();
                output.sendText(result.text);
                break;
            case COMPOSE_HANGUL:
                composeHangul(result.text.charAt(0));
                break;
            case BACKSPACE:
                for (int index = 0; index < result.repeatCount; index++) {
                    backspace();
                }
                break;
            case ENTER:
                finishHangulComposition();
                output.sendKey("ENTER", 0, 1);
                break;
            case SEND_KEY:
                finishHangulComposition();
                output.sendKey(result.text, result.modifiers, result.repeatCount);
                break;
            case TOGGLE_LANGUAGE:
                finishHangulComposition();
                break;
            case STATE_CHANGE:
            case NONE:
            case UNKNOWN:
                break;
        }
    }

    private void composeHangul(char jamo) {
        HangulComposer.Output composition = hangulComposer.input(jamo);
        replaceRemoteComposition(composingText, composition.committed + composition.composing);
        composingText = composition.composing;
    }

    private void backspace() {
        if (!hangulComposer.hasComposition()) {
            output.sendKey("BACKSPACE", 0, 1);
            return;
        }

        HangulComposer.Output composition = hangulComposer.backspace();
        replaceRemoteComposition(composingText, composition.composing);
        composingText = composition.composing;
    }

    private void replaceRemoteComposition(String previous, String replacement) {
        int codePoints = previous.codePointCount(0, previous.length());
        if (codePoints > 0) {
            output.sendKey("BACKSPACE", 0, codePoints);
        }
        if (!replacement.isEmpty()) {
            output.sendText(replacement);
        }
    }

    private void finishHangulComposition() {
        if (hangulComposer.hasComposition()) {
            hangulComposer.flush();
        }
        composingText = "";
    }

    private void resetState() {
        coordinator.cancelAll();
        inputState.resetAll();
        if (inputState.getLanguage() != ChordMapper.Language.ENGLISH) {
            inputState.toggleLanguage();
        }
        hangulComposer.reset();
        composingText = "";
    }

    private static int parseNumericBinding(String normalized) {
        try {
            int value = normalized.startsWith("0x")
                    ? Integer.parseInt(normalized.substring(2), 16)
                    : Integer.parseInt(normalized);
            if (value != 0 && (value & ~0xFF) == 0 && Integer.bitCount(value) == 1) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.US);
    }

    interface Output {
        void sendText(String text);

        void sendKey(String keyName, int modifiers, int repeatCount);
    }

    private static final class HostOutput implements Output {
        @Override
        public void sendText(String text) {
            Game game = Game.instance;
            if (game != null) {
                game.sendFoldChordText(text);
            }
        }

        @Override
        public void sendKey(String keyName, int modifiers, int repeatCount) {
            int keyCode = keyCodeFor(keyName);
            Game game = Game.instance;
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN && game != null) {
                game.sendFoldChordKey(keyCode, remoteModifiers(modifiers), repeatCount);
            }
        }

        private static int keyCodeFor(String keyName) {
            if (keyName != null && keyName.length() == 1) {
                char letter = Character.toUpperCase(keyName.charAt(0));
                if (letter >= 'A' && letter <= 'Z') {
                    return KeyEvent.KEYCODE_A + (letter - 'A');
                }
            }
            if (keyName == null) {
                return KeyEvent.KEYCODE_UNKNOWN;
            }
            switch (keyName) {
                case "BACKSPACE":
                    return KeyEvent.KEYCODE_DEL;
                case "ENTER":
                    return KeyEvent.KEYCODE_ENTER;
                case "DPAD_UP":
                    return KeyEvent.KEYCODE_DPAD_UP;
                case "DPAD_LEFT":
                    return KeyEvent.KEYCODE_DPAD_LEFT;
                case "DPAD_DOWN":
                    return KeyEvent.KEYCODE_DPAD_DOWN;
                case "DPAD_RIGHT":
                    return KeyEvent.KEYCODE_DPAD_RIGHT;
                case "PAGE_UP":
                    return KeyEvent.KEYCODE_PAGE_UP;
                case "PAGE_DOWN":
                    return KeyEvent.KEYCODE_PAGE_DOWN;
                case "MOVE_HOME":
                    return KeyEvent.KEYCODE_MOVE_HOME;
                case "MOVE_END":
                    return KeyEvent.KEYCODE_MOVE_END;
                case "TAB":
                    return KeyEvent.KEYCODE_TAB;
                case "FORWARD_DEL":
                    return KeyEvent.KEYCODE_FORWARD_DEL;
                case "ESCAPE":
                    return KeyEvent.KEYCODE_ESCAPE;
                case "MENU":
                    return KeyEvent.KEYCODE_MENU;
                default:
                    return KeyEvent.KEYCODE_UNKNOWN;
            }
        }

        private static byte remoteModifiers(int modifiers) {
            int state = 0;
            if ((modifiers & ChordMapper.MODIFIER_SHIFT) != 0) {
                state |= KeyboardPacket.MODIFIER_SHIFT;
            }
            if ((modifiers & ChordMapper.MODIFIER_CTRL) != 0) {
                state |= KeyboardPacket.MODIFIER_CTRL;
            }
            if ((modifiers & ChordMapper.MODIFIER_ALT) != 0) {
                state |= KeyboardPacket.MODIFIER_ALT;
            }
            if ((modifiers & ChordMapper.MODIFIER_META) != 0) {
                state |= KeyboardPacket.MODIFIER_META;
            }
            if ((modifiers & ChordMapper.MODIFIER_ALT_GR) != 0) {
                state |= KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT;
            }
            return (byte) state;
        }
    }
}
