package com.forgptstas.neurorecorder;

import android.net.Uri;

public final class RecordingItem {
    private final long id;
    private final Uri uri;
    private final String name;
    private final long dateAddedSeconds;
    private final long durationMillis;
    private final long sizeBytes;

    public RecordingItem(long id, Uri uri, String name, long dateAddedSeconds, long durationMillis, long sizeBytes) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.dateAddedSeconds = dateAddedSeconds;
        this.durationMillis = durationMillis;
        this.sizeBytes = sizeBytes;
    }

    public long getId() {
        return id;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public long getDateAddedSeconds() {
        return dateAddedSeconds;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
