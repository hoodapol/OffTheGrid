package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public final class ImageStore {

    private static final String TAG = "ImageStore";
    private static final String DIR = "images";
    private static final int MAX_SIDE = 1024;
    private static final int MAX_BYTES = 300 * 1024;
    private static final int THUMB_SIDE = 600;
    private static final int MAX_RECEIVED_BYTES = 2 * 1024 * 1024;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(cacheSizeKb()) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private ImageStore() {
    }

    private static int cacheSizeKb() {
        return (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
    }

    public static String saveFromUri(Context context, Uri uri) {
        try {
            Bitmap bitmap = decode(context, uri);
            if (bitmap == null) return null;
            byte[] bytes = compress(bitmap);
            String name = UUID.randomUUID().toString() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(file(context, name))) {
                out.write(bytes);
            }
            return name;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Could not save image", e);
            return null;
        }
    }

    public static Bitmap load(Context context, String name) {
        File file = file(context, name);
        if (!file.exists()) return null;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "Could not load image", e);
            return null;
        }
    }

    public static String saveBitmap(Context context, Bitmap bitmap) {
        try {
            byte[] bytes = compress(bitmap);
            String name = UUID.randomUUID().toString() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(file(context, name))) {
                out.write(bytes);
            }
            return name;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Could not save bitmap", e);
            return null;
        }
    }

    public static String readScaledBase64(Context context, String name, int maxSide,
                                          int maxBytes) {
        if (name == null) return "";
        File file = file(context, name);
        if (!file.exists()) return "";
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "";
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return "";
            bitmap = scaleDown(bitmap, maxSide);
            return Base64.encodeToString(compress(bitmap, maxBytes), Base64.NO_WRAP);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Could not read scaled image", e);
            return "";
        }
    }

    public static String saveBase64(Context context, String data) {
        if (data == null || data.length() > MAX_RECEIVED_BYTES * 4 / 3 + 8) return null;
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_RECEIVED_BYTES) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            String name = UUID.randomUUID().toString() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(file(context, name))) {
                out.write(bytes);
            }
            return name;
        } catch (IllegalArgumentException | IOException e) {
            Log.w(TAG, "Could not save received image", e);
            return null;
        }
    }

    public static String readBase64(Context context, String name) {
        File file = file(context, name);
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (IOException e) {
            Log.w(TAG, "Could not read image", e);
            return null;
        }
    }

    public static File file(Context context, String name) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create image folder");
        }
        return new File(dir, name);
    }

    public static Bitmap thumbnail(Context context, String name) {
        Bitmap cached = CACHE.get(name);
        if (cached != null) return cached;
        File file = file(context, name);
        if (!file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_SIDE);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap != null) {
            CACHE.put(name, bitmap);
        }
        return bitmap;
    }

    public static void delete(Context context, String name) {
        CACHE.remove(name);
        File file = file(context, name);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete " + name);
        }
    }

    private static Bitmap decode(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) return null;
        bitmap = rotate(bitmap, orientation(context, uri));
        return scaleDown(bitmap, MAX_SIDE);
    }

    private static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= target) {
            sample *= 2;
        }
        return sample;
    }

    private static int orientation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maxSide) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxSide) return bitmap;
        float scale = (float) maxSide / longest;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)),
                true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static byte[] compress(Bitmap bitmap) {
        return compress(bitmap, MAX_BYTES);
    }

    private static byte[] compress(Bitmap bitmap, int maxBytes) {
        Bitmap current = bitmap;
        int quality = 85;
        byte[] bytes = encode(current, quality);
        while (bytes.length > maxBytes) {
            if (quality > 45) {
                quality -= 10;
            } else {
                int longest = Math.max(current.getWidth(), current.getHeight());
                Bitmap smaller = scaleDown(current, Math.round(longest * 0.8f));
                if (smaller == current) break;
                current = smaller;
            }
            bytes = encode(current, quality);
        }
        return bytes;
    }

    private static byte[] encode(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }
}