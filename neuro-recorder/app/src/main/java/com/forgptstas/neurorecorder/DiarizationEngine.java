package com.forgptstas.neurorecorder;

import java.io.File;
import java.util.List;

public interface DiarizationEngine {
    interface ProgressListener {
        void onProgress(int processedChunks, int totalChunks);
    }

    List<DiarizationSegment> process(
            File segmentationModel,
            File embeddingModel,
            float[] samples,
            int sampleRate,
            ProgressListener progressListener
    ) throws Exception;
}
