package com.forgptstas.neurorecorder.modules.recorder;

import android.content.Context;
import android.content.Intent;

import com.forgptstas.neurorecorder.RecorderService;

/** Android foreground-service recorder implementation backed by RecorderService. */
public final class AndroidRecorderModule implements RecorderModule {
    private final Context appContext;
    private volatile boolean recording;

    public AndroidRecorderModule(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public void startRecording() {
        Intent intent = new Intent(appContext, RecorderService.class).setAction(RecorderService.ACTION_START);
        appContext.startForegroundService(intent);
    }

    @Override
    public void stopRecording() {
        sendServiceCommand(RecorderService.ACTION_STOP);
    }

    public void queryRecordingState() {
        sendServiceCommand(RecorderService.ACTION_QUERY);
    }

    @Override
    public boolean isRecording() {
        return recording;
    }

    public void setRecordingState(boolean recording) {
        this.recording = recording;
    }

    private void sendServiceCommand(String action) {
        Intent intent = new Intent(appContext, RecorderService.class).setAction(action);
        appContext.startService(intent);
    }
}
