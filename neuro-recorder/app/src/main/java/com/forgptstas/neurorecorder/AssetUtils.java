package com.forgptstas.neurorecorder;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class AssetUtils {
    private AssetUtils() {
    }

    static File prepareModel(Context context, String assetRoot, String targetName) throws IOException {
        File modelsRoot = new File(context.getFilesDir(), "models");
        File target = new File(modelsRoot, targetName);
        File marker = new File(target, ".ready");

        if (marker.isFile()) {
            return target;
        }

        if (target.exists()) {
            deleteRecursively(target);
        }
        if (!target.mkdirs() && !target.isDirectory()) {
            throw new IOException("Не удалось создать каталог модели: " + target);
        }

        copyAssetTree(context.getAssets(), assetRoot, target);

        if (!marker.createNewFile() && !marker.isFile()) {
            throw new IOException("Не удалось создать маркер модели");
        }
        return target;
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File target) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null) {
            throw new IOException("Не удалось прочитать assets: " + assetPath);
        }

        if (children.length == 0) {
            copyAssetFile(assets, assetPath, target);
            return;
        }

        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + target);
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childTarget = new File(target, child);
            copyAssetTree(assets, childAssetPath, childTarget);
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + parent);
        }

        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Не удалось удалить: " + file);
        }
    }
}
