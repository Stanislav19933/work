package com.forgptstas.neurorecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private Button recordButton;
    private TextView statusText;
    private TextView archiveEmptyText;
    private LinearLayout archiveContainer;
    private RecordingRepository recordingRepository;

    private boolean recording;
    private MediaPlayer mediaPlayer;
    private Uri playingUri;
    private Button playingButton;
    private SeekBar playingSeekBar;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());

    private final Runnable playbackProgress = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && playingSeekBar != null) {
                try {
                    playingSeekBar.setMax(Math.max(mediaPlayer.getDuration(), 1));
                    playingSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                    if (mediaPlayer.isPlaying()) {
                        playbackHandler.postDelayed(this, 300);
                    }
                } catch (IllegalStateException ignored) {
                    stopPlayback();
                }
            }
        }
    };

    private final BroadcastReceiver recorderReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RecorderService.ACTION_STATE.equals(intent.getAction())) {
                return;
            }

            recording = intent.getBooleanExtra(RecorderService.EXTRA_RECORDING, false);
            String error = intent.getStringExtra(RecorderService.EXTRA_ERROR);
            if (error != null && !error.isBlank()) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }

            refreshRecordingUi();
            if (!recording) {
                loadArchive();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = findViewById(R.id.recordButton);
        statusText = findViewById(R.id.statusText);
        archiveEmptyText = findViewById(R.id.archiveEmptyText);
        archiveContainer = findViewById(R.id.archiveContainer);
        recordingRepository = new RecordingRepository(this);

        recordButton.setOnClickListener(view -> {
            if (recording) {
                sendCommand(RecorderService.ACTION_STOP);
            } else {
                ensurePermissionsAndStart();
            }
        });

        refreshRecordingUi();
        loadArchive();
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
        loadArchive();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(recorderReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        super.onDestroy();
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

        stopPlayback();
        sendCommand(RecorderService.ACTION_START);
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

    private void refreshRecordingUi() {
        recordButton.setEnabled(true);
        recordButton.setText(recording ? R.string.stop_recording : R.string.start_recording);
        statusText.setText(recording ? R.string.status_recording : R.string.status_ready);
    }

    private void loadArchive() {
        new Thread(() -> {
            List<RecordingItem> items = recordingRepository.loadAll();
            runOnUiThread(() -> renderArchive(items));
        }, "recording-archive-loader").start();
    }

    private void renderArchive(List<RecordingItem> items) {
        archiveContainer.removeAllViews();
        archiveEmptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (RecordingItem item : items) {
            archiveContainer.addView(createRecordingCard(item));
        }
    }

    private View createRecordingCard(RecordingItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        card.setBackground(background);

        TextView title = new TextView(this);
        title.setText(item.getName());
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView metadata = new TextView(this);
        metadata.setText(formatMetadata(item));
        metadata.setTextColor(Color.rgb(75, 85, 99));
        metadata.setTextSize(13);
        metadata.setPadding(0, dp(5), 0, dp(8));
        card.addView(metadata);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax((int) Math.max(item.getDurationMillis(), 1));
        seekBar.setProgress(0);
        card.addView(seekBar);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button playButton = compactButton(getString(R.string.play));
        Button renameButton = compactButton(getString(R.string.rename));
        Button shareButton = compactButton(getString(R.string.share));
        Button deleteButton = compactButton(getString(R.string.delete));

        actions.addView(playButton);
        actions.addView(renameButton);
        actions.addView(shareButton);
        actions.addView(deleteButton);
        card.addView(actions);

        playButton.setOnClickListener(view -> togglePlayback(item, playButton, seekBar));
        renameButton.setOnClickListener(view -> showRenameDialog(item));
        shareButton.setOnClickListener(view -> shareRecording(item));
        deleteButton.setOnClickListener(view -> confirmDelete(item));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && item.getUri().equals(playingUri)) {
                    try {
                        mediaPlayer.seekTo(progress);
                    } catch (IllegalStateException ignored) {
                        stopPlayback();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        return card;
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextAllCaps(false);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void togglePlayback(RecordingItem item, Button button, SeekBar seekBar) {
        if (mediaPlayer != null && item.getUri().equals(playingUri)) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                button.setText(R.string.play);
                playbackHandler.removeCallbacks(playbackProgress);
            } else {
                mediaPlayer.start();
                button.setText(R.string.pause);
                playbackHandler.post(playbackProgress);
            }
            return;
        }

        stopPlayback();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, item.getUri());
            mediaPlayer.setOnPreparedListener(player -> {
                seekBar.setMax(Math.max(player.getDuration(), 1));
                player.start();
                button.setText(R.string.pause);
                playbackHandler.post(playbackProgress);
            });
            mediaPlayer.setOnCompletionListener(player -> stopPlayback());
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show();
                stopPlayback();
                return true;
            });
            playingUri = item.getUri();
            playingButton = button;
            playingSeekBar = seekBar;
            mediaPlayer.prepareAsync();
        } catch (Exception exception) {
            stopPlayback();
            Toast.makeText(this, "Не удалось открыть запись: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopPlayback() {
        playbackHandler.removeCallbacks(playbackProgress);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
        }
        if (playingButton != null) {
            playingButton.setText(R.string.play);
        }
        if (playingSeekBar != null) {
            playingSeekBar.setProgress(0);
        }
        mediaPlayer = null;
        playingUri = null;
        playingButton = null;
        playingSeekBar = null;
    }

    private void showRenameDialog(RecordingItem item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(stripExtension(item.getName()));
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_recording)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    stopPlayback();
                    boolean renamed = recordingRepository.rename(item, input.getText().toString());
                    Toast.makeText(this, renamed ? R.string.rename_success : R.string.rename_error, Toast.LENGTH_SHORT).show();
                    loadArchive();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(RecordingItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_recording)
                .setMessage(getString(R.string.delete_confirmation, item.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    stopPlayback();
                    boolean deleted = recordingRepository.delete(item);
                    Toast.makeText(this, deleted ? R.string.delete_success : R.string.delete_error, Toast.LENGTH_SHORT).show();
                    loadArchive();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareRecording(RecordingItem item) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, item.getUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.share_recording)));
    }

    private String formatMetadata(RecordingItem item) {
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(item.getDateAddedSeconds() * 1000L));
        return date + "  •  " + formatDuration(item.getDurationMillis()) + "  •  " + formatSize(item.getSizeBytes());
    }

    private static String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(milliseconds, 0) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
