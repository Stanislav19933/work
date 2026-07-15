package com.forgptstas.neurorecorder;

/** Text fragment attributed to one diarized speaker interval. */
public final class SpeakerAttributedSegment {
    private final String speakerLabel;
    private final String text;
    private final long startMs;
    private final long endMs;

    public SpeakerAttributedSegment(String speakerLabel, String text, long startMs, long endMs) {
        this.speakerLabel = speakerLabel == null || speakerLabel.isBlank() ? "Спикер" : speakerLabel;
        this.text = text == null ? "" : text;
        this.startMs = Math.max(0L, startMs);
        this.endMs = Math.max(this.startMs, endMs);
    }

    public SpeakerAttributedSegment(int speakerId, String text, long startMs, long endMs) {
        this("Спикер " + (speakerId + 1), text, startMs, endMs);
    }

    public static SpeakerAttributedSegment fromUtterance(Utterance utterance) {
        return new SpeakerAttributedSegment(
                utterance.getSpeakerLabel(),
                utterance.getText(),
                utterance.getTimestampMs(),
                utterance.getEndTimestampMs()
        );
    }

    public String getSpeakerLabel() {
        return speakerLabel;
    }

    public String getText() {
        return text;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }
}
