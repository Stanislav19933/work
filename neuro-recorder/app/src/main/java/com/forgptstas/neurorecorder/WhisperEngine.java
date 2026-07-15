package com.forgptstas.neurorecorder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class WhisperEngine {
    private static final String RECORD_SEPARATOR = "\u001e";
    private static final String FIELD_SEPARATOR = "\u001f";

    static {
        System.loadLibrary("neuro_whisper");
    }

    public String transcribe(File modelFile, float[] samples, String language) {
        List<WhisperSegment> segments = transcribeSegments(modelFile, samples, language);
        StringBuilder text = new StringBuilder();
        for (WhisperSegment segment : segments) {
            if (!segment.getText().isEmpty()) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(segment.getText());
            }
        }
        if (text.length() == 0) {
            throw new IllegalStateException("Whisper не вернул текст.");
        }
        return text.toString().trim();
    }

    public List<WhisperSegment> transcribeSegments(File modelFile, float[] samples, String language) {
        validateInput(modelFile, samples);
        int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
        String encoded = transcribeSegmentsNative(
                modelFile.getAbsolutePath(),
                samples,
                threads,
                language == null || language.trim().isEmpty() ? "ru" : language.trim()
        );
        List<WhisperSegment> result = parseSegments(encoded);
        if (result.isEmpty()) {
            throw new IllegalStateException("Whisper не вернул сегменты текста.");
        }
        return result;
    }

    private void validateInput(File modelFile, float[] samples) {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IllegalArgumentException("Модель Whisper не найдена.");
        }
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("Аудиоданные пусты.");
        }
    }

    private List<WhisperSegment> parseSegments(String encoded) {
        List<WhisperSegment> segments = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return segments;
        }
        String[] records = encoded.split(RECORD_SEPARATOR, -1);
        for (String record : records) {
            if (record.isEmpty()) {
                continue;
            }
            String[] fields = record.split(FIELD_SEPARATOR, 3);
            if (fields.length != 3) {
                continue;
            }
            try {
                long startMillis = Long.parseLong(fields[0]);
                long endMillis = Long.parseLong(fields[1]);
                String text = fields[2].trim();
                if (!text.isEmpty()) {
                    segments.add(new WhisperSegment(startMillis, endMillis, text));
                }
            } catch (NumberFormatException ignored) {
                // Один повреждённый сегмент не должен ломать всю расшифровку.
            }
        }
        return segments;
    }

    private native String transcribeSegmentsNative(
            String modelPath,
            float[] samples,
            int threads,
            String language
    );
}
