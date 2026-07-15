package com.forgptstas.neurorecorder.modules.names;

import com.forgptstas.neurorecorder.Utterance;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Context-based speaker name recognition without regex-only matching. */
public final class ContextNameRecognitionModule implements NameRecognitionModule {
    @Override
    public Map<String, String> detectSpeakerNames(List<Utterance> utterances) {
        Map<String, String> names = new HashMap<>();
        if (utterances == null) {
            return names;
        }
        for (Utterance utterance : utterances) {
            String detected = detectSelfIntroduction(utterance.getText());
            if (detected != null) {
                names.put(String.valueOf(utterance.getSpeakerId()), detected);
            }
        }
        return names;
    }

    public String detectSelfIntroduction(String text) {
        List<String> tokens = tokenize(text);
        for (int i = 0; i < tokens.size(); i++) {
            String current = lower(tokens.get(i));
            if ("зовут".equals(current) && i > 0 && isSelfReference(tokens.get(i - 1))) {
                return nextName(tokens, i + 1);
            }
            if (("имя".equals(current) || "звать".equals(current)) && hasPrevious(tokens, i, "мое", "моё")) {
                return nextName(tokens, i + 1);
            }
            if ("я".equals(current)) {
                String next = nextName(tokens, i + 1);
                if (next != null && !isCommonVerb(lower(next))) {
                    return next;
                }
            }
            if ("это".equals(current) && i == 0) {
                return nextName(tokens, i + 1);
            }
        }
        return null;
    }

    private static boolean hasPrevious(List<String> tokens, int index, String first, String second) {
        if (index == 0) {
            return false;
        }
        String previous = lower(tokens.get(index - 1));
        return first.equals(previous) || second.equals(previous);
    }

    private static boolean isSelfReference(String token) {
        String lower = lower(token);
        return "меня".equals(lower) || "мне".equals(lower) || "нас".equals(lower);
    }

    private static String nextName(List<String> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size(); i++) {
            String token = cleanupName(tokens.get(i));
            if (token == null) {
                continue;
            }
            String lower = lower(token);
            if (isStopWord(lower) || isCommonVerb(lower)) {
                continue;
            }
            if (Character.isUpperCase(token.charAt(0)) || isLikelyRussianName(token)) {
                return capitalize(token);
            }
        }
        return null;
    }

    private static List<String> tokenize(String text) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '-') {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String cleanupName(String token) {
        if (token == null || token.length() < 2) {
            return null;
        }
        return token;
    }

    private static boolean isStopWord(String value) {
        return "как".equals(value)
                || "и".equals(value)
                || "а".equals(value)
                || "ну".equals(value)
                || "вот".equals(value)
                || "значит".equals(value);
    }

    private static boolean isCommonVerb(String value) {
        return "буду".equals(value)
                || "сделаю".equals(value)
                || "могу".equals(value)
                || "хочу".equals(value)
                || "думаю".equals(value)
                || "предлагаю".equals(value)
                || "считаю".equals(value)
                || "проверю".equals(value)
                || "отправлю".equals(value);
    }

    private static boolean isLikelyRussianName(String token) {
        String lower = lower(token);
        return lower.endsWith("ей")
                || lower.endsWith("ий")
                || lower.endsWith("ан")
                || lower.endsWith("на")
                || lower.endsWith("ия")
                || lower.endsWith("ла")
                || lower.endsWith("ир")
                || lower.endsWith("ор")
                || lower.endsWith("им")
                || lower.endsWith("рт");
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
