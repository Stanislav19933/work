package com.forgptstas.neurorecorder;

public final class Utterance {
    private final int speakerId;
    private final String text;
    private final long timestampMs;
    private final long endTimestampMs;

    public Utterance(int speakerId, String text, long timestampMs) {
        this(speakerId, text, timestampMs, timestampMs);
    }

    public Utterance(int speakerId, String text, long timestampMs, long endTimestampMs) {
        this.speakerId = speakerId;
        this.text = text;
        this.timestampMs = timestampMs;
        this.endTimestampMs = endTimestampMs;
    }

    public int getSpeakerId() {
        return speakerId;
    }

    public String getSpeakerLabel() {
        return "Спикер " + (speakerId + 1);
    }

    public String getText() {
        return text;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getEndTimestampMs() {
        return endTimestampMs;
    }
}
