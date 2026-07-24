package com.foldchord.mvp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ChordInputCoordinatorTest {
    @Test
    public void mainThumbAndSubFingerInputsCommitAsOneChord() {
        ChordInputCoordinator coordinator = new ChordInputCoordinator();
        RecordingListener listener = new RecordingListener();
        coordinator.addListener(listener);

        long mainTr = ChordInputCoordinator.pointerToken(1, 1);
        long subLr = ChordInputCoordinator.pointerToken(2, 1);
        coordinator.press(mainTr, ChordMapper.TR, 1_000L);
        coordinator.press(subLr, ChordMapper.LR, 1_010L);
        coordinator.release(subLr, 1_090L);

        assertEquals(1, listener.events.size());
        assertEquals(ChordMapper.TR | ChordMapper.LR, listener.events.get(0).mask);
        assertEquals(ChordMapper.TR, coordinator.getCurrentMask());

        coordinator.release(mainTr, 1_100L);
    }

    @Test
    public void heldThumbsSelectSystemLayer() {
        ChordInputCoordinator coordinator = new ChordInputCoordinator();
        RecordingListener listener = new RecordingListener();
        coordinator.addListener(listener);

        coordinator.press(ChordInputCoordinator.pointerToken(1, 1),
                ChordMapper.TL, 1_000L);
        coordinator.press(ChordInputCoordinator.pointerToken(1, 2),
                ChordMapper.TR, 1_010L);
        long subLi = ChordInputCoordinator.pointerToken(2, 1);
        coordinator.press(subLi, ChordMapper.LI, 1_220L);
        coordinator.release(subLi, 1_290L);

        assertEquals(1, listener.events.size());
        assertTrue(listener.events.get(0).systemLayer);
    }

    private static final class RecordingListener implements ChordInputCoordinator.Listener {
        final List<ChordInputCoordinator.ChordEvent> events = new ArrayList<>();

        @Override
        public void onChordChanged(int mask) {
        }

        @Override
        public void onChordCommitted(ChordInputCoordinator.ChordEvent event) {
            events.add(event);
        }
    }
}
