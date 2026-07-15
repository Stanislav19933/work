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

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import com.forgptstas.neurorecorder.modules.storage.StorageModule;
import com.forgptstas.neurorecorder.modules.recorder.AndroidRecorderModule;
import com.forgptstas.neurorecorder.modules.export.TxtExportModule;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private Button recordButton;
    private TextView statusText;
    private TextView archiveEmptyText;
    private Button modelsButton;
    private LinearLayout archiveContainer;
    private StorageModule storageModule;
    private AndroidRecorderModule recorderModule;
    private TranscriptStore transcriptStore;
    private SummaryStore summaryStore;
    private DiarizationStore diarizationStore;
    private TxtExportModule txtExportModule;

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
            recorderModule.setRecordingState(recording);
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
        modelsButton = findViewById(R.id.modelsButton);
        archiveContainer = findViewById(R.id.archiveContainer);
        storageModule = new RecordingRepository(this);
        recorderModule = new AndroidRecorderModule(this);
        transcriptStore = new TranscriptStore(this);
        summaryStore = new SummaryStore(this);
        diarizationStore = new DiarizationStore(this);
        txtExportModule = new TxtExportModule();

        modelsButton.setOnClickListener(view -> startActivity(new Intent(this, ModelStatusActivity.class)));

        recordButton.setOnClickListener(view -> {
            if (recording) {
                stopRecording();
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
        queryRecordingState();
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
        startRecording();
    }

    private void startRecording() {
        try {
            recorderModule.startRecording();
        } catch (Exception exception) {
            Toast.makeText(this, "Не удалось начать запись: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        try {
            recorderModule.stopRecording();
        } catch (Exception exception) {
            Toast.makeText(this, "Не удалось остановить запись: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void queryRecordingState() {
        try {
            recorderModule.queryRecordingState();
        } catch (Exception ignored) {
            // Сервис мог ещё не быть запущен — UI останется в состоянии Ready.
        }
    }

    private void refreshRecordingUi() {
        recordButton.setEnabled(true);
        recordButton.setText(recording ? R.string.stop_recording : R.string.start_recording);
        statusText.setText(recording ? R.string.status_recording : R.string.status_ready);
    }

    private void loadArchive() {
        new Thread(() -> {
            List<RecordingItem> items = storageModule.loadRecordings();
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
        title.setOnClickListener(view -> openRecordingDetails(item));
        card.addView(title);

        TextView metadata = new TextView(this);
        metadata.setText(formatMetadata(item));
        metadata.setTextColor(Color.rgb(75, 85, 99));
        metadata.setTextSize(13);
        metadata.setPadding(0, dp(5), 0, dp(8));
        card.addView(metadata);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax((int) Math.max(item.getDurationMillis(), 1));
        card.addView(seekBar);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button playButton = compactButton(getString(R.string.play));
        Button transcribeButton = compactButton(getString(R.string.transcribe));
        Button renameButton = compactButton(getString(R.string.rename));
        Button summaryButton = compactButton(getString(R.string.summary));
        Button shareButton = compactButton(getString(R.string.share));
        actions.addView(playButton);
        actions.addView(transcribeButton);
        actions.addView(renameButton);
        actions.addView(summaryButton);
        actions.addView(shareButton);
        card.addView(actions);

        Button deleteButton = compactButton(getString(R.string.delete));
        Button exportTxtButton = compactButton(getString(R.string.export_txt));
        Button exportDocxButton = compactButton(getString(R.string.export_docx));
        Button exportPdfButton = compactButton(getString(R.string.export_pdf));
        LinearLayout deleteRow = new LinearLayout(this);
        deleteRow.addView(exportTxtButton);
        deleteRow.addView(exportDocxButton);
        deleteRow.addView(exportPdfButton);
        deleteRow.addView(deleteButton);
        card.addView(deleteRow);

        TextView transcriptView = new TextView(this);
        transcriptView.setTextColor(Color.rgb(31, 41, 55));
        transcriptView.setTextSize(14);
        transcriptView.setPadding(0, dp(10), 0, 0);
        String savedTranscript = transcriptStore.load(item);
        if (savedTranscript == null || savedTranscript.isBlank()) {
            transcriptView.setVisibility(View.GONE);
        } else {
            transcriptView.setText(savedTranscript);
            transcriptView.setVisibility(View.VISIBLE);
            transcribeButton.setText(R.string.transcribe_again);
        }
        card.addView(transcriptView);

        TextView summaryView = new TextView(this);
        summaryView.setTextColor(Color.rgb(31, 41, 55));
        summaryView.setTextSize(14);
        summaryView.setPadding(0, dp(10), 0, 0);
        String savedSummary = summaryStore.load(item);
        if (savedSummary == null || savedSummary.isBlank()) {
            summaryView.setVisibility(View.GONE);
        } else {
            summaryView.setText(savedSummary);
            summaryView.setVisibility(View.VISIBLE);
        }
        card.addView(summaryView);

        playButton.setOnClickListener(view -> togglePlayback(item, playButton, seekBar));
        transcribeButton.setOnClickListener(view -> transcribe(item, transcribeButton, transcriptView));
        renameButton.setOnClickListener(view -> showRenameDialog(item));
        summaryButton.setOnClickListener(view -> summarize(item, summaryButton, summaryView));
        shareButton.setOnClickListener(view -> shareRecording(item));
        exportTxtButton.setOnClickListener(view -> shareTranscriptTxt(item));
        exportDocxButton.setOnClickListener(view -> shareTranscriptDocx(item));
        exportPdfButton.setOnClickListener(view -> shareTranscriptPdf(item));
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
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        return card;
    }

    private void openRecordingDetails(RecordingItem item) {
        Intent intent = new Intent(this, RecordingDetailsActivity.class)
                .putExtra(RecordingDetailsActivity.EXTRA_ID, item.getId())
                .putExtra(RecordingDetailsActivity.EXTRA_URI, item.getUri().toString())
                .putExtra(RecordingDetailsActivity.EXTRA_NAME, item.getName())
                .putExtra(RecordingDetailsActivity.EXTRA_DATE_ADDED, item.getDateAddedSeconds())
                .putExtra(RecordingDetailsActivity.EXTRA_DURATION, item.getDurationMillis())
                .putExtra(RecordingDetailsActivity.EXTRA_SIZE, item.getSizeBytes());
        startActivity(intent);
    }

    private void transcribe(RecordingItem item, Button button, TextView transcriptView) {
        stopPlayback();
        button.setEnabled(false);
        button.setText(R.string.transcribing);
        transcriptView.setVisibility(View.VISIBLE);
        transcriptView.setText(R.string.processing_queued);

        Data input = new Data.Builder()
                .putLong(TranscriptionWorker.KEY_ID, item.getId())
                .putString(TranscriptionWorker.KEY_URI, item.getUri().toString())
                .putString(TranscriptionWorker.KEY_NAME, item.getName())
                .putLong(TranscriptionWorker.KEY_DATE_ADDED, item.getDateAddedSeconds())
                .putLong(TranscriptionWorker.KEY_DURATION, item.getDurationMillis())
                .putLong(TranscriptionWorker.KEY_SIZE, item.getSizeBytes())
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TranscriptionWorker.class)
                .setInputData(input)
                .addTag("transcription")
                .build();
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniqueWork("transcription-" + item.getId(), ExistingWorkPolicy.REPLACE, request);
        watchTranscriptionWork(workManager, request, item, button, transcriptView);
    }

    private void watchTranscriptionWork(
            WorkManager workManager,
            OneTimeWorkRequest request,
            RecordingItem item,
            Button button,
            TextView transcriptView
    ) {
        playbackHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        WorkInfo workInfo = workManager.getWorkInfoById(request.getId()).get();
                        runOnUiThread(() -> handleTranscriptionWorkInfo(workInfo, workManager, request, item, button, transcriptView, this));
                    } catch (Exception exception) {
                        runOnUiThread(() -> {
                            button.setEnabled(true);
                            button.setText(R.string.transcribe);
                            transcriptView.setText("Ошибка расшифровки: " + safeMessage(exception));
                        });
                    }
                }, "transcription-work-watch").start();
            }
        }, 600);
    }

    private void handleTranscriptionWorkInfo(
            WorkInfo workInfo,
            WorkManager workManager,
            OneTimeWorkRequest request,
            RecordingItem item,
            Button button,
            TextView transcriptView,
            Runnable watcher
    ) {
        if (workInfo == null) {
            playbackHandler.postDelayed(watcher, 600);
            return;
        }
        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
            String text = transcriptStore.load(item);
            transcriptView.setText(text == null || text.isBlank() ? getString(R.string.details_no_transcript) : text);
            button.setEnabled(true);
            button.setText(R.string.transcribe_again);
            return;
        }
        if (workInfo.getState() == WorkInfo.State.FAILED || workInfo.getState() == WorkInfo.State.CANCELLED) {
            String error = workInfo.getOutputData().getString(TranscriptionWorker.KEY_ERROR);
            transcriptView.setText("Ошибка расшифровки: " + (error == null ? workInfo.getState().name() : error));
            button.setEnabled(true);
            button.setText(R.string.transcribe);
            return;
        }
        Data progress = workInfo.getProgress();
        updateTranscriptionProgress(progress.getString(TranscriptionWorker.KEY_MESSAGE), progress.getInt(TranscriptionWorker.KEY_PERCENT, 0), button, transcriptView);
        playbackHandler.postDelayed(watcher, 800);
    }

    private void updateTranscriptionProgress(String message, int percent, Button button, TextView transcriptView) {
        if ("model".equals(message)) {
            button.setText(getString(R.string.model_download_progress, percent));
            transcriptView.setText(getString(R.string.model_download_progress_long, percent));
        } else if ("vad_model".equals(message)) {
            button.setText(getString(R.string.model_download_progress, percent));
            transcriptView.setText("Скачиваю модель определения речи: " + percent + "%");
        } else if ("audio".equals(message)) {
            button.setText(R.string.transcribing);
            transcriptView.setText(R.string.audio_preparing);
        } else if ("vad".equals(message)) {
            transcriptView.setText("Убираю тишину перед расшифровкой: " + percent + "%");
        } else if ("diarization_model".equals(message)) {
            button.setText(getString(R.string.model_download_progress, percent));
            transcriptView.setText("Скачиваю модели разделения говорящих: " + percent + "%");
        } else if ("diarization".equals(message)) {
            transcriptView.setText("Разделяю говорящих: " + percent + "%");
        } else if ("asr".equals(message)) {
            transcriptView.setText(R.string.whisper_processing);
        }
    }

    private void summarize(RecordingItem item, Button button, TextView summaryView) {
        String transcript = transcriptStore.load(item);
        if (transcript == null || transcript.isBlank()) {
            Toast.makeText(this, R.string.summary_no_text, Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        button.setText(R.string.summary_processing);
        summaryView.setVisibility(View.VISIBLE);
        summaryView.setText(R.string.summary_processing);

        Data input = new Data.Builder()
                .putLong(SummaryWorker.KEY_ID, item.getId())
                .putString(SummaryWorker.KEY_URI, item.getUri().toString())
                .putString(SummaryWorker.KEY_NAME, item.getName())
                .putLong(SummaryWorker.KEY_DATE_ADDED, item.getDateAddedSeconds())
                .putLong(SummaryWorker.KEY_DURATION, item.getDurationMillis())
                .putLong(SummaryWorker.KEY_SIZE, item.getSizeBytes())
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SummaryWorker.class)
                .setInputData(input)
                .addTag("summary")
                .build();
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniqueWork("summary-" + item.getId(), ExistingWorkPolicy.REPLACE, request);
        watchSummaryWork(workManager, request, item, button, summaryView);
    }

    private void watchSummaryWork(
            WorkManager workManager,
            OneTimeWorkRequest request,
            RecordingItem item,
            Button button,
            TextView summaryView
    ) {
        playbackHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        WorkInfo workInfo = workManager.getWorkInfoById(request.getId()).get();
                        runOnUiThread(() -> handleSummaryWorkInfo(workInfo, workManager, request, item, button, summaryView, this));
                    } catch (Exception exception) {
                        runOnUiThread(() -> {
                            button.setEnabled(true);
                            button.setText(R.string.summary);
                            summaryView.setText(getString(R.string.summary_error) + " " + safeMessage(exception));
                        });
                    }
                }, "summary-work-watch").start();
            }
        }, 500);
    }

    private void handleSummaryWorkInfo(
            WorkInfo workInfo,
            WorkManager workManager,
            OneTimeWorkRequest request,
            RecordingItem item,
            Button button,
            TextView summaryView,
            Runnable watcher
    ) {
        if (workInfo == null || workInfo.getState() == WorkInfo.State.RUNNING || workInfo.getState() == WorkInfo.State.ENQUEUED) {
            Data progress = workInfo == null ? Data.EMPTY : workInfo.getProgress();
            int percent = progress.getInt(SummaryWorker.KEY_PROGRESS_PERCENT, -1);
            String stage = progress.getString(SummaryWorker.KEY_PROGRESS_STAGE);
            if (percent >= 0) {
                if ("summary_model".equals(stage)) {
                    button.setText(getString(R.string.model_download_progress, percent));
                    summaryView.setText("Скачиваю локальную SLM-модель для саммари: " + percent + "%");
                } else if ("summary".equals(stage)) {
                    summaryView.setText("Локальная SLM-модель готовит саммари: " + percent + "%");
                }
            }
            playbackHandler.postDelayed(watcher, 700);
            return;
        }
        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
            String summary = summaryStore.load(item);
            summaryView.setText(summary == null || summary.isBlank() ? getString(R.string.details_no_summary) : summary);
            button.setEnabled(true);
            button.setText(R.string.summary);
            return;
        }
        String error = workInfo.getOutputData().getString(SummaryWorker.KEY_ERROR);
        summaryView.setText(getString(R.string.summary_error) + " " + (error == null ? workInfo.getState().name() : error));
        button.setEnabled(true);
        button.setText(R.string.summary);
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextAllCaps(false);
        button.setTextSize(11);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), dp(5), dp(6), dp(5));
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
            Toast.makeText(this, "Не удалось открыть запись: " + safeMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private void stopPlayback() {
        playbackHandler.removeCallbacks(playbackProgress);
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (IllegalStateException ignored) { }
            mediaPlayer.release();
        }
        if (playingButton != null) playingButton.setText(R.string.play);
        if (playingSeekBar != null) playingSeekBar.setProgress(0);
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
                    boolean renamed = storageModule.renameRecording(item, input.getText().toString());
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
                    boolean deleted = storageModule.deleteRecording(item);
                    if (deleted) {
                        transcriptStore.delete(item);
                        summaryStore.delete(item);
                        diarizationStore.delete(item);
                    }
                    Toast.makeText(this, deleted ? R.string.delete_success : R.string.delete_error, Toast.LENGTH_SHORT).show();
                    loadArchive();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareTranscriptTxt(RecordingItem item) {
        shareTranscriptExport(
                item,
                "text/plain",
                R.string.share_transcript_txt,
                R.string.export_txt_error,
                transcript -> txtExportModule.exportTranscriptTxt(this, item.getName(), transcript)
        );
    }

    private void shareTranscriptDocx(RecordingItem item) {
        shareTranscriptExport(
                item,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                R.string.share_transcript_docx,
                R.string.export_docx_error,
                transcript -> txtExportModule.exportTranscriptDocx(this, item.getName(), transcript)
        );
    }

    private void shareTranscriptPdf(RecordingItem item) {
        shareTranscriptExport(
                item,
                "application/pdf",
                R.string.share_transcript_pdf,
                R.string.export_pdf_error,
                transcript -> txtExportModule.exportTranscriptPdf(this, item.getName(), transcript)
        );
    }

    private void shareTranscriptExport(
            RecordingItem item,
            String mimeType,
            int chooserTitle,
            int errorMessage,
            TranscriptExporter exporter
    ) {
        String transcript = transcriptStore.load(item);
        if (transcript == null || transcript.isBlank()) {
            Toast.makeText(this, R.string.export_txt_no_text, Toast.LENGTH_SHORT).show();
            return;
        }
        String exportText = buildExportText(item, transcript);
        try {
            Uri uri = exporter.export(exportText);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(chooserTitle)));
        } catch (Exception exception) {
            Toast.makeText(this, getString(errorMessage) + " " + safeMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private String buildExportText(RecordingItem item, String transcript) {
        StringBuilder builder = new StringBuilder();
        builder.append(item.getName()).append("\n\n");
        String summary = summaryStore.load(item);
        if (summary != null && !summary.isBlank()) {
            builder.append("Саммари встречи\n")
                    .append(summary)
                    .append("\n\n");
        }
        String speakers = diarizationStore.load(item);
        if (speakers != null && !speakers.isBlank()) {
            builder.append("По участникам\n")
                    .append(speakers)
                    .append("\n");
        }
        builder.append("Расшифровка\n")
                .append(transcript);
        return builder.toString();
    }

    private interface TranscriptExporter {
        Uri export(String transcript) throws Exception;
    }

    private void shareRecording(RecordingItem item) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("audio/wav")
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
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
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
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }
}
