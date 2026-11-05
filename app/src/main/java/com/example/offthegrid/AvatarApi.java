package com.example.offthegrid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class AvatarApi {

    private static final String TAG = "AvatarApi";
    private static final String BASE = "https://api.dicebear.com/10.x/";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 1024 * 1024;

    private AvatarApi() {
    }

    public static String url(String style, String seed, String background) {
        return BASE + style + "/png?seed=" + Uri.encode(seed) + "&backgroundColor=" + background;
    }

    public static Bitmap fetch(String style, String seed, String background) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url(style, seed, background)).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "image/png");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + " for " + style);
                return null;
            }
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) return null;
                    out.write(buffer, 0, read);
                }
                byte[] bytes = out.toByteArray();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (IOException e) {
            Log.w(TAG, "Fetch failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}