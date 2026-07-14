package com.forgptstas.neurorecorder;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class DiarizationModelManager {
    public interface ProgressListener {
        void onProgress(String modelName, int percent);
    }

    private static final String SEGMENTATION_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/model.int8.onnx";
    private static final String EMBEDDING_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/nemo_en_titanet_small.onnx";

    private final File directory;
    private final File segmentationModel;
    private final File embeddingModel;

    public DiarizationModelManager(Context context) {
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
        ensureModel(SEGMENTATION_URL, segmentationModel, "Сегментация", listener);
        ensureModel(EMBEDDING_URL, embeddingModel, "Голосовые признаки", listener);
    }

    private static void ensureModel(
            String sourceUrl,
            File target,
            String displayName,
            ProgressListener listener
    ) throws Exception {
        if (isUsable(target)) {
            return;
        }

        File temporary = new File(target.getParentFile(), target.getName() + ".download");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("Не удалось очистить незавершённую загрузку модели");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "NeuroRecorder/0.3");

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Сервер модели вернул HTTP " + status);
            }

            long total = connection.getContentLengthLong();
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0;
                int lastPercent = -1;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                    downloaded += count;
                    if (listener != null && total > 0) {
                        int percent = (int) Math.min(100, downloaded * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(displayName, percent);
                        }
                    }
                }
            }

            if (!isUsable(temporary)) {
                throw new IllegalStateException("Загруженная модель пуста");
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Не удалось заменить старую модель");
            }
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("Не удалось сохранить модель");
            }
        } finally {
            connection.disconnect();
            if (temporary.exists() && !target.exists()) {
                temporary.delete();
            }
        }
    }

    private static boolean isUsable(File file) {
        return file.isFile() && file.length() > 1024;
    }
}
