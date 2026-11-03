package com.example.offthegrid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class MessageSink {

    private static final String CHANNEL_ID = "incoming_messages";

    private MessageSink() {
    }

    public static Uri save(Context context, String sender, String peer, String text, int status,
                           String uid) {
        return save(context, sender, peer, text, status, uid, null);
    }

    public static Uri save(Context context, String sender, String peer, String text, int status,
                           String uid, String image) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_SENDER, sender);
        values.put(MessageStorage.COL_TEXT, text);
        values.put(MessageStorage.COL_TIMESTAMP, System.currentTimeMillis());
        values.put(MessageStorage.COL_PEER, peer);
        values.put(MessageStorage.COL_STATUS, status);
        values.put(MessageStorage.COL_UID, uid);
        values.put(MessageStorage.COL_IMAGE, image);
        return context.getContentResolver().insert(MessageStorage.CONTENT_URI, values);
    }

    public static void setStatus(Context context, long id, int status) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_STATUS, status);
        context.getContentResolver().update(
                MessageStorage.CONTENT_URI,
                values,
                MessageStorage.COL_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public static void deleteMessage(Context context, long id) {
        String selection = MessageStorage.COL_ID + "=?";
        String[] args = new String[]{String.valueOf(id)};
        List<String> images = imageNames(context, selection, args);
        context.getContentResolver().delete(MessageStorage.CONTENT_URI, selection, args);
        deleteImages(context, images);
    }

    public static void deleteChat(Context context, String peer) {
        String selection = MessageStorage.COL_PEER + "=?";
        String[] args = new String[]{peer};
        List<String> images = imageNames(context, selection, args);
        context.getContentResolver().delete(MessageStorage.CONTENT_URI, selection, args);
        deleteImages(context, images);
    }

    public static void deleteRemote(Context context, String peer, String uid) {
        String selection = MessageStorage.COL_UID + "=? AND " + MessageStorage.COL_PEER + "=? AND "
                + MessageStorage.COL_SENDER + "=?";
        String[] args = new String[]{uid, peer, peer};
        List<String> images = imageNames(context, selection, args);
        int count = context.getContentResolver().delete(MessageStorage.CONTENT_URI, selection, args);
        if (count > 0) {
            deleteImages(context, images);
            broadcastSaved(context, peer);
        }
    }

    private static List<String> imageNames(Context context, String selection, String[] args) {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_IMAGE},
                selection + " AND " + MessageStorage.COL_IMAGE + " IS NOT NULL",
                args,
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(0));
                }
            }
        }
        return names;
    }

    private static void deleteImages(Context context, List<String> names) {
        for (String name : names) {
            ImageStore.delete(context, name);
        }
    }

    public static void broadcastSaved(Context context, String peer) {
        Intent intent = new Intent(OffTheGridService.EVENT_MESSAGE_SAVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(OffTheGridService.EXTRA_PEER, peer);
        context.sendBroadcast(intent);
    }

    public static void receive(Context context, String peer, String text, String uid) {
        if (uid != null && alreadyReceived(context, peer, uid)) return;
        save(context, peer, peer, text, MessageStorage.STATUS_DONE, uid);
        broadcastSaved(context, peer);
        notifyIncoming(context, peer, text);
    }

    private static boolean alreadyReceived(Context context, String peer, String uid) {
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_ID},
                MessageStorage.COL_UID + "=? AND " + MessageStorage.COL_PEER + "=? AND "
                        + MessageStorage.COL_SENDER + "=?",
                new String[]{uid, peer, peer},
                null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Incoming messages", NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission")
    private static void notifyIncoming(Context context, String peer, String body) {
        if (peer.equals(ChatActivity.visiblePeer)) return;
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);
        Intent open = new Intent(context, ChatActivity.class);
        open.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        PendingIntent pendingIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent(peer.hashCode(),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(peer)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(context).notify(peer.hashCode(), notification);
    }
}