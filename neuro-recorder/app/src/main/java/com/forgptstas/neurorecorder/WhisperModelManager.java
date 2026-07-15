package com.forgptstas.neurorecorder;

import android.content.Context;

import com.forgptstas.neurorecorder.modules.models.ModelFileManager;

import java.io.File;

public final class WhisperModelManager {
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private static final String MODEL_NAME = "ggml-base.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    private static final long MIN_MODEL_BYTES = 100L * 1024L * 1024L;

    private final ModelFileManager modelFileManager;

    public WhisperModelManager(Context context) {
        modelFileManager = new ModelFileManager(context);
    }

    public File getModelFile() {
        return modelFileManager.modelFile(MODEL_NAME);
    }

    public boolean isReady() {
        return modelFileManager.isReady(MODEL_NAME, MIN_MODEL_BYTES);
    }

    public File ensureModel(ProgressListener listener) throws Exception {
        return modelFileManager.ensureDownloaded(
                MODEL_NAME,
                MODEL_URL,
                MIN_MODEL_BYTES,
                listener == null ? null : listener::onProgress
        );
    }
}
