package com.forgptstas.neurorecorder.modules.diarization;

import com.forgptstas.neurorecorder.Utterance;

import java.util.ArrayList;
import java.util.List;

/** Attaches the recognized transcript text to diarized speaker intervals in chronological order. */
public final class SpeakerTextAligner {
    public List<Utterance> align(String transcript, List<Utterance> speakerSegments) {
        if (speakerSegments == null || speakerSegments.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> chunks = splitTranscript(transcript);
        if (chunks.isEmpty()) {
            return speakerSegments;
        }
        ArrayList<Utterance> aligned = new ArrayList<>();
        int chunkIndex = 0;
        for (int i = 0; i < speakerSegments.size(); i++) {
            Utterance segment = speakerSegments.get(i);
            int remainingSegments = speakerSegments.size() - i;
            int remainingChunks = chunks.size() - chunkIndex;
            int take = Math.max(1, (int) Math.ceil(remainingChunks / (double) remainingSegments));
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < take && chunkIndex < chunks.size(); j++) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(chunks.get(chunkIndex++));
            }
            aligned.add(new Utterance(
                    segment.getSpeakerId(),
                    text.toString(),
                    segment.getTimestampMs(),
                    segment.getEndTimestampMs()
            ));
        }
        return aligned;
    }

    private static List<String> splitTranscript(String transcript) {
        ArrayList<String> chunks = new ArrayList<>();
        if (transcript == null || transcript.isBlank()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < transcript.length(); i++) {
            char c = transcript.charAt(i);
            current.append(c);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                addChunk(chunks, current);
            }
        }
        addChunk(chunks, current);
        return chunks;
    }

    private static void addChunk(List<String> chunks, StringBuilder current) {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            chunks.add(text);
        }
        current.setLength(0);
    }
}
