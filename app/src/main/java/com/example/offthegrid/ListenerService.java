package com.example.offthegrid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ListenerService extends Service {

    public static final int PORT = 5050;
    public static final String ACTION_STOP = "com.example.offthegrid.STOP_LISTENER";

    private static final String TAG = "ListenerService";
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int NOTIFICATION_ID = 4101;
    private static final String CHANNEL_ID = "visibility";

    private ExecutorService workers;
    private NsdHelper nsdHelper;
    private WifiManager.MulticastLock multicastLock;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public static void start(Context context) {
        Intent intent = new Intent(context, ListenerService.class);
        if (VisibilityPrefs.isStayVisible(context)) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            VisibilityPrefs.setStayVisible(this, false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (VisibilityPrefs.isStayVisible(this)) {
            enterForeground();
        } else {
            stopForeground(true);
        }
        if (!running) {
            running = true;
            workers = Executors.newCachedThreadPool();
            workers.execute(this::acceptLoop);
            acquireMulticastLock();
            nsdHelper = new NsdHelper(getApplicationContext());
            nsdHelper.start();
        }
        return START_NOT_STICKY;
    }

    private void enterForeground() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not enter foreground", e);
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Visibility", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Intent open = new Intent(this, DiscoverActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ListenerService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(getString(R.string.visibility_notification_title))
                .setContentText(getString(R.string.visibility_notification_text))
                .setContentIntent(openPending)
                .addAction(0, getString(R.string.visibility_stop), stopPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("offthegrid");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Multicast lock failed", e);
        }
    }

    private void acceptLoop() {
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            serverSocket = server;
            Log.d(TAG, "Listening on port " + PORT);
            while (running) {
                Socket socket = server.accept();
                try {
                    workers.execute(() -> handle(socket));
                } catch (RejectedExecutionException e) {
                    closeQuietly(socket);
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                Log.e(TAG, "Listener stopped", e);
            }
        } finally {
            closeQuietly(server);
        }
    }

    private void handle(Socket socket) {
        try (Socket connection = socket;
             BufferedReader in = Wire.reader(connection);
             BufferedWriter out = Wire.writer(connection)) {
            connection.setSoTimeout(READ_TIMEOUT_MS);
            Wire.Packet packet = Wire.parse(in.readLine());
            if (packet == null) return;
            Context app = getApplicationContext();
            if (Wire.HELLO.equals(packet.type)) {
                rememberSender(connection, packet);
                Wire.writeLine(out, Wire.hello(Profile.getName(this), Profile.getId(this)));
            } else if (Wire.MSG.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                if (!packet.ref.isEmpty()) {
                    MessageMetaStore.saveReply(app, packet.id, packet.ref, packet.quote);
                }
                if (packet.image.isEmpty()) {
                    MessageSink.receive(app, key, packet.text, packet.id);
                } else if (!MessageSink.receiveImage(app, key, packet.image, packet.id)) {
                    return;
                }
                TypingTracker.set(key, false);
                Wire.writeLine(out, Wire.ack());
            } else if (Wire.DEL.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                MessageSink.deleteRemote(app, key, packet.id);
                Wire.writeLine(out, Wire.ack());
            } else if (Wire.TYPING.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                TypingTracker.set(key, "1".equals(packet.text));
                Wire.writeLine(out, Wire.ack());
            } else if (Wire.REACT.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                if (handleReaction(app, key, packet)) {
                    Wire.writeLine(out, Wire.ack());
                }
            } else if (Wire.GET_PROFILE.equals(packet.type)) {
                if (isSelf(packet)) return;
                rememberSender(connection, packet);
                boolean friend = FriendStore.isFriend(app, packet.device);
                Wire.writeLine(out, ProfileShare.build(app, friend, packet.device));
            } else if (Wire.GET_AVATAR.equals(packet.type)) {
                if (isSelf(packet)) return;
                rememberSender(connection, packet);
                Wire.writeLine(out, Wire.avatar(Profile.getName(this), Profile.getId(this),
                        ImageStore.readScaledBase64(app, Profile.getAvatar(this), 128, 12 * 1024)));
            } else if (Wire.FRIEND.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                FriendActions.onIncoming(app, key, packet.device, packet.text);
                Wire.writeLine(out, Wire.ack());
            }
        } catch (IOException e) {
            Log.w(TAG, "Connection failed", e);
        }
    }

    private boolean handleReaction(Context app, String key, Wire.Packet packet) {
        String kind = packet.body;
        boolean on = !packet.text.isEmpty();
        if ("post".equals(kind)) {
            if (!FriendStore.isFriend(app, packet.device) || !postExists(app, packet.id)) {
                return false;
            }
            MessageMetaStore.setHeart(app, packet.id, packet.device, on);
            return true;
        }
        if ("msg".equals(kind)) {
            if (!MessageMetaStore.messageExists(app, key, packet.id)) {
                return false;
            }
            MessageMetaStore.setHeart(app, packet.id, packet.device, on);
            MessageSink.broadcastSaved(app, key);
            return true;
        }
        return false;
    }

    private boolean postExists(Context app, String uid) {
        for (Post post : PostStore.loadMine(app)) {
            if (uid.equals(post.uid)) return true;
        }
        return false;
    }

    private boolean isSelf(Wire.Packet packet) {
        return packet.device.equals(Profile.getId(this));
    }

    private String rememberSender(Socket connection, Wire.Packet packet) {
        if (isSelf(packet)) return null;
        String host = connection.getInetAddress().getHostAddress();
        return PeerRegistry.registerIfAbsent(packet.name, packet.device, host, PORT);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            Log.w(TAG, "Close failed", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        running = false;
        if (nsdHelper != null) {
            nsdHelper.stop();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        closeQuietly(serverSocket);
        if (workers != null) {
            workers.shutdownNow();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}