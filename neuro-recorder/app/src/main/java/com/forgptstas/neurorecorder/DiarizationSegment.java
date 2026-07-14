package com.forgptstas.neurorecorder;

import java.util.Objects;

public final class DiarizationSegment {
    private final float startSeconds;
    private final float endSeconds;
    private final int speakerIndex;

    public DiarizationSegment(float startSeconds, float endSeconds, int speakerIndex) {
        if (startSeconds < 0f) {
            throw new IllegalArgumentException("startSeconds must be non-negative");
        }
        if (endSeconds <= startSeconds) {
            throw new IllegalArgumentException("endSeconds must be greater than startSeconds");
        }
        if (speakerIndex < 0) {
            throw new IllegalArgumentException("speakerIndex must be non-negative");
        }
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.speakerIndex = speakerIndex;
    }

    public float getStartSeconds() {
        return startSeconds;
    }

    public float getEndSeconds() {
        return endSeconds;
    }

    public int getSpeakerIndex() {
        return speakerIndex;
    }

    public String getSpeakerLabel() {
        return "Спикер " + (speakerIndex + 1);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiarizationSegment)) {
            return false;
        }
        DiarizationSegment that = (DiarizationSegment) other;
        return Float.compare(startSeconds, that.startSeconds) == 0
                && Float.compare(endSeconds, that.endSeconds) == 0
                && speakerIndex == that.speakerIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startSeconds, endSeconds, speakerIndex);
    }
}
