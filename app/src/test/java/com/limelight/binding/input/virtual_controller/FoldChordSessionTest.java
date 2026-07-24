package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;

import com.foldchord.mvp.ChordInputCoordinator;
import com.foldchord.mvp.ChordMapper;

import org.junit.Test;

public class FoldChordSessionTest {
    @Test
    public void parsesNamedAndNumericLayoutBindings() {
        assertEquals(ChordMapper.TL, FoldChordSession.parseBinding("TL"));
        assertEquals(ChordMapper.LM, FoldChordSession.parseBinding("left-middle"));
        assertEquals(ChordMapper.RR, FoldChordSession.parseBinding("RightRing"));
        assertEquals(ChordMapper.TR, FoldChordSession.parseBinding("0x80"));
        assertEquals(0, FoldChordSession.parseBinding("invalid"));
    }

    @Test
    public void englishChordIsForwardedAsUtf8Text() {
        RecordingOutput output = new RecordingOutput();
        FoldChordSession session = new FoldChordSession(output);

        session.onChordCommitted(ChordInputCoordinator.ChordEvent.cleanCode(
                ChordMapper.LR, 70L, 1_000L));

        assertEquals("e", output.text.toString());
    }

    @Test
    public void hangulCompositionReplacesTheLiveRemoteSyllable() {
        RecordingOutput output = new RecordingOutput();
        FoldChordSession session = new FoldChordSession(output);

        session.onChordCommitted(systemCode(ChordMapper.ENTER_MASK, 1_000L));
        session.onChordCommitted(ChordInputCoordinator.ChordEvent.cleanCode(
                ChordMapper.LR, 70L, 1_100L));
        session.onChordCommitted(ChordInputCoordinator.ChordEvent.cleanCode(
                ChordMapper.TR | ChordMapper.LR, 70L, 1_200L));

        assertEquals("가", output.text.toString());
    }

    private static ChordInputCoordinator.ChordEvent systemCode(int fingerMask, long endedAt) {
        return new ChordInputCoordinator.ChordEvent(
                ChordMapper.THUMB_MASK | fingerMask,
                70L, 0L, 70L, true, true, false, endedAt);
    }

    private static final class RecordingOutput implements FoldChordSession.Output {
        final StringBuilder text = new StringBuilder();

        @Override
        public void sendText(String value) {
            text.append(value);
        }

        @Override
        public void sendKey(String keyName, int modifiers, int repeatCount) {
            if (!"BACKSPACE".equals(keyName)) {
                return;
            }
            for (int repeat = 0; repeat < repeatCount && text.length() > 0; repeat++) {
                int end = text.length();
                int start = Character.offsetByCodePoints(text, end, -1);
                text.delete(start, end);
            }
        }
    }
}
