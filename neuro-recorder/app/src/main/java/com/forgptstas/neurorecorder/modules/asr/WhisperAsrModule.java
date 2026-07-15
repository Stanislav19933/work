package com.forgptstas.neurorecorder.modules.asr;

import android.content.Context;
import android.net.Uri;

import com.forgptstas.neurorecorder.AudioDecoder;
import com.forgptstas.neurorecorder.WhisperEngine;
import com.forgptstas.neurorecorder.WhisperModelManager;

import java.io.File;
import java.util.Collections;

/** Current ASR implementation backed by the existing local Whisper JNI path. */
public final class WhisperAsrModule implements AsrModule {
    private final WhisperModelManager modelManager;
    private final WhisperEngine whisperEngine;
    private final String language;

    public WhisperAsrModule(WhisperModelManager modelManager, WhisperEngine whisperEngine, String language) {
        this.modelManager = modelManager;
        this.whisperEngine = whisperEngine;
        this.language = language;
    }

    public boolean isModelReady() {
        return modelManager.isReady();
    }

    @Override
    public RecognitionResult transcribe(Context context, Uri audioUri, ProgressListener listener) throws Exception {
        File model = modelManager.ensureModel(percent -> {
            if (listener != null) {
                listener.onProgress("model", percent);
            }
        });
        if (listener != null) {
            listener.onProgress("audio", 0);
        }
        float[] samples = AudioDecoder.decodeToMono16Khz(context, audioUri);
        if (listener != null) {
            listener.onProgress("asr", 0);
        }
        String text = whisperEngine.transcribe(model, samples, language);
        if (listener != null) {
            listener.onProgress("done", 100);
        }
        return new RecognitionResult(text, Collections.emptyList());
    }
}
