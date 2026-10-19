package com.example.offthegrid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OffTheGridService extends Service {
    private static final String TAG = "OffTheGridService";
    private static final String CHANNEL_ID = "incoming_messages";

    public static final String ACTION_SEND = "com.example.offthegrid.action.SEND";
    public static final String ACTION_SIMULATE_INCOMING = "com.example.offthegrid.action.SIMULATE_INCOMING";
    public static final String EVENT_MESSAGE_SAVED = "com.example.offthegrid.event.MESSAGE_SAVED";
    public static final String EXTRA_PEER = "peer";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DELAY_MS = "delay_ms";

    private ExecutorService io;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        io = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand " + (intent == null ? "null" : intent.getAction()));
        if (intent == null || intent.getAction() == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final String action = intent.getAction();
        final String peer = intent.getStringExtra(EXTRA_PEER);
        final String text = intent.getStringExtra(EXTRA_TEXT);
        final long delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 0);
        io.execute(() -> {
            handle(action, peer, text, delayMs);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    private void handle(String action, String peer, String text, long delayMs) {
        if (peer == null) return;
        if (ACTION_SEND.equals(action) && text != null) {
            save(MessageStorage.ME, peer, text);
            broadcastSaved(peer);
        } else if (ACTION_SIMULATE_INCOMING.equals(action)) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            String body = "Hello from " + peer;
            save(peer, peer, body);
            broadcastSaved(peer);
            notifyIncoming(peer, body);
        }
    }

    private void save(String sender, String peer, String text) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_SENDER, sender);
        values.put(MessageStorage.COL_TEXT, text);
        values.put(MessageStorage.COL_TIMESTAMP, System.currentTimeMillis());
        values.put(MessageStorage.COL_PEER, peer);
        values.put(MessageStorage.COL_STATUS, MessageStorage.STATUS_DONE);
        getContentResolver().insert(MessageStorage.CONTENT_URI, values);
    }

    private void broadcastSaved(String peer) {
        Intent intent = new Intent(EVENT_MESSAGE_SAVED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PEER, peer);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Incoming messages", NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission")
    private void notifyIncoming(String peer, String body) {
        if (peer.equals(ChatActivity.visiblePeer)) return;
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent open = new Intent(this, ChatActivity.class);
        open.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        PendingIntent pendingIntent = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(open)
                .getPendingIntent(peer.hashCode(),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(peer)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(this).notify(peer.hashCode(), notification);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        io.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}