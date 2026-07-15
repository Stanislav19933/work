package com.forgptstas.neurorecorder;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import com.forgptstas.neurorecorder.modules.models.ModelFileManager;

import java.io.File;
import java.util.Locale;

public final class ModelStatusActivity extends Activity {
    private static final ModelEntry[] MODELS = new ModelEntry[]{
            new ModelEntry(
                    "Gemma 3 1B summary SLM",
                    "summary/gemma3-1b-it-int4.task",
                    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                    200L * 1024L * 1024L
            ),
            new ModelEntry(
                    "GigaAM CTC v2 ASR",
                    "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/model.int8.onnx",
                    "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/resolve/main/model.int8.onnx",
                    200L * 1024L * 1024L
            ),
            new ModelEntry(
                    "GigaAM tokens",
                    "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/tokens.txt",
                    "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/resolve/main/tokens.txt",
                    100L
            ),
            new ModelEntry(
                    "Silero VAD",
                    "silero-vad/silero_vad.onnx",
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
                    1L * 1024L * 1024L
            ),
            new ModelEntry(
                    "Diarization segmentation",
                    "speaker-diarization/pyannote-segmentation-3-0/model.int8.onnx",
                    "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.int8.onnx",
                    4L * 1024L * 1024L
            ),
            new ModelEntry(
                    "Diarization embeddings",
                    "speaker-diarization/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
                    20L * 1024L * 1024L
            )
    };

    private ModelFileManager modelFileManager;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modelFileManager = new ModelFileManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText(R.string.models_title);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.models_hint);
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(12));
        root.addView(hint);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        android.widget.Button downloadAll = new android.widget.Button(this);
        downloadAll.setText(R.string.models_download_all);
        downloadAll.setTextAllCaps(false);
        downloadAll.setOnClickListener(view -> downloadAllModels());
        root.addView(downloadAll);

        android.widget.Button clear = new android.widget.Button(this);
        clear.setText(R.string.models_clear);
        clear.setTextAllCaps(false);
        clear.setOnClickListener(view -> {
            modelFileManager.clearAll();
            Toast.makeText(this, R.string.models_cleared, Toast.LENGTH_SHORT).show();
            render();
        });
        root.addView(clear);

        android.widget.Button refresh = new android.widget.Button(this);
        refresh.setText(R.string.models_refresh);
        refresh.setTextAllCaps(false);
        refresh.setOnClickListener(view -> render());
        root.addView(refresh);

        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        long totalBytes = 0;
        int readyCount = 0;
        for (ModelEntry model : MODELS) {
            File file = modelFileManager.modelFile(model.path);
            boolean ready = modelFileManager.isReady(model.path, model.minBytes);
            if (ready) {
                readyCount++;
            }
            totalBytes += Math.max(file.length(), 0L);
            list.addView(row(model.name, ready, file.length()));
        }
        list.addView(row(getString(R.string.models_total), readyCount == MODELS.length, totalBytes));
    }

    private TextView row(String name, boolean ready, long bytes) {
        TextView view = new TextView(this);
        view.setText(String.format(
                Locale.getDefault(),
                "%s\n%s • %s",
                name,
                ready ? getString(R.string.models_ready) : getString(R.string.models_missing),
                formatSize(bytes)
        ));
        view.setTextSize(15);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private void downloadAllModels() {
        Toast.makeText(this, R.string.models_download_started, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                for (ModelEntry model : MODELS) {
                    modelFileManager.ensureDownloaded(model.path, model.url, model.minBytes, percent -> { });
                    runOnUiThread(this::render);
                }
                runOnUiThread(() -> Toast.makeText(this, R.string.models_download_done, Toast.LENGTH_LONG).show());
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.models_download_error) + " " + safeMessage(exception), Toast.LENGTH_LONG).show());
            }
        }, "model-download-all").start();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ModelEntry {
        private final String name;
        private final String path;
        private final String url;
        private final long minBytes;

        private ModelEntry(String name, String path, String url, long minBytes) {
            this.name = name;
            this.path = path;
            this.url = url;
            this.minBytes = minBytes;
        }
    }
}
