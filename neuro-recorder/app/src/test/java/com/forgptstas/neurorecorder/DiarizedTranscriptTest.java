package com.forgptstas.neurorecorder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class DiarizedTranscriptTest {
    @Test
    public void groupsAdjacentSegmentsBySpeaker() {
        DiarizedTranscript transcript = new DiarizedTranscript(Arrays.asList(
                new SpeakerAttributedSegment("Алексей", "Привет.", 0, 1_000),
                new SpeakerAttributedSegment("Алексей", "Продолжаю мысль.", 1_000, 2_000),
                new SpeakerAttributedSegment("Мария", "Отвечаю.", 2_000, 3_000)
        ));

        assertEquals("Алексей: Привет. Продолжаю мысль.\n\nМария: Отвечаю.", transcript.toDisplayText());
    }
}
