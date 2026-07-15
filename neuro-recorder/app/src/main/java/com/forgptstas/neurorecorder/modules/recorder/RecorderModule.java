package com.forgptstas.neurorecorder.modules.recorder;

/**
 * Replaceable boundary for microphone recording.
 * Implementations may use AudioRecord, a test fake, or another Android recorder.
 */
public interface RecorderModule {
    void startRecording();

    void stopRecording();

    boolean isRecording();
}
