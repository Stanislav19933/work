package com.forgptstas.neurorecorder;

import java.io.File;

public final class WhisperEngine {
    static {
        System.loadLibrary("neuro_whisper");
    }

    public String transcribe(File modelFile, float[] samples, String language) {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IllegalArgumentException("Модель Whisper не найдена.");
        }
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("Аудиоданные пусты.");
        }
        int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
        String text = transcribeNative(modelFile.getAbsolutePath(), samples, threads, language);
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalStateException("Whisper не вернул текст.");
        }
        return text.trim();
    }

    private native String transcribeNative(String modelPath, float[] samples, int threads, String language);
}
