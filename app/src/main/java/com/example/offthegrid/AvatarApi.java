package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;

public final class AvatarApi {

    public interface Callback {
        void onBitmap(Bitmap bitmap);

        void onFailure();
    }

    private static final String TAG = "AvatarApi";
    private static final String BASE = "https://api.dicebear.com/10.x/";
    private static final int TIMEOUT_MS = 8000;

    private static RequestQueue queue;

    private AvatarApi() {
    }

    public static String url(String style, String seed, String background) {
        return BASE + style + "/png?seed=" + Uri.encode(seed) + "&backgroundColor=" + background;
    }

    private static synchronized RequestQueue queue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context.getApplicationContext());
        }
        return queue;
    }

    public static void fetch(Context context, Object tag, String style, String seed,
                             String background, Callback callback) {
        ImageRequest request = new ImageRequest(
                url(style, seed, background),
                callback::onBitmap,
                0,
                0,
                ImageView.ScaleType.CENTER_INSIDE,
                Bitmap.Config.ARGB_8888,
                error -> {
                    Log.w(TAG, "Avatar request failed for " + style + ": " + error);
                    callback.onFailure();
                });
        request.setTag(tag);
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1f));
        queue(context).add(request);
    }

    public static void cancel(Context context, Object tag) {
        queue(context).cancelAll(tag);
    }
}