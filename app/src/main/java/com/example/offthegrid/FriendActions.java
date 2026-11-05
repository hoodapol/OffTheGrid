package com.example.offthegrid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FriendActions {

    public static final String REQUEST = "REQUEST";
    public static final String ACCEPT = "ACCEPT";
    public static final String DECLINE = "DECLINE";
    public static final String REMOVE = "REMOVE";

    private static final String CHANNEL_ID = "friend_requests";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private FriendActions() {
    }

    public static void onIncoming(Context context, String key, String device, String action) {
        FriendStore.Friend current = FriendStore.get(context, device);
        int state = current == null ? FriendStore.STATE_NONE : current.state;
        switch (action) {
            case REQUEST:
                if (state == FriendStore.STATE_FRIEND) {
                    sendLater(context, key, ACCEPT);
                } else if (state == FriendStore.STATE_PENDING_OUT) {
                    FriendStore.upsert(context, device, key, FriendStore.STATE_FRIEND);
                    sendLater(context, key, ACCEPT);
                } else {
                    FriendStore.upsert(context, device, key, FriendStore.STATE_PENDING_IN);
                    if (state != FriendStore.STATE_PENDING_IN) {
                        notifyRequest(context, key);
                    }
                }
                break;
            case ACCEPT:
                if (state == FriendStore.STATE_PENDING_OUT) {
                    FriendStore.upsert(context, device, key, FriendStore.STATE_FRIEND);
                }
                break;
            case DECLINE:
                if (state == FriendStore.STATE_PENDING_OUT) {
                    FriendStore.remove(context, device);
                }
                break;
            case REMOVE:
                if (state != FriendStore.STATE_NONE) {
                    FriendStore.remove(context, device);
                }
                break;
            default:
                break;
        }
    }

    private static void sendLater(Context context, String key, String action) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> PeerClient.sendFriend(app, key, action));
    }

    @SuppressLint("MissingPermission")
    private static void notifyRequest(Context context, String name) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Friend requests", NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Intent open = new Intent(context, DiscoverActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, name.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(name)
                .setContentText(context.getString(R.string.friend_request_notification))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(context).notify(("friend:" + name).hashCode(), notification);
    }
}