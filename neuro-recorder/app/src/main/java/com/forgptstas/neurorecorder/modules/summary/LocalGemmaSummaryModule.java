package com.forgptstas.neurorecorder.modules.summary;

import android.content.Context;

import com.forgptstas.neurorecorder.Utterance;
import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runs a downloaded Gemma 3 1B LiteRT model locally through MediaPipe LLM Inference. */
public final class LocalGemmaSummaryModule implements SummaryModule {
    public interface ProgressListener {
        void onProgress(String stage, int percent);
    }

    public static final String MODEL_FILE = "summary/gemma3-1b-it-int4.task";
    public static final String MODEL_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task";
    public static final long MIN_MODEL_BYTES = 200L * 1024L * 1024L;
    private static final int MAX_TRANSCRIPT_CHARS = 18_000;

    private final Context context;
    private final ModelFileManager modelFileManager;

    public LocalGemmaSummaryModule(Context context, ModelFileManager modelFileManager) {
        this.context = context.getApplicationContext();
        this.modelFileManager = modelFileManager;
    }

    public boolean isReady() {
        return modelFileManager.isReady(MODEL_FILE, MIN_MODEL_BYTES);
    }

    @Override
    public MeetingSummary summarize(List<Utterance> utterances) throws Exception {
        StringBuilder transcript = new StringBuilder();
        if (utterances != null) {
            for (Utterance utterance : utterances) {
                transcript.append(utterance.getSpeakerLabel()).append(": ").append(utterance.getText()).append('\n');
            }
        }
        return summarizeTranscript(transcript.toString(), null);
    }

    public MeetingSummary summarizeTranscript(String transcript, ProgressListener listener) throws Exception {
        notify(listener, "summary_model", 1);
        File model = modelFileManager.ensureDownloaded(MODEL_FILE, MODEL_URL, MIN_MODEL_BYTES, percent -> notify(listener, "summary_model", percent));
        notify(listener, "summary", 1);
        String response = generate(model, buildPrompt(trimTranscript(transcript)));
        notify(listener, "summary", 100);
        return parseResponse(response);
    }

    public String formatForDisplay(MeetingSummary summary) {
        return "Краткое содержание:\n" + safe(summary.getShortSummary())
                + "\n\nЗадачи:\n" + bulletList(summary.getTasks())
                + "\n\nОтветственные:\n" + bulletList(summary.getOwners())
                + "\n\nСроки:\n" + bulletList(summary.getDeadlines())
                + "\n\nОткрытые вопросы:\n" + bulletList(summary.getOpenQuestions())
                + "\n\nВажные цифры:\n" + bulletList(summary.getImportantNumbers())
                + "\n\nРешения:\n" + bulletList(summary.getDecisions());
    }

    private String generate(File model, String prompt) throws Exception {
        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.getAbsolutePath())
                .setMaxTokens(4096)
                .setTopK(40)
                .setTemperature(0.2f)
                .setRandomSeed(7)
                .build();
        try (LlmInference inference = LlmInference.createFromOptions(context, options)) {
            return inference.generateResponse(prompt);
        }
    }

    private static String buildPrompt(String transcript) {
        return "Ты локальный помощник NeuroRecorder. Данные нельзя отправлять в интернет. "
                + "Сделай строго структурированное саммари встречи на русском языке. "
                + "Не выдумывай факты: если пункт не найден, напиши 'Не найдено'.\n\n"
                + "Верни ответ ровно в таком формате:\n"
                + "Краткое содержание:\n...\n\n"
                + "Задачи:\n- ...\n\n"
                + "Ответственные:\n- ...\n\n"
                + "Сроки:\n- ...\n\n"
                + "Открытые вопросы:\n- ...\n\n"
                + "Важные цифры:\n- ...\n\n"
                + "Решения:\n- ...\n\n"
                + "Расшифровка встречи:\n" + transcript;
    }

    private static String trimTranscript(String transcript) {
        String value = transcript == null ? "" : transcript.trim();
        if (value.length() <= MAX_TRANSCRIPT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_TRANSCRIPT_CHARS)
                + "\n\n[Расшифровка была обрезана до " + MAX_TRANSCRIPT_CHARS + " символов из-за лимита локальной SLM-модели.]";
    }

    private static MeetingSummary parseResponse(String response) {
        String value = response == null ? "" : response.trim();
        return new MeetingSummary(
                section(value, "Краткое содержание", "Задачи"),
                bullets(section(value, "Задачи", "Ответственные")),
                bullets(section(value, "Ответственные", "Сроки")),
                bullets(section(value, "Сроки", "Открытые вопросы")),
                bullets(section(value, "Открытые вопросы", "Важные цифры")),
                bullets(section(value, "Важные цифры", "Решения")),
                bullets(section(value, "Решения", null))
        );
    }

    private static String section(String text, String start, String end) {
        int startIndex = text.indexOf(start + ":");
        if (startIndex < 0) {
            return "Не найдено";
        }
        startIndex += start.length() + 1;
        int endIndex = end == null ? -1 : text.indexOf(end + ":", startIndex);
        String section = endIndex < 0 ? text.substring(startIndex) : text.substring(startIndex, endIndex);
        String trimmed = section.trim();
        return trimmed.isEmpty() ? "Не найдено" : trimmed;
    }

    private static List<String> bullets(String section) {
        if (section == null || section.isBlank() || "Не найдено".equalsIgnoreCase(section.trim())) {
            return Collections.emptyList();
        }
        ArrayList<String> result = new ArrayList<>();
        for (String line : section.split("\\R")) {
            String item = line.trim();
            while (item.startsWith("-") || item.startsWith("•") || item.startsWith("*")) {
                item = item.substring(1).trim();
            }
            if (!item.isEmpty() && !"Не найдено".equalsIgnoreCase(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static String bulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- Не найдено";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
        return builder.toString().trim();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Не найдено" : value.trim();
    }

    private static void notify(ProgressListener listener, String stage, int percent) {
        if (listener != null) {
            listener.onProgress(stage, Math.max(0, Math.min(100, percent)));
        }
    }
}
