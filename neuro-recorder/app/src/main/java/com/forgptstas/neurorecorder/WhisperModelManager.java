package com.forgptstas.neurorecorder;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class WhisperModelManager {
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private static final String MODEL_NAME = "ggml-base.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";

    private final File modelFile;

    public WhisperModelManager(Context context) {
        File directory = new File(context.getFilesDir(), "models");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        modelFile = new File(directory, MODEL_NAME);
    }

    public File getModelFile() {
        return modelFile;
    }

    public boolean isReady() {
        return modelFile.isFile() && modelFile.length() > 100L * 1024L * 1024L;
    }

    public File ensureModel(ProgressListener listener) throws Exception {
        if (isReady()) {
            return modelFile;
        }
        File part = new File(modelFile.getParentFile(), MODEL_NAME + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Сервер модели вернул HTTP " + code);
        }
        long total = connection.getContentLengthLong();
        long downloaded = 0;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(part, false)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            int lastPercent = -1;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (total > 0 && listener != null) {
                    int percent = (int) Math.min(100, downloaded * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }
        if (part.length() < 100L * 1024L * 1024L) {
            part.delete();
            throw new IllegalStateException("Модель скачалась не полностью.");
        }
        if (modelFile.exists() && !modelFile.delete()) {
            part.delete();
            throw new IllegalStateException("Не удалось заменить модель.");
        }
        if (!part.renameTo(modelFile)) {
            part.delete();
            throw new IllegalStateException("Не удалось сохранить модель.");
        }
        return modelFile;
    }
}
