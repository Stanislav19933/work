package com.forgptstas.neurorecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RecorderService extends Service {
    public static final String ACTION_START = "com.forgptstas.neurorecorder.action.START";
    public static final String ACTION_STOP = "com.forgptstas.neurorecorder.action.STOP";
    public static final String ACTION_QUERY = "com.forgptstas.neurorecorder.action.QUERY";
    public static final String ACTION_STATE = "com.forgptstas.neurorecorder.action.STATE";

    public static final String EXTRA_RECORDING = "recording";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "neuro_recorder_recording";
    private static final int NOTIFICATION_ID = 7;

    private MediaRecorder mediaRecorder;
    private boolean recording;
    private File temporaryFile;
    private String lastFileUri;
    private String lastFileName;

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

        File cacheDirectory = new File(getCacheDir(), "recordings");
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            broadcastState("Не удалось создать временную папку для записи.");
            stopSelf();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        lastFileName = "meeting_" + timestamp + ".m4a";
        temporaryFile = new File(cacheDirectory, lastFileName);
        lastFileUri = null;

        try {
            mediaRecorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new MediaRecorder(this)
                    : new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128_000);
            mediaRecorder.setAudioSamplingRate(44_100);
            mediaRecorder.setOutputFile(temporaryFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            recording = true;
            startForeground(NOTIFICATION_ID, buildNotification());
            broadcastState(null);
        } catch (IOException | RuntimeException exception) {
            releaseRecorder();
            deleteTemporaryFile();
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
        } finally {
            recording = false;
            releaseRecorder();
            stopForeground(STOP_FOREGROUND_REMOVE);
        }

        if (error == null) {
            try {
                lastFileUri = publishToMusicFolder();
            } catch (IOException exception) {
                error = "Не удалось сохранить запись в папку Music/NeuroRecorder: " + safeMessage(exception);
            }
        }

        deleteTemporaryFile();
        broadcastState(error);
        stopSelf();
    }

    private String publishToMusicFolder() throws IOException {
        if (temporaryFile == null || !temporaryFile.isFile() || temporaryFile.length() == 0) {
            throw new IOException("временный аудиофайл отсутствует");
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, lastFileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/NeuroRecorder");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore не создал файл");
        }

        boolean success = false;
        try (FileInputStream input = new FileInputStream(temporaryFile);
             OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("не удалось открыть файл назначения");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }

        ContentValues readyValues = new ContentValues();
        readyValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
        resolver.update(uri, readyValues, null, null);
        return uri.toString();
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
                .putExtra(EXTRA_FILE_URI, lastFileUri)
                .putExtra(EXTRA_FILE_NAME, lastFileName);
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

    private void deleteTemporaryFile() {
        if (temporaryFile != null && temporaryFile.exists()) {
            temporaryFile.delete();
        }
        temporaryFile = null;
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
            deleteTemporaryFile();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
