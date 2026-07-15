package com.forgptstas.neurorecorder.modules.asr;

import android.content.Context;
import android.net.Uri;

import com.forgptstas.neurorecorder.AudioDecoder;
import com.forgptstas.neurorecorder.modules.diarization.SherpaSpeakerDiarizationModule;
import com.forgptstas.neurorecorder.modules.diarization.SpeakerTextAligner;
import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.forgptstas.neurorecorder.modules.vad.SileroVadModule;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;


/** Primary Russian ASR implementation backed by sherpa-onnx GigaAM CTC v2. */
public final class SherpaGigaAmAsrModule implements AsrModule {
    private static final int SAMPLE_RATE = 16_000;
    private static final String MODEL_DIR = "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19";
    private static final String MODEL_FILE = MODEL_DIR + "/model.int8.onnx";
    private static final String TOKENS_FILE = MODEL_DIR + "/tokens.txt";
    private static final String MODEL_URL = "https://huggingface.co/csukuangfj/"
            + MODEL_DIR + "/resolve/main/model.int8.onnx";
    private static final String TOKENS_URL = "https://huggingface.co/csukuangfj/"
            + MODEL_DIR + "/resolve/main/tokens.txt";
    private static final long MIN_MODEL_BYTES = 200L * 1024L * 1024L;
    private static final long MIN_TOKENS_BYTES = 100L;

    private final ModelFileManager modelFileManager;
    private final SileroVadModule vadModule;
    private final SherpaSpeakerDiarizationModule diarizationModule;
    private final SpeakerTextAligner speakerTextAligner;

    public SherpaGigaAmAsrModule(
            ModelFileManager modelFileManager,
            SileroVadModule vadModule,
            SherpaSpeakerDiarizationModule diarizationModule
    ) {
        this.modelFileManager = modelFileManager;
        this.vadModule = vadModule;
        this.diarizationModule = diarizationModule;
        this.speakerTextAligner = new SpeakerTextAligner();
    }

    public boolean isModelReady() {
        return modelFileManager.isReady(MODEL_FILE, MIN_MODEL_BYTES)
                && modelFileManager.isReady(TOKENS_FILE, MIN_TOKENS_BYTES)
                && vadModule.isModelReady()
                && diarizationModule.isModelReady();
    }

    @Override
    public RecognitionResult transcribe(Context context, Uri audioUri, ProgressListener listener) throws Exception {
        ensureModelFiles(listener);
        if (listener != null) {
            listener.onProgress("audio", 0);
        }
        float[] samples = AudioDecoder.decodeToMono16Khz(context, audioUri);
        float[] speechSamples = vadModule.keepSpeech(samples, (message, percent) -> {
            if (listener != null) {
                listener.onProgress(message, percent);
            }
        });
        diarizationModule.setProgressListener((message, percent) -> {
            if (listener != null) {
                listener.onProgress(message, percent);
            }
        });
        java.util.List<com.forgptstas.neurorecorder.Utterance> speakerSegments = diarizationModule.assignSpeakers(samples, java.util.Collections.emptyList());
        if (listener != null) {
            listener.onProgress("asr", 0);
        }

        OfflineRecognizer recognizer = createRecognizer();
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(speechSamples, SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            java.util.List<com.forgptstas.neurorecorder.Utterance> alignedSegments =
                    speakerTextAligner.align(result.getText(), speakerSegments);
            if (listener != null) {
                listener.onProgress("done", 100);
            }
            return new RecognitionResult(result.getText(), alignedSegments);
        } finally {
            stream.release();
            recognizer.release();
        }
    }

    private void ensureModelFiles(ProgressListener listener) throws Exception {
        modelFileManager.ensureDownloaded(MODEL_FILE, MODEL_URL, MIN_MODEL_BYTES, percent -> {
            if (listener != null) {
                listener.onProgress("model", percent == 100 ? 90 : Math.max(1, percent * 90 / 100));
            }
        });
        modelFileManager.ensureDownloaded(TOKENS_FILE, TOKENS_URL, MIN_TOKENS_BYTES, percent -> {
            if (listener != null) {
                listener.onProgress("model", 90 + Math.max(0, Math.min(10, percent / 10)));
            }
        });
    }

    private OfflineRecognizer createRecognizer() {
        File model = modelFileManager.modelFile(MODEL_FILE);
        File tokens = modelFileManager.modelFile(TOKENS_FILE);

        FeatureConfig featureConfig = new FeatureConfig();
        featureConfig.setSampleRate(SAMPLE_RATE);
        featureConfig.setFeatureDim(80);
        featureConfig.setDither(0.0f);

        OfflineNemoEncDecCtcModelConfig nemo = new OfflineNemoEncDecCtcModelConfig();
        nemo.setModel(model.getAbsolutePath());

        OfflineModelConfig modelConfig = new OfflineModelConfig();
        modelConfig.setNemo(nemo);
        modelConfig.setTokens(tokens.getAbsolutePath());
        modelConfig.setNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        modelConfig.setProvider("cpu");

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(featureConfig);
        config.setModelConfig(modelConfig);
        config.setDecodingMethod("greedy_search");
        return new OfflineRecognizer(null, config);
    }
}
