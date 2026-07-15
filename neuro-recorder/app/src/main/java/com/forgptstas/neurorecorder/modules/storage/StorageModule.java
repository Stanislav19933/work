package com.forgptstas.neurorecorder.modules.storage;

import android.net.Uri;

import com.forgptstas.neurorecorder.RecordingItem;

import java.util.List;

/** Replaceable boundary for recordings and generated meeting artifacts. */
public interface StorageModule {
    List<RecordingItem> loadRecordings();

    boolean renameRecording(RecordingItem item, String requestedName);

    boolean deleteRecording(RecordingItem item);

    Uri getRecordingUri(RecordingItem item);
}
