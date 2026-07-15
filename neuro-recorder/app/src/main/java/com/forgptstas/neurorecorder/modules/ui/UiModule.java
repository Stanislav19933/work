package com.forgptstas.neurorecorder.modules.ui;

/** Boundary for UI screens so app logic can move out of Activity classes step by step. */
public interface UiModule {
    void showRecordingScreen();

    void showArchiveScreen();

    void showRecordingDetails(long recordingId);
}
