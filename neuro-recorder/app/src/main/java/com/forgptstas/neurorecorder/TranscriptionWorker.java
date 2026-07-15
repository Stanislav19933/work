package com.forgptstas.neurorecorder;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.forgptstas.neurorecorder.modules.asr.RecognitionResult;
import com.forgptstas.neurorecorder.modules.asr.SherpaGigaAmAsrModule;
import com.forgptstas.neurorecorder.modules.diarization.SherpaSpeakerDiarizationModule;
import com.forgptstas.neurorecorder.modules.models.ModelFileManager;
import com.forgptstas.neurorecorder.modules.names.ContextNameRecognitionModule;
import com.forgptstas.neurorecorder.modules.vad.SileroVadModule;

import java.util.Map;

public final class TranscriptionWorker extends Worker {
    public static final String KEY_ID = "recording_id";
    public static final String KEY_URI = "recording_uri";
    public static final String KEY_NAME = "recording_name";
    public static final String KEY_DATE_ADDED = "recording_date_added";
    public static final String KEY_DURATION = "recording_duration";
    public static final String KEY_SIZE = "recording_size";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_PERCENT = "percent";
    public static final String KEY_ERROR = "error";

    public TranscriptionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
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
            DiarizationStore diarizationStore = new DiarizationStore(context);
            ModelFileManager modelFileManager = new ModelFileManager(context);
            SileroVadModule vadModule = new SileroVadModule(modelFileManager);
            SherpaSpeakerDiarizationModule diarizationModule = new SherpaSpeakerDiarizationModule(modelFileManager);
            SherpaGigaAmAsrModule asrModule = new SherpaGigaAmAsrModule(modelFileManager, vadModule, diarizationModule);
            ContextNameRecognitionModule nameRecognitionModule = new ContextNameRecognitionModule();

            RecognitionResult result = asrModule.transcribe(context, item.getUri(), (message, percent) -> {
                setProgressAsync(new Data.Builder()
                        .putString(KEY_MESSAGE, message)
                        .putInt(KEY_PERCENT, percent)
                        .build());
            });
            String text = result.getPlainText();
            transcriptStore.save(item, text);
            Map<String, String> speakerNames = nameRecognitionModule.detectSpeakerNames(result.getUtterances());
            diarizationStore.save(item, result.getUtterances(), speakerNames);
            summaryStore.delete(item);
            return Result.success(new Data.Builder().putString(KEY_MESSAGE, "done").putInt(KEY_PERCENT, 100).build());
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
