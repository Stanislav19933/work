package com.forgptstas.neurorecorder.modules.diarization;

import com.forgptstas.neurorecorder.Utterance;
import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.k2fsa.sherpa.onnx.FastClusteringConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Official sherpa-onnx speaker diarization pipeline: segmentation, embeddings, clustering. */
public final class SherpaSpeakerDiarizationModule implements SpeakerDiarizationModule {
    public interface ProgressListener {
        void onProgress(String message, int percent);
    }

    private static final String SEGMENTATION_FILE = "speaker-diarization/pyannote-segmentation-3-0/model.int8.onnx";
    private static final String SEGMENTATION_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.int8.onnx";
    private static final long MIN_SEGMENTATION_BYTES = 4L * 1024L * 1024L;
    private static final String EMBEDDING_FILE = "speaker-diarization/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
    private static final String EMBEDDING_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
    private static final long MIN_EMBEDDING_BYTES = 20L * 1024L * 1024L;

    private final ModelFileManager modelFileManager;
    private ProgressListener progressListener;

    public SherpaSpeakerDiarizationModule(ModelFileManager modelFileManager) {
        this.modelFileManager = modelFileManager;
    }

    public boolean isModelReady() {
        return modelFileManager.isReady(SEGMENTATION_FILE, MIN_SEGMENTATION_BYTES)
                && modelFileManager.isReady(EMBEDDING_FILE, MIN_EMBEDDING_BYTES);
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    @Override
    public List<Utterance> assignSpeakers(float[] monoPcm16Khz, List<Utterance> ignored) throws Exception {
        ensureModels();
        notifyProgress("diarization", 0);
        OfflineSpeakerDiarization diarization = createDiarization();
        try {
            OfflineSpeakerDiarizationSegment[] segments = diarization.process(monoPcm16Khz);
            ArrayList<Utterance> utterances = new ArrayList<>();
            for (OfflineSpeakerDiarizationSegment segment : segments) {
                long startMs = (long) (segment.getStart() * 1000L);
                long endMs = (long) (segment.getEnd() * 1000L);
                utterances.add(new Utterance(segment.getSpeaker(), "", startMs, endMs));
            }
            notifyProgress("diarization", 100);
            return utterances;
        } finally {
            diarization.release();
        }
    }

    private void ensureModels() throws Exception {
        modelFileManager.ensureDownloaded(SEGMENTATION_FILE, SEGMENTATION_URL, MIN_SEGMENTATION_BYTES, percent -> {
            notifyProgress("diarization_model", Math.max(1, percent / 2));
        });
        modelFileManager.ensureDownloaded(EMBEDDING_FILE, EMBEDDING_URL, MIN_EMBEDDING_BYTES, percent -> {
            notifyProgress("diarization_model", 50 + Math.max(0, percent / 2));
        });
    }

    private OfflineSpeakerDiarization createDiarization() {
        File segmentation = modelFileManager.modelFile(SEGMENTATION_FILE);
        File embedding = modelFileManager.modelFile(EMBEDDING_FILE);

        OfflineSpeakerSegmentationPyannoteModelConfig pyannote = new OfflineSpeakerSegmentationPyannoteModelConfig();
        pyannote.setModel(segmentation.getAbsolutePath());

        OfflineSpeakerSegmentationModelConfig segmentationConfig = new OfflineSpeakerSegmentationModelConfig();
        segmentationConfig.setPyannote(pyannote);
        segmentationConfig.setNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        segmentationConfig.setProvider("cpu");

        SpeakerEmbeddingExtractorConfig embeddingConfig = new SpeakerEmbeddingExtractorConfig(
                embedding.getAbsolutePath(),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                false,
                "cpu"
        );

        FastClusteringConfig clusteringConfig = new FastClusteringConfig();
        clusteringConfig.setNumClusters(-1);
        clusteringConfig.setThreshold(0.5f);

        OfflineSpeakerDiarizationConfig config = new OfflineSpeakerDiarizationConfig();
        config.setSegmentation(segmentationConfig);
        config.setEmbedding(embeddingConfig);
        config.setClustering(clusteringConfig);
        config.setMinDurationOn(0.2f);
        config.setMinDurationOff(0.5f);
        return new OfflineSpeakerDiarization(null, config);
    }

    private void notifyProgress(String message, int percent) {
        if (progressListener != null) {
            progressListener.onProgress(message, Math.max(0, Math.min(100, percent)));
        }
    }

}
