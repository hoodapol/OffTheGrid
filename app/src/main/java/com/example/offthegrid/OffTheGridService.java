package com.example.offthegrid;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OffTheGridService extends Service {
    private static final String TAG = "OffTheGridService";

    public static final String ACTION_SEND = "com.example.offthegrid.action.SEND";
    public static final String ACTION_SEND_IMAGE = "com.example.offthegrid.action.SEND_IMAGE";
    public static final String ACTION_UNSEND = "com.example.offthegrid.action.UNSEND";
    public static final String ACTION_RETRY = "com.example.offthegrid.action.RETRY";
    public static final String EVENT_MESSAGE_SAVED = "com.example.offthegrid.event.MESSAGE_SAVED";
    public static final String EXTRA_PEER = "peer";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_ROW_ID = "row_id";
    public static final String EXTRA_IMAGE = "image";

    private ExecutorService io;

    private static final class PendingMessage {
        final long id;
        final String uid;
        final String text;
        final String image;

        PendingMessage(long id, String uid, String text, String image) {
            this.id = id;
            this.uid = uid;
            this.text = text;
            this.image = image;
        }
    }

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
        final String image = intent.getStringExtra(EXTRA_IMAGE);
        final long rowId = intent.getLongExtra(EXTRA_ROW_ID, -1);
        io.execute(() -> {
            handle(action, peer, text, uid, rowId, image);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    private void handle(String action, String peer, String text, String uid, long rowId,
                        String image) {
        if (peer == null) return;
        if (ACTION_SEND.equals(action) && text != null) {
            send(peer, text);
        } else if (ACTION_SEND_IMAGE.equals(action) && image != null) {
            sendImage(peer, image);
        } else if (ACTION_RETRY.equals(action)) {
            retry(peer);
        } else if (ACTION_UNSEND.equals(action) && uid != null && rowId >= 0) {
            unsend(peer, uid, rowId);
        }
    }

    private void send(String peer, String text) {
        String uid = UUID.randomUUID().toString();
        Uri row = MessageSink.save(this, MessageStorage.ME, peer, text,
                MessageStorage.STATUS_PENDING, uid);
        MessageSink.broadcastSaved(this, peer);
        if (row != null && PeerClient.sendMessage(this, peer, uid, text)) {
            MessageSink.setStatus(this, ContentUris.parseId(row), MessageStorage.STATUS_DONE);
            MessageSink.broadcastSaved(this, peer);
        }
    }

    private void sendImage(String peer, String image) {
        String uid = UUID.randomUUID().toString();
        Uri row = MessageSink.save(this, MessageStorage.ME, peer, "",
                MessageStorage.STATUS_PENDING, uid, image);
        MessageSink.broadcastSaved(this, peer);
        if (row != null && PeerClient.sendImage(this, peer, uid, image)) {
            MessageSink.setStatus(this, ContentUris.parseId(row), MessageStorage.STATUS_DONE);
            MessageSink.broadcastSaved(this, peer);
        }
    }

    private void retry(String peer) {
        boolean changed = false;
        for (PendingMessage item : loadPending(peer)) {
            if (item.image != null && !ImageStore.file(this, item.image).exists()) continue;
            boolean sent = item.image == null
                    ? PeerClient.sendMessage(this, peer, item.uid, item.text)
                    : PeerClient.sendImage(this, peer, item.uid, item.image);
            if (!sent) break;
            MessageSink.setStatus(this, item.id, MessageStorage.STATUS_DONE);
            changed = true;
        }
        if (changed) {
            MessageSink.broadcastSaved(this, peer);
        }
    }

    private List<PendingMessage> loadPending(String peer) {
        List<PendingMessage> list = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                MessageStorage.CONTENT_URI,
                null,
                MessageStorage.COL_PEER + "=? AND "
                        + MessageStorage.COL_SENDER + "=? AND "
                        + MessageStorage.COL_STATUS + "=? AND "
                        + MessageStorage.COL_UID + " IS NOT NULL",
                new String[]{peer, MessageStorage.ME,
                        String.valueOf(MessageStorage.STATUS_PENDING)},
                MessageStorage.COL_TIMESTAMP + " ASC, " + MessageStorage.COL_ID + " ASC")) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_ID);
                int uidIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_UID);
                int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
                int imageIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_IMAGE);
                while (cursor.moveToNext()) {
                    list.add(new PendingMessage(cursor.getLong(idIndex),
                            cursor.getString(uidIndex), cursor.getString(textIndex),
                            cursor.getString(imageIndex)));
                }
            }
        }
        return list;
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