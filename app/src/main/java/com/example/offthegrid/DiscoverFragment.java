package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.FragmentDiscoverBinding;

import java.util.ArrayList;
import java.util.List;

public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private PeerAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PeerRegistry.Listener registryListener = () -> main.post(this::refreshPeers);
    private final FriendStore.Listener friendListener = () -> main.post(this::refreshPeers);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        adapter = new PeerAdapter(this::openChat);
        binding.rvPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPeers.setAdapter(adapter);
        binding.btnRefreshNearby.setOnClickListener(v -> refreshNearby());
        refreshPeers();
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        PeerRegistry.addListener(registryListener);
        FriendStore.addListener(friendListener);
        refreshPeers();
    }

    @Override
    public void onStop() {
        PeerRegistry.removeListener(registryListener);
        FriendStore.removeListener(friendListener);
        super.onStop();
    }

    private boolean onWifi() {
        Context context = getContext();
        if (context == null) return true;
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void refreshPeers() {
        if (binding == null) return;
        Context context = getContext();
        if (context == null) return;
        List<String> names = PeerRegistry.names();
        List<String> friends = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String name : names) {
            PeerRegistry.Address address = PeerRegistry.get(name);
            if (address != null && FriendStore.isFriend(context, address.deviceId)) {
                friends.add(name);
            } else {
                others.add(name);
            }
        }
        adapter.setAll(friends, others);
        boolean empty = names.isEmpty();
        binding.tvPeersEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvPeers.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvNearbyWifiWarning.setVisibility(empty && !onWifi() ? View.VISIBLE : View.GONE);
    }

    private void refreshNearby() {
        Context context = requireContext();
        binding.btnRefreshNearby.setEnabled(false);
        binding.btnRefreshNearby.setAlpha(0.4f);
        binding.btnRefreshNearby.animate().rotationBy(360f).setDuration(800).start();
        ListenerService.restartDiscovery(context);
        AvatarCache.forceRefresh(context);
        PresenceMonitor.sweepNow(context);
        refreshPeers();
        main.postDelayed(() -> {
            if (binding != null) {
                binding.btnRefreshNearby.setEnabled(true);
                binding.btnRefreshNearby.setAlpha(1f);
            }
        }, 2000);
    }

    private void openChat(String peer) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        main.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }
}