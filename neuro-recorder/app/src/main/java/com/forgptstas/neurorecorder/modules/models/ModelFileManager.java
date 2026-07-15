package com.forgptstas.neurorecorder.modules.models;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads and caches local model files used by on-device processing modules. */
public final class ModelFileManager {
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private final File modelsDirectory;

    public ModelFileManager(Context context) {
        modelsDirectory = new File(context.getFilesDir(), "models");
        if (!modelsDirectory.exists()) {
            modelsDirectory.mkdirs();
        }
    }

    public File modelFile(String fileName) {
        return new File(modelsDirectory, fileName);
    }

    public boolean isReady(String fileName, long minBytes) {
        File file = modelFile(fileName);
        return file.isFile() && file.length() >= minBytes;
    }

    public void clearAll() {
        deleteRecursively(modelsDirectory);
        modelsDirectory.mkdirs();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public File ensureDownloaded(String fileName, String url, long minBytes, ProgressListener listener) throws Exception {
        File modelFile = modelFile(fileName);
        if (isReady(fileName, minBytes)) {
            if (listener != null) {
                listener.onProgress(100);
            }
            return modelFile;
        }

        File parent = modelFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку модели.");
        }
        File part = new File(modelsDirectory, fileName + ".part");
        File partParent = part.getParentFile();
        if (partParent != null && !partParent.exists() && !partParent.mkdirs()) {
            throw new IllegalStateException("Не удалось создать временную папку модели.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
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

        if (part.length() < minBytes) {
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
        if (listener != null) {
            listener.onProgress(100);
        }
        return modelFile;
    }
}
