package com.forgptstas.neurorecorder.modules.asr;

import android.content.Context;
import android.net.Uri;

/** Replaceable offline speech recognition boundary. */
public interface AsrModule {
    RecognitionResult transcribe(Context context, Uri audioUri, ProgressListener listener) throws Exception;

    interface ProgressListener {
        void onProgress(String message, int percent);
    }
}
