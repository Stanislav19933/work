package com.forgptstas.neurorecorder.modules.summary;

import java.util.List;

public final class MeetingSummary {
    private final String shortSummary;
    private final List<String> tasks;
    private final List<String> owners;
    private final List<String> deadlines;
    private final List<String> openQuestions;
    private final List<String> importantNumbers;
    private final List<String> decisions;

    public MeetingSummary(
            String shortSummary,
            List<String> tasks,
            List<String> owners,
            List<String> deadlines,
            List<String> openQuestions,
            List<String> importantNumbers,
            List<String> decisions
    ) {
        this.shortSummary = shortSummary;
        this.tasks = tasks;
        this.owners = owners;
        this.deadlines = deadlines;
        this.openQuestions = openQuestions;
        this.importantNumbers = importantNumbers;
        this.decisions = decisions;
    }

    public String getShortSummary() {
        return shortSummary;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<String> getOwners() {
        return owners;
    }

    public List<String> getDeadlines() {
        return deadlines;
    }

    public List<String> getOpenQuestions() {
        return openQuestions;
    }

    public List<String> getImportantNumbers() {
        return importantNumbers;
    }

    public List<String> getDecisions() {
        return decisions;
    }
}
