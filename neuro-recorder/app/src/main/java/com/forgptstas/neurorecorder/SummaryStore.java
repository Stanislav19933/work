package com.forgptstas.neurorecorder;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SummaryStore {
    private final File directory;

    public SummaryStore(Context context) {
        directory = new File(context.getFilesDir(), "summaries");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void save(RecordingItem item, String text) throws Exception {
        Files.writeString(fileFor(item).toPath(), text, StandardCharsets.UTF_8);
    }

    public String load(RecordingItem item) {
        File file = fileFor(item);
        if (!file.isFile()) {
            return null;
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void delete(RecordingItem item) {
        File file = fileFor(item);
        if (file.exists()) {
            file.delete();
        }
    }

    private File fileFor(RecordingItem item) {
        return new File(directory, "summary_" + item.getId() + ".txt");
    }
}
