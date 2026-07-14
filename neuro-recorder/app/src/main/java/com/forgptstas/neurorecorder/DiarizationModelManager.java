package com.forgptstas.neurorecorder;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class DiarizationModelManager {
    public interface ProgressListener {
        void onProgress(String modelName, int percent);
    }

    private static final String SEGMENTATION_ASSET = "diarization/model.int8.onnx";
    private static final String EMBEDDING_ASSET = "diarization/nemo_en_titanet_small.onnx";

    private final Context context;
    private final File directory;
    private final File segmentationModel;
    private final File embeddingModel;

    public DiarizationModelManager(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(context.getFilesDir(), "diarization-models");
        segmentationModel = new File(directory, "pyannote-segmentation-3.0-int8.onnx");
        embeddingModel = new File(directory, "nemo-titanet-small.onnx");
    }

    public boolean isReady() {
        return isUsable(segmentationModel) && isUsable(embeddingModel);
    }

    public File getSegmentationModel() {
        return segmentationModel;
    }

    public File getEmbeddingModel() {
        return embeddingModel;
    }

    public void ensureModels(ProgressListener listener) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку моделей диаризации");
        }
        copyAssetIfNeeded(SEGMENTATION_ASSET, segmentationModel, "Сегментация", listener);
        copyAssetIfNeeded(EMBEDDING_ASSET, embeddingModel, "Голосовые признаки", listener);
    }

    private void copyAssetIfNeeded(
            String assetPath,
            File target,
            String displayName,
            ProgressListener listener
    ) throws Exception {
        if (isUsable(target)) {
            return;
        }

        File temporary = new File(target.getParentFile(), target.getName() + ".copying");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("Не удалось очистить временный файл модели");
        }

        try (InputStream input = new BufferedInputStream(context.getAssets().open(assetPath));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            byte[] buffer = new byte[64 * 1024];
            long copied = 0;
            int lastReported = -1;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                copied += count;
                if (listener != null) {
                    int progressStep = (int) Math.min(99, copied / (1024 * 1024));
                    if (progressStep != lastReported) {
                        lastReported = progressStep;
                        listener.onProgress(displayName, progressStep);
                    }
                }
            }
        }

        if (!isUsable(temporary)) {
            temporary.delete();
            throw new IllegalStateException("Встроенная модель диаризации повреждена");
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IllegalStateException("Не удалось заменить старую модель");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IllegalStateException("Не удалось сохранить модель диаризации");
        }
        if (listener != null) {
            listener.onProgress(displayName, 100);
        }
    }

    private static boolean isUsable(File file) {
        return file.isFile() && file.length() > 1024;
    }
}
