package com.example.offthegrid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class GallerySaver {

    private static final String TAG = "GallerySaver";
    private static final String FOLDER = "OffTheGrid";

    private GallerySaver() {
    }

    public static boolean save(Context context, String name) {
        File source = ImageStore.file(context, name);
        if (source == null || !source.exists()) return false;
        String display = "OffTheGrid_" + System.currentTimeMillis() + ".jpg";
        try (InputStream in = new FileInputStream(source)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveWithMediaStore(context, in, display);
            }
            return saveToPictures(context, in, display);
        } catch (IOException e) {
            Log.w(TAG, "Save failed", e);
            return false;
        }
    }

    private static boolean saveWithMediaStore(Context context, InputStream in, String display) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, display);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + FOLDER);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                resolver.delete(uri, null, null);
                return false;
            }
            copy(in, out);
        } catch (IOException e) {
            Log.w(TAG, "Write failed", e);
            resolver.delete(uri, null, null);
            return false;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return true;
    }

    private static boolean saveToPictures(Context context, InputStream in, String display)
            throws IOException {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                FOLDER);
        if (!dir.exists() && !dir.mkdirs()) return false;
        File target = new File(dir, display);
        try (OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()},
                new String[]{"image/jpeg"}, null);
        return true;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}