package com.forgptstas.neurorecorder;

public final class WhisperSegment {
    private final long startMillis;
    private final long endMillis;
    private final String text;

    public WhisperSegment(long startMillis, long endMillis, String text) {
        this.startMillis = Math.max(0L, startMillis);
        this.endMillis = Math.max(this.startMillis, endMillis);
        this.text = text == null ? "" : text.trim();
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public String getText() {
        return text;
    }

    public long midpointMillis() {
        return startMillis + (endMillis - startMillis) / 2L;
    }
}
