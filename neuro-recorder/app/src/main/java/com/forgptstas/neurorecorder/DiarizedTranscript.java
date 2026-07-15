package com.forgptstas.neurorecorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiarizedTranscript {
    private final List<SpeakerAttributedSegment> segments;

    public DiarizedTranscript(List<SpeakerAttributedSegment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    public List<SpeakerAttributedSegment> getSegments() {
        return segments;
    }

    public String toDisplayText() {
        StringBuilder result = new StringBuilder();
        String previousSpeaker = null;

        for (SpeakerAttributedSegment segment : segments) {
            if (!segment.getSpeakerLabel().equals(previousSpeaker)) {
                if (result.length() > 0) {
                    result.append('\n').append('\n');
                }
                result.append(segment.getSpeakerLabel()).append(": ");
                previousSpeaker = segment.getSpeakerLabel();
            } else {
                result.append(' ');
            }
            result.append(segment.getText().trim());
        }

        return result.toString().trim();
    }
}
