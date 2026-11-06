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

import java.util.List;

public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private PeerAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PeerRegistry.Listener registryListener = () -> main.post(this::refreshPeers);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        adapter = new PeerAdapter(this::openChat);
        binding.rvPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPeers.setAdapter(adapter);
        refreshPeers();
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        PeerRegistry.addListener(registryListener);
        refreshPeers();
    }

    @Override
    public void onStop() {
        PeerRegistry.removeListener(registryListener);
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
        List<String> names = PeerRegistry.names();
        adapter.setAll(names);
        boolean empty = names.isEmpty();
        binding.tvPeersEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvPeers.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvNearbyWifiWarning.setVisibility(empty && !onWifi() ? View.VISIBLE : View.GONE);
    }

    private void openChat(String peer) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}