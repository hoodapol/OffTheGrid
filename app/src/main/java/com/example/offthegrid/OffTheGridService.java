package com.example.offthegrid;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OffTheGridService extends Service {
    private static final String TAG = "OffTheGridService";

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
        MessageSink.createChannel(this);
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
            send(peer, text);
        } else if (ACTION_SIMULATE_INCOMING.equals(action)) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            MessageSink.receive(this, peer, "Hello from " + peer);
        }
    }

    private void send(String peer, String text) {
        boolean networked = PeerRegistry.contains(peer);
        int firstStatus = networked ? MessageStorage.STATUS_PENDING : MessageStorage.STATUS_DONE;
        Uri row = MessageSink.save(this, MessageStorage.ME, peer, text, firstStatus);
        MessageSink.broadcastSaved(this, peer);
        if (networked && row != null && PeerClient.sendMessage(this, peer, text)) {
            MessageSink.setStatus(this, row, MessageStorage.STATUS_DONE);
        }
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