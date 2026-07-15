package com.forgptstas.neurorecorder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class DiarizationStore {
    private final File directory;

    public DiarizationStore(android.content.Context context) {
        directory = new File(context.getFilesDir(), "diarization");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void save(RecordingItem item, List<Utterance> utterances) throws Exception {
        save(item, utterances, java.util.Collections.emptyMap());
    }

    public void save(RecordingItem item, List<Utterance> utterances, Map<String, String> speakerNames) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Utterance utterance : utterances) {
            builder.append(speakerTitle(utterance, speakerNames))
                    .append(" • ")
                    .append(formatInterval(utterance))
                    .append(" • ")
                    .append(displayText(utterance))
                    .append('\n');
        }
        Files.write(fileFor(item).toPath(), builder.toString().getBytes(StandardCharsets.UTF_8));
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
        return new File(directory, "recording_" + item.getId() + ".txt");
    }

    private static String speakerTitle(Utterance utterance, Map<String, String> speakerNames) {
        String name = speakerNames.get(String.valueOf(utterance.getSpeakerId()));
        return name == null || name.isBlank() ? utterance.getSpeakerLabel() : name;
    }

    private static String displayText(Utterance utterance) {
        String text = utterance.getText();
        return text == null || text.isBlank() ? "[речь без текста]" : text;
    }

    private static String formatInterval(Utterance utterance) {
        return formatTime(utterance.getTimestampMs()) + "–" + formatTime(utterance.getEndTimestampMs());
    }

    private static String formatTime(long timestampMs) {
        long totalSeconds = Math.max(0, timestampMs / 1000L);
        return String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
