package com.forgptstas.neurorecorder.modules.summary;

import com.forgptstas.neurorecorder.Utterance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fully local extractive summarizer that produces structured meeting notes on device. */
public final class LocalExtractiveSummaryModule implements SummaryModule {
    @Override
    public MeetingSummary summarize(List<Utterance> utterances) {
        StringBuilder transcript = new StringBuilder();
        if (utterances != null) {
            for (Utterance utterance : utterances) {
                transcript.append(utterance.getText()).append('\n');
            }
        }
        return summarizeTranscript(transcript.toString());
    }

    public MeetingSummary summarizeTranscript(String transcript) {
        List<String> sentences = splitSentences(transcript == null ? "" : transcript);
        String shortSummary = sentences.isEmpty()
                ? "Недостаточно текста для саммари."
                : String.join(" ", sentences.subList(0, Math.min(3, sentences.size())));
        return new MeetingSummary(
                shortSummary,
                findByKeywords(sentences, "задач", "сдел", "подготов", "отправ", "провер", "нужно", "надо"),
                findByKeywords(sentences, "ответствен", "назнач", "беру", "возьму", "сделаю"),
                findByKeywords(sentences, "срок", "дедлайн", "до ", "завтра", "сегодня", "недел", "месяц"),
                findQuestions(sentences),
                findImportantNumbers(sentences),
                findByKeywords(sentences, "решили", "решение", "согласовали", "утвердили", "договорились")
        );
    }

    public String formatForDisplay(MeetingSummary summary) {
        return "Краткое содержание:\n" + summary.getShortSummary()
                + "\n\nЗадачи:\n" + bulletList(summary.getTasks())
                + "\n\nОтветственные:\n" + bulletList(summary.getOwners())
                + "\n\nСроки:\n" + bulletList(summary.getDeadlines())
                + "\n\nОткрытые вопросы:\n" + bulletList(summary.getOpenQuestions())
                + "\n\nВажные цифры:\n" + bulletList(summary.getImportantNumbers())
                + "\n\nРешения:\n" + bulletList(summary.getDecisions());
    }

    private static List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.replace('\n', ' ').split("(?<=[.!?])\\s+")) {
            String sentence = part.trim();
            if (!sentence.isEmpty()) {
                result.add(sentence);
            }
        }
        return result;
    }

    private static List<String> findQuestions(List<String> sentences) {
        List<String> result = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence.contains("?")) {
                result.add(sentence);
            }
        }
        return limit(result);
    }

    private static List<String> findImportantNumbers(List<String> sentences) {
        List<String> result = new ArrayList<>();
        for (String sentence : sentences) {
            if (containsDigit(sentence)) {
                result.add(sentence);
            }
        }
        return limit(result);
    }

    private static boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> findByKeywords(List<String> sentences, String... keywords) {
        List<String> result = new ArrayList<>();
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    result.add(sentence);
                    break;
                }
            }
        }
        return limit(result);
    }

    private static List<String> limit(List<String> input) {
        return input.size() <= 5 ? input : new ArrayList<>(input.subList(0, 5));
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
}
