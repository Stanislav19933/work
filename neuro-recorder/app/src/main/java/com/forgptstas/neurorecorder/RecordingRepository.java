package com.forgptstas.neurorecorder;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public final class RecordingRepository {
    private static final String RELATIVE_PATH = Environment.DIRECTORY_MUSIC + "/NeuroRecorder/";

    private final ContentResolver resolver;

    public RecordingRepository(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    public List<RecordingItem> loadAll() {
        List<RecordingItem> items = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
        };

        String selection;
        String[] selectionArgs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Audio.Media.RELATIVE_PATH + "=?";
            selectionArgs = new String[]{RELATIVE_PATH};
        } else {
            selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
            selectionArgs = new String[]{"meeting_%"};
        }

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor == null) {
                return items;
            }

            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                Uri uri = ContentUris.withAppendedId(collection, id);
                items.add(new RecordingItem(
                        id,
                        uri,
                        cursor.getString(nameIndex),
                        cursor.getLong(dateIndex),
                        cursor.getLong(durationIndex),
                        cursor.getLong(sizeIndex)
                ));
            }
        }
        return items;
    }

    public boolean rename(RecordingItem item, String requestedName) {
        String cleaned = requestedName == null ? "" : requestedName.trim();
        if (cleaned.isEmpty()) {
            return false;
        }
        if (!cleaned.toLowerCase().endsWith(".m4a")) {
            cleaned += ".m4a";
        }
        cleaned = cleaned.replaceAll("[\\/:*?\"<>|]", "_");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, cleaned);
        return resolver.update(item.getUri(), values, null, null) > 0;
    }

    public boolean delete(RecordingItem item) {
        return resolver.delete(item.getUri(), null, null) > 0;
    }
}
