package com.forgptstas.neurorecorder.modules.diarization;

import static org.junit.Assert.assertEquals;

import com.forgptstas.neurorecorder.Utterance;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class SpeakerTextAlignerTest {
    @Test
    public void attachesTextToSpeakerIntervalsInOrder() {
        SpeakerTextAligner aligner = new SpeakerTextAligner();
        List<Utterance> segments = Arrays.asList(
                new Utterance(0, "", 0, 2000),
                new Utterance(1, "", 2000, 5000)
        );

        List<Utterance> result = aligner.align("Привет. Обсудим задачу? Сделаю сегодня.", segments);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getSpeakerId());
        assertEquals("Привет. Обсудим задачу?", result.get(0).getText());
        assertEquals(1, result.get(1).getSpeakerId());
        assertEquals("Сделаю сегодня.", result.get(1).getText());
        assertEquals(5000, result.get(1).getEndTimestampMs());
    }
}
