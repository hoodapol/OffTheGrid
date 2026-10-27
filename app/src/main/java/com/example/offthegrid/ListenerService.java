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
    private static final int READ_TIMEOUT_MS = 5000;

    private ExecutorService workers;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (!running) {
            running = true;
            workers = Executors.newCachedThreadPool();
            workers.execute(this::acceptLoop);
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
                Wire.writeLine(out, Wire.hello(Profile.getName(this)));
            } else if (Wire.MSG.equals(packet.type)) {
                MessageSink.receive(getApplicationContext(), packet.name, packet.text);
                Wire.writeLine(out, Wire.ack());
            }
        } catch (IOException e) {
            Log.w(TAG, "Connection failed", e);
        }
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