package com.forgptstas.neurorecorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SpeakerTranscriptComposer {
    public String compose(List<WhisperSegment> whisperSegments, List<DiarizationSegment> diarizationSegments) {
        if (whisperSegments == null || whisperSegments.isEmpty()) {
            return "";
        }

        List<DiarizationSegment> speakers = diarizationSegments == null
                ? Collections.emptyList()
                : new ArrayList<>(diarizationSegments);
        speakers.sort(Comparator.comparingDouble(DiarizationSegment::getStartSeconds));

        StringBuilder output = new StringBuilder();
        int previousSpeaker = Integer.MIN_VALUE;
        for (WhisperSegment segment : whisperSegments) {
            if (segment.getText().isEmpty()) {
                continue;
            }
            int speaker = findSpeaker(segment.midpointMillis(), speakers);
            if (output.length() > 0) {
                output.append('\n');
            }
            if (speaker != previousSpeaker) {
                output.append(speaker >= 0 ? "Спикер " + (speaker + 1) : "Спикер неизвестен").append(": ");
            }
            output.append(segment.getText());
            previousSpeaker = speaker;
        }
        return output.toString().trim();
    }

    private int findSpeaker(long midpointMillis, List<DiarizationSegment> segments) {
        double pointSeconds = midpointMillis / 1000.0;
        DiarizationSegment nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (DiarizationSegment segment : segments) {
            if (pointSeconds >= segment.getStartSeconds() && pointSeconds <= segment.getEndSeconds()) {
                return segment.getSpeakerIndex();
            }
            double distance = Math.min(
                    Math.abs(pointSeconds - segment.getStartSeconds()),
                    Math.abs(pointSeconds - segment.getEndSeconds())
            );
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = segment;
            }
        }
        return nearest != null && nearestDistance <= 0.75 ? nearest.getSpeakerIndex() : -1;
    }
}
