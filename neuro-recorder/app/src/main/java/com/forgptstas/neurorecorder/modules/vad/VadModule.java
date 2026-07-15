package com.forgptstas.neurorecorder.modules.vad;

/** Replaceable offline voice activity detection boundary. */
public interface VadModule {
    float[] keepSpeech(float[] samples, ProgressListener listener) throws Exception;

    interface ProgressListener {
        void onProgress(String message, int percent);
    }
}
