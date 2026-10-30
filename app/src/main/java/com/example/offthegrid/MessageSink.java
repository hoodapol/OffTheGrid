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
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

public final class MessageSink {

    private static final String CHANNEL_ID = "incoming_messages";

    private MessageSink() {
    }

    public static Uri save(Context context, String sender, String peer, String text, int status,
                           String uid) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_SENDER, sender);
        values.put(MessageStorage.COL_TEXT, text);
        values.put(MessageStorage.COL_TIMESTAMP, System.currentTimeMillis());
        values.put(MessageStorage.COL_PEER, peer);
        values.put(MessageStorage.COL_STATUS, status);
        values.put(MessageStorage.COL_UID, uid);
        return context.getContentResolver().insert(MessageStorage.CONTENT_URI, values);
    }

    public static void setStatus(Context context, Uri row, int status) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_STATUS, status);
        context.getContentResolver().update(
                MessageStorage.CONTENT_URI,
                values,
                MessageStorage.COL_ID + "=?",
                new String[]{row.getLastPathSegment()});
    }

    public static void deleteMessage(Context context, long id) {
        context.getContentResolver().delete(
                MessageStorage.CONTENT_URI,
                MessageStorage.COL_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public static void deleteChat(Context context, String peer) {
        context.getContentResolver().delete(
                MessageStorage.CONTENT_URI,
                MessageStorage.COL_PEER + "=?",
                new String[]{peer});
    }

    public static void deleteRemote(Context context, String peer, String uid) {
        int count = context.getContentResolver().delete(
                MessageStorage.CONTENT_URI,
                MessageStorage.COL_UID + "=? AND " + MessageStorage.COL_PEER + "=? AND "
                        + MessageStorage.COL_SENDER + "=?",
                new String[]{uid, peer, peer});
        if (count > 0) {
            broadcastSaved(context, peer);
        }
    }

    public static void broadcastSaved(Context context, String peer) {
        Intent intent = new Intent(OffTheGridService.EVENT_MESSAGE_SAVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(OffTheGridService.EXTRA_PEER, peer);
        context.sendBroadcast(intent);
    }

    public static void receive(Context context, String peer, String text, String uid) {
        save(context, peer, peer, text, MessageStorage.STATUS_DONE, uid);
        broadcastSaved(context, peer);
        notifyIncoming(context, peer, text);
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