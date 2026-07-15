package com.forgptstas.neurorecorder;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.forgptstas.neurorecorder.modules.export.TxtExportModule;

public final class RecordingDetailsActivity extends Activity {
    public static final String EXTRA_ID = "recording_id";
    public static final String EXTRA_URI = "recording_uri";
    public static final String EXTRA_NAME = "recording_name";
    public static final String EXTRA_DATE_ADDED = "recording_date_added";
    public static final String EXTRA_DURATION = "recording_duration";
    public static final String EXTRA_SIZE = "recording_size";

    private RecordingItem item;
    private TranscriptStore transcriptStore;
    private SummaryStore summaryStore;
    private DiarizationStore diarizationStore;
    private TextView contentView;
    private TxtExportModule txtExportModule;
    private MediaPlayer mediaPlayer;
    private Button playButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        item = readRecordingItem();
        transcriptStore = new TranscriptStore(this);
        summaryStore = new SummaryStore(this);
        diarizationStore = new DiarizationStore(this);
        txtExportModule = new TxtExportModule();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText(item.getName());
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button audioTab = tabButton(getString(R.string.tab_audio));
        Button textTab = tabButton(getString(R.string.tab_text));
        Button speakersTab = tabButton(getString(R.string.tab_speakers));
        Button summaryTab = tabButton(getString(R.string.tab_summary));
        tabs.addView(audioTab);
        tabs.addView(textTab);
        tabs.addView(speakersTab);
        tabs.addView(summaryTab);
        root.addView(tabs);

        ScrollView scrollView = new ScrollView(this);
        contentView = new TextView(this);
        contentView.setTextSize(15);
        contentView.setPadding(0, dp(16), 0, dp(16));
        scrollView.addView(contentView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        playButton = tabButton(getString(R.string.play));
        playButton.setOnClickListener(view -> togglePlayback());
        root.addView(playButton);

        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        Button txtButton = tabButton(getString(R.string.export_txt));
        Button docxButton = tabButton(getString(R.string.export_docx));
        Button pdfButton = tabButton(getString(R.string.export_pdf));
        txtButton.setOnClickListener(view -> shareExport(
                "text/plain",
                R.string.share_transcript_txt,
                R.string.export_txt_error,
                text -> txtExportModule.exportTranscriptTxt(this, item.getName(), text)
        ));
        docxButton.setOnClickListener(view -> shareExport(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                R.string.share_transcript_docx,
                R.string.export_docx_error,
                text -> txtExportModule.exportTranscriptDocx(this, item.getName(), text)
        ));
        pdfButton.setOnClickListener(view -> shareExport(
                "application/pdf",
                R.string.share_transcript_pdf,
                R.string.export_pdf_error,
                text -> txtExportModule.exportTranscriptPdf(this, item.getName(), text)
        ));
        exportRow.addView(txtButton);
        exportRow.addView(docxButton);
        exportRow.addView(pdfButton);
        root.addView(exportRow);

        audioTab.setOnClickListener(view -> showAudio());
        textTab.setOnClickListener(view -> showText());
        speakersTab.setOnClickListener(view -> showSpeakers());
        summaryTab.setOnClickListener(view -> showSummary());

        setContentView(root);
        showAudio();
    }

    private RecordingItem readRecordingItem() {
        long id = getIntent().getLongExtra(EXTRA_ID, -1L);
        Uri uri = Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        String name = getIntent().getStringExtra(EXTRA_NAME);
        long dateAdded = getIntent().getLongExtra(EXTRA_DATE_ADDED, 0L);
        long duration = getIntent().getLongExtra(EXTRA_DURATION, 0L);
        long size = getIntent().getLongExtra(EXTRA_SIZE, 0L);
        return new RecordingItem(id, uri, name == null ? getString(R.string.app_name) : name, dateAdded, duration, size);
    }

    private void showAudio() {
        playButton.setVisibility(View.VISIBLE);
        contentView.setText(getString(R.string.details_audio_body, item.getName(), item.getUri().toString()));
    }

    private void showText() {
        playButton.setVisibility(View.GONE);
        String transcript = transcriptStore.load(item);
        contentView.setText(transcript == null || transcript.isBlank()
                ? getString(R.string.details_no_transcript)
                : transcript);
    }

    private void showSpeakers() {
        playButton.setVisibility(View.GONE);
        String speakers = diarizationStore.load(item);
        contentView.setText(speakers == null || speakers.isBlank()
                ? getString(R.string.details_no_speakers)
                : speakers);
    }

    private void showSummary() {
        playButton.setVisibility(View.GONE);
        String summary = summaryStore.load(item);
        contentView.setText(summary == null || summary.isBlank()
                ? getString(R.string.details_no_summary)
                : summary);
    }

    private void togglePlayback() {
        if (mediaPlayer != null) {
            stopPlayback();
            return;
        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, item.getUri());
            mediaPlayer.setOnPreparedListener(player -> {
                player.start();
                playButton.setText(R.string.pause);
            });
            mediaPlayer.setOnCompletionListener(player -> stopPlayback());
            mediaPlayer.prepareAsync();
        } catch (Exception exception) {
            stopPlayback();
            Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
        }
        mediaPlayer = null;
        if (playButton != null) {
            playButton.setText(R.string.play);
        }
    }

    private void shareExport(String mimeType, int chooserTitle, int errorMessage, DetailExporter exporter) {
        String transcript = transcriptStore.load(item);
        if (transcript == null || transcript.isBlank()) {
            Toast.makeText(this, R.string.export_txt_no_text, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = exporter.export(buildExportText(transcript));
            android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(share, getString(chooserTitle)));
        } catch (Exception exception) {
            Toast.makeText(this, getString(errorMessage) + " " + safeMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private String buildExportText(String transcript) {
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

    private interface DetailExporter {
        Uri export(String text) throws Exception;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private Button tabButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextAllCaps(false);
        button.setTextSize(12);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }
}
