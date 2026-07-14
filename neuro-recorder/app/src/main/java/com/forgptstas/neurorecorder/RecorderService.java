package com.forgptstas.neurorecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RecorderService extends Service {
    public static final String ACTION_START = "com.forgptstas.neurorecorder.action.START";
    public static final String ACTION_STOP = "com.forgptstas.neurorecorder.action.STOP";
    public static final String ACTION_QUERY = "com.forgptstas.neurorecorder.action.QUERY";
    public static final String ACTION_STATE = "com.forgptstas.neurorecorder.action.STATE";

    public static final String EXTRA_RECORDING = "recording";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "neuro_recorder_recording";
    private static final int NOTIFICATION_ID = 7;

    private MediaRecorder mediaRecorder;
    private boolean recording;
    private String currentFilePath;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_START.equals(action)) {
            startRecording();
        } else if (ACTION_STOP.equals(action)) {
            stopRecording();
        } else if (ACTION_QUERY.equals(action)) {
            broadcastState(null);
        }

        return recording ? START_STICKY : START_NOT_STICKY;
    }

    private void startRecording() {
        if (recording) {
            broadcastState(null);
            return;
        }

        File directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (directory == null) {
            broadcastState("Не удалось открыть папку для записей.");
            stopSelf();
            return;
        }

        if (!directory.exists() && !directory.mkdirs()) {
            broadcastState("Не удалось создать папку для записей.");
            stopSelf();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File outputFile = new File(directory, "meeting_" + timestamp + ".m4a");
        currentFilePath = outputFile.getAbsolutePath();

        try {
            mediaRecorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new MediaRecorder(this)
                    : new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128_000);
            mediaRecorder.setAudioSamplingRate(44_100);
            mediaRecorder.setOutputFile(currentFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            recording = true;
            startForeground(NOTIFICATION_ID, buildNotification());
            broadcastState(null);
        } catch (IOException | RuntimeException exception) {
            releaseRecorder();
            deleteEmptyOutput();
            broadcastState("Ошибка записи: " + safeMessage(exception));
            stopSelf();
        }
    }

    private void stopRecording() {
        if (!recording) {
            broadcastState(null);
            stopSelf();
            return;
        }

        String error = null;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException exception) {
            error = "Запись получилась слишком короткой или повреждена.";
            deleteEmptyOutput();
        } finally {
            recording = false;
            releaseRecorder();
            stopForeground(STOP_FOREGROUND_REMOVE);
        }

        broadcastState(error);
        stopSelf();
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null,
                        getString(R.string.stop_recording),
                        stopPendingIntent
                ).build())
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private void broadcastState(String error) {
        Intent state = new Intent(ACTION_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RECORDING, recording)
                .putExtra(EXTRA_FILE_PATH, currentFilePath);
        if (error != null) {
            state.putExtra(EXTRA_ERROR, error);
        }
        sendBroadcast(state);
    }

    private void releaseRecorder() {
        if (mediaRecorder == null) {
            return;
        }
        try {
            mediaRecorder.reset();
        } catch (RuntimeException ignored) {
        }
        mediaRecorder.release();
        mediaRecorder = null;
    }

    private void deleteEmptyOutput() {
        if (currentFilePath == null) {
            return;
        }
        File file = new File(currentFilePath);
        if (file.exists() && file.length() < 1024) {
            file.delete();
            currentFilePath = null;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @Override
    public void onDestroy() {
        if (recording) {
            stopRecording();
        } else {
            releaseRecorder();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
