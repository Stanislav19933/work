package com.forgptstas.neurorecorder.modules.models;

public final class ModelCandidate {
    public enum Task {
        ASR,
        VAD,
        DIARIZATION,
        SUMMARY
    }

    private final String id;
    private final Task task;
    private final String displayName;
    private final String language;
    private final String engine;
    private final boolean androidCandidate;

    public ModelCandidate(String id, Task task, String displayName, String language, String engine, boolean androidCandidate) {
        this.id = id;
        this.task = task;
        this.displayName = displayName;
        this.language = language;
        this.engine = engine;
        this.androidCandidate = androidCandidate;
    }

    public String getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguage() {
        return language;
    }

    public String getEngine() {
        return engine;
    }

    public boolean isAndroidCandidate() {
        return androidCandidate;
    }
}
