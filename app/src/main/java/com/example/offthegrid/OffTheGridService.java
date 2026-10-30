package com.example.offthegrid;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OffTheGridService extends Service {
    private static final String TAG = "OffTheGridService";

    public static final String ACTION_SEND = "com.example.offthegrid.action.SEND";
    public static final String ACTION_UNSEND = "com.example.offthegrid.action.UNSEND";
    public static final String EVENT_MESSAGE_SAVED = "com.example.offthegrid.event.MESSAGE_SAVED";
    public static final String EXTRA_PEER = "peer";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_ROW_ID = "row_id";

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
        final String uid = intent.getStringExtra(EXTRA_UID);
        final long rowId = intent.getLongExtra(EXTRA_ROW_ID, -1);
        io.execute(() -> {
            handle(action, peer, text, uid, rowId);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    private void handle(String action, String peer, String text, String uid, long rowId) {
        if (peer == null) return;
        if (ACTION_SEND.equals(action) && text != null) {
            send(peer, text);
        } else if (ACTION_UNSEND.equals(action) && uid != null && rowId >= 0) {
            unsend(peer, uid, rowId);
        }
    }

    private void send(String peer, String text) {
        String uid = UUID.randomUUID().toString();
        boolean networked = PeerRegistry.contains(peer);
        int firstStatus = networked ? MessageStorage.STATUS_PENDING : MessageStorage.STATUS_DONE;
        Uri row = MessageSink.save(this, MessageStorage.ME, peer, text, firstStatus, uid);
        MessageSink.broadcastSaved(this, peer);
        if (networked && row != null && PeerClient.sendMessage(this, peer, uid, text)) {
            MessageSink.setStatus(this, row, MessageStorage.STATUS_DONE);
        }
    }

    private void unsend(String peer, String uid, long rowId) {
        if (PeerClient.sendDelete(this, peer, uid)) {
            MessageSink.deleteMessage(this, rowId);
            MessageSink.broadcastSaved(this, peer);
        } else {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.unsend_failed, peer),
                            Toast.LENGTH_LONG).show());
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