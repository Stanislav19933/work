package com.forgptstas.neurorecorder.modules.summary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LocalExtractiveSummaryModuleTest {
    private final LocalExtractiveSummaryModule module = new LocalExtractiveSummaryModule();

    @Test
    public void extractsStructuredMeetingFields() {
        MeetingSummary summary = module.summarizeTranscript(
                "Решили запустить тест 25 июля. "
                        + "Анна сделает отчёт до пятницы. "
                        + "Кто проверит бюджет 150000 рублей?"
        );

        assertFalse(summary.getShortSummary().isBlank());
        assertFalse(summary.getTasks().isEmpty());
        assertFalse(summary.getDeadlines().isEmpty());
        assertFalse(summary.getOpenQuestions().isEmpty());
        assertFalse(summary.getImportantNumbers().isEmpty());
        assertFalse(summary.getDecisions().isEmpty());
    }

    @Test
    public void formatsEmptyListsClearly() {
        String formatted = module.formatForDisplay(module.summarizeTranscript("Короткая встреча без задач."));
        assertTrue(formatted.contains("Краткое содержание"));
        assertTrue(formatted.contains("Не найдено"));
    }
}
