package com.forgptstas.neurorecorder.modules.vad;

import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Silero VAD implementation backed by the official sherpa-onnx VAD API. */
public final class SileroVadModule implements VadModule {
    private static final int SAMPLE_RATE = 16_000;
    private static final String MODEL_FILE = "silero-vad/silero_vad.onnx";
    private static final String MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx";
    private static final long MIN_MODEL_BYTES = 1L * 1024L * 1024L;

    private final ModelFileManager modelFileManager;

    public SileroVadModule(ModelFileManager modelFileManager) {
        this.modelFileManager = modelFileManager;
    }

    public boolean isModelReady() {
        return modelFileManager.isReady(MODEL_FILE, MIN_MODEL_BYTES);
    }

    @Override
    public float[] keepSpeech(float[] samples, ProgressListener listener) throws Exception {
        ensureModel(listener);
        if (listener != null) {
            listener.onProgress("vad", 0);
        }
        Vad vad = createVad();
        try {
            vad.acceptWaveform(samples);
            vad.flush();
            List<float[]> segments = new ArrayList<>();
            int totalSamples = 0;
            while (!vad.empty()) {
                SpeechSegment segment = vad.front();
                float[] speech = segment.getSamples();
                if (speech.length > 0) {
                    segments.add(speech);
                    totalSamples += speech.length;
                }
                vad.pop();
            }
            if (listener != null) {
                listener.onProgress("vad", 100);
            }
            if (totalSamples == 0) {
                return samples;
            }
            float[] compact = new float[totalSamples];
            int offset = 0;
            for (float[] segment : segments) {
                System.arraycopy(segment, 0, compact, offset, segment.length);
                offset += segment.length;
            }
            return compact;
        } finally {
            vad.release();
        }
    }

    private void ensureModel(ProgressListener listener) throws Exception {
        modelFileManager.ensureDownloaded(MODEL_FILE, MODEL_URL, MIN_MODEL_BYTES, percent -> {
            if (listener != null) {
                listener.onProgress("vad_model", percent);
            }
        });
    }

    private Vad createVad() {
        File model = modelFileManager.modelFile(MODEL_FILE);
        SileroVadModelConfig silero = new SileroVadModelConfig();
        silero.setModel(model.getAbsolutePath());
        silero.setThreshold(0.5f);
        silero.setMinSilenceDuration(0.25f);
        silero.setMinSpeechDuration(0.25f);
        silero.setWindowSize(512);
        silero.setMaxSpeechDuration(30.0f);

        VadModelConfig config = new VadModelConfig();
        config.setSileroVadModelConfig(silero);
        config.setSampleRate(SAMPLE_RATE);
        config.setNumThreads(1);
        config.setProvider("cpu");
        return new Vad(null, config);
    }
}
