package com.example.offthegrid;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class NsdHelper {

    private static final String TAG = "NsdHelper";
    private static final String SERVICE_TYPE = "_offthegrid._tcp.";

    private final Context context;
    private final NsdManager nsd;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Queue<NsdServiceInfo> pending = new ArrayDeque<>();
    private final Map<String, String> peerByService = new ConcurrentHashMap<>();

    private volatile String ownServiceName = "";
    private boolean resolving;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;

    public NsdHelper(Context context) {
        this.context = context.getApplicationContext();
        this.nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    public void start() {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(Profile.getName(context));
        info.setServiceType(SERVICE_TYPE);
        info.setPort(ListenerService.PORT);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                ownServiceName = serviceInfo.getServiceName();
                Log.d(TAG, "Registered as " + ownServiceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Unregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(TAG, "Unregistration failed: " + errorCode);
            }
        };

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (!service.getServiceType().contains("_offthegrid")) return;
                if (service.getServiceName().equals(ownServiceName)) return;
                enqueue(service);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                String peer = peerByService.remove(service.getServiceName());
                if (peer != null) {
                    PeerRegistry.remove(peer);
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Start discovery failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Stop discovery failed: " + errorCode);
            }
        };

        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener);
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "NSD start failed", e);
        }
    }

    public void stop() {
        try {
            if (registrationListener != null) {
                nsd.unregisterService(registrationListener);
            }
            if (discoveryListener != null) {
                nsd.stopServiceDiscovery(discoveryListener);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "NSD stop failed", e);
        }
        worker.shutdownNow();
    }

    private synchronized void enqueue(NsdServiceInfo service) {
        pending.add(service);
        resolveNext();
    }

    private synchronized void resolveNext() {
        if (resolving) return;
        NsdServiceInfo next = pending.poll();
        if (next == null) return;
        resolving = true;
        try {
            nsd.resolveService(next, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "Resolve failed: " + errorCode);
                    finishResolve();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    handleResolved(serviceInfo);
                    finishResolve();
                }
            });
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Resolve start failed", e);
            resolving = false;
        }
    }

    private synchronized void finishResolve() {
        resolving = false;
        resolveNext();
    }

    private void handleResolved(NsdServiceInfo info) {
        InetAddress address = info.getHost();
        if (address == null) return;
        String host = address.getHostAddress();
        int port = info.getPort();
        String serviceName = info.getServiceName();
        try {
            worker.execute(() -> {
                String name = PeerClient.hello(context, host, port);
                if (name != null && !name.equals(Profile.getName(context))) {
                    peerByService.put(serviceName, name);
                    PeerRegistry.put(name, host, port);
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Worker stopped", e);
        }
    }
}