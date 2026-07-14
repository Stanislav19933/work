package com.forgptstas.neurorecorder;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private Button recordButton;
    private Button shareButton;
    private TextView statusText;
    private TextView transcriptText;
    private TextView summaryText;

    private boolean recording;
    private String lastRecordingUri;
    private String lastRecordingName;

    private final BroadcastReceiver recorderReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RecorderService.ACTION_STATE.equals(intent.getAction())) {
                return;
            }

            recording = intent.getBooleanExtra(RecorderService.EXTRA_RECORDING, false);
            String uri = intent.getStringExtra(RecorderService.EXTRA_FILE_URI);
            String fileName = intent.getStringExtra(RecorderService.EXTRA_FILE_NAME);
            String error = intent.getStringExtra(RecorderService.EXTRA_ERROR);

            if (uri != null && !uri.isBlank()) {
                lastRecordingUri = uri;
            }
            if (fileName != null && !fileName.isBlank()) {
                lastRecordingName = fileName;
            }

            if (error != null && !error.isBlank()) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }

            refreshUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = findViewById(R.id.recordButton);
        shareButton = findViewById(R.id.shareButton);
        statusText = findViewById(R.id.statusText);
        transcriptText = findViewById(R.id.transcriptText);
        summaryText = findViewById(R.id.summaryText);

        recordButton.setOnClickListener(view -> {
            if (recording) {
                stopRecording();
            } else {
                ensurePermissionsAndStart();
            }
        });
        shareButton.setOnClickListener(view -> shareLastRecording());

        lastRecordingUri = getPreferences(MODE_PRIVATE).getString("last_recording_uri", null);
        lastRecordingName = getPreferences(MODE_PRIVATE).getString("last_recording_name", null);
        refreshUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RecorderService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recorderReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(recorderReceiver, filter);
        }
        sendCommand(RecorderService.ACTION_QUERY);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(recorderReceiver);
        super.onStop();
    }

    private void ensurePermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }

        sendCommand(RecorderService.ACTION_START);
    }

    private void stopRecording() {
        sendCommand(RecorderService.ACTION_STOP);
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(this, RecorderService.class).setAction(action);
        try {
            if (RecorderService.ACTION_START.equals(action)) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception exception) {
            Toast.makeText(this, "Не удалось выполнить команду: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshUi() {
        recordButton.setEnabled(true);
        recordButton.setText(recording ? R.string.stop_recording : R.string.start_recording);
        statusText.setText(recording ? R.string.status_recording : R.string.status_ready);

        boolean hasRecording = lastRecordingUri != null && canReadUri(lastRecordingUri);
        shareButton.setEnabled(hasRecording && !recording);

        if (hasRecording) {
            getPreferences(MODE_PRIVATE).edit()
                    .putString("last_recording_uri", lastRecordingUri)
                    .putString("last_recording_name", lastRecordingName)
                    .apply();
            transcriptText.setText(getString(
                    R.string.recording_saved,
                    lastRecordingName == null ? getString(R.string.unknown_recording_name) : lastRecordingName,
                    "Music/NeuroRecorder"
            ));
            summaryText.setText(R.string.summary_next_version);
        } else {
            transcriptText.setText(R.string.empty_transcript);
            summaryText.setText(R.string.empty_summary);
        }
    }

    private boolean canReadUri(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            try (android.os.ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
                return descriptor != null;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private void shareLastRecording() {
        if (lastRecordingUri == null || !canReadUri(lastRecordingUri)) {
            Toast.makeText(this, R.string.recording_not_found, Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = Uri.parse(lastRecordingUri);
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.share_recording)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ensurePermissionsAndStart();
            return;
        }

        Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
        if (requestCode == REQUEST_RECORD_AUDIO && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(settingsIntent);
        }
    }
}
