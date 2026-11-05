package com.example.offthegrid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

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

    private static final String TAG = "ListenerService";
    private static final int READ_TIMEOUT_MS = 10000;

    private ExecutorService workers;
    private NsdHelper nsdHelper;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (!running) {
            running = true;
            workers = Executors.newCachedThreadPool();
            workers.execute(this::acceptLoop);
            nsdHelper = new NsdHelper(getApplicationContext());
            nsdHelper.start();
        }
        return START_NOT_STICKY;
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
            if (Wire.HELLO.equals(packet.type)) {
                rememberSender(connection, packet);
                Wire.writeLine(out, Wire.hello(Profile.getName(this), Profile.getId(this)));
            } else if (Wire.MSG.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                if (packet.image.isEmpty()) {
                    MessageSink.receive(getApplicationContext(), key, packet.text, packet.id);
                } else if (!MessageSink.receiveImage(
                        getApplicationContext(), key, packet.image, packet.id)) {
                    return;
                }
                Wire.writeLine(out, Wire.ack());
            } else if (Wire.DEL.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                MessageSink.deleteRemote(getApplicationContext(), key, packet.id);
                Wire.writeLine(out, Wire.ack());
            } else if (Wire.GET_PROFILE.equals(packet.type)) {
                if (isSelf(packet)) return;
                rememberSender(connection, packet);
                boolean friend = FriendStore.isFriend(getApplicationContext(), packet.device);
                Wire.writeLine(out, ProfileShare.build(getApplicationContext(), friend));
            } else if (Wire.GET_AVATAR.equals(packet.type)) {
                if (isSelf(packet)) return;
                rememberSender(connection, packet);
                Wire.writeLine(out, Wire.avatar(Profile.getName(this), Profile.getId(this),
                        ImageStore.readScaledBase64(getApplicationContext(),
                                Profile.getAvatar(this), 128, 12 * 1024)));
            } else if (Wire.FRIEND.equals(packet.type)) {
                if (isSelf(packet)) return;
                String key = rememberSender(connection, packet);
                FriendActions.onIncoming(getApplicationContext(), key, packet.device, packet.text);
                Wire.writeLine(out, Wire.ack());
            }
        } catch (IOException e) {
            Log.w(TAG, "Connection failed", e);
        }
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