package com.forgptstas.neurorecorder;

final class Utterance {
    final int speakerId;
    final String text;
    final long timestampMs;

    Utterance(int speakerId, String text, long timestampMs) {
        this.speakerId = speakerId;
        this.text = text;
        this.timestampMs = timestampMs;
    }
}
