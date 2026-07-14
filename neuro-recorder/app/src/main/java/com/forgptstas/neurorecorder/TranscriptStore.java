package com.forgptstas.neurorecorder;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class TranscriptStore {
    private final File directory;

    public TranscriptStore(Context context) {
        directory = new File(context.getFilesDir(), "transcripts");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void save(RecordingItem item, String text) throws Exception {
        Files.write(fileFor(item).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    public String load(RecordingItem item) {
        File file = fileFor(item);
        if (!file.isFile()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
        return new File(directory, "recording_" + item.getId() + ".txt");
    }
}
