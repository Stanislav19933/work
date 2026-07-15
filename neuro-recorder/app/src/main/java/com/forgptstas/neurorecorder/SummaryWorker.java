package com.forgptstas.neurorecorder;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.forgptstas.neurorecorder.modules.summary.LocalGemmaSummaryModule;
import com.forgptstas.neurorecorder.modules.summary.MeetingSummary;

public final class SummaryWorker extends Worker {
    public static final String KEY_ID = "recording_id";
    public static final String KEY_URI = "recording_uri";
    public static final String KEY_NAME = "recording_name";
    public static final String KEY_DATE_ADDED = "recording_date_added";
    public static final String KEY_DURATION = "recording_duration";
    public static final String KEY_SIZE = "recording_size";
    public static final String KEY_ERROR = "error";
    public static final String KEY_PROGRESS_STAGE = "progress_stage";
    public static final String KEY_PROGRESS_PERCENT = "progress_percent";

    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            RecordingItem item = readItem();
            Context context = getApplicationContext();
            TranscriptStore transcriptStore = new TranscriptStore(context);
            SummaryStore summaryStore = new SummaryStore(context);
            String transcript = transcriptStore.load(item);
            if (transcript == null || transcript.isBlank()) {
                return Result.failure(new Data.Builder().putString(KEY_ERROR, context.getString(R.string.summary_no_text)).build());
            }
            ModelFileManager modelFileManager = new ModelFileManager(context);
            LocalGemmaSummaryModule summaryModule = new LocalGemmaSummaryModule(context, modelFileManager);
            MeetingSummary summary = summaryModule.summarizeTranscript(transcript, (stage, percent) -> setProgressAsync(new Data.Builder()
                    .putString(KEY_PROGRESS_STAGE, stage)
                    .putInt(KEY_PROGRESS_PERCENT, percent)
                    .build()));
            summaryStore.save(item, summaryModule.formatForDisplay(summary));
            return Result.success();
        } catch (Exception exception) {
            return Result.failure(new Data.Builder().putString(KEY_ERROR, safeMessage(exception)).build());
        }
    }

    private RecordingItem readItem() {
        Data input = getInputData();
        return new RecordingItem(
                input.getLong(KEY_ID, -1L),
                Uri.parse(input.getString(KEY_URI)),
                input.getString(KEY_NAME),
                input.getLong(KEY_DATE_ADDED, 0L),
                input.getLong(KEY_DURATION, 0L),
                input.getLong(KEY_SIZE, 0L)
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
