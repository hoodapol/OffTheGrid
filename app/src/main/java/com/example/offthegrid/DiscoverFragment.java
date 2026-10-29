package com.example.offthegrid;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.FragmentDiscoverBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private PeerAdapter adapter;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        adapter = new PeerAdapter(this::openChat);
        binding.rvPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPeers.setAdapter(adapter);
        adapter.add("Test Peer");
        for (String name : PeerRegistry.names()) {
            adapter.addIfMissing(name);
        }
        binding.tvThisDevice.setText(getString(R.string.listening_on, ListenerService.PORT));
        binding.btnWifi.setOnClickListener(v -> openWifiSettings());
        binding.btnConnectToggle.setOnClickListener(v -> toggleConnectCard());
        binding.btnConnect.setOnClickListener(v -> connect());
        return binding.getRoot();
    }

    private void toggleConnectCard() {
        boolean show = binding.connectCard.getVisibility() != View.VISIBLE;
        binding.connectCard.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void connect() {
        String host = binding.etHost.getText().toString().trim();
        int port = parsePort(binding.etPort.getText().toString().trim());
        if (host.isEmpty() || port < 0) {
            Toast.makeText(requireContext(), R.string.enter_host_and_port, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.btnConnect.setEnabled(false);
        Context appContext = requireContext().getApplicationContext();
        network.execute(() -> {
            String name = PeerClient.hello(appContext, host, port);
            if (name != null) {
                PeerRegistry.put(name, host, port);
            }
            main.post(() -> showConnectResult(name));
        });
    }

    private void showConnectResult(String name) {
        if (binding == null) return;
        binding.btnConnect.setEnabled(true);
        if (name == null) {
            Toast.makeText(requireContext(), R.string.connect_failed, Toast.LENGTH_LONG).show();
            return;
        }
        adapter.addIfMissing(name);
        binding.connectCard.setVisibility(View.GONE);
        Toast.makeText(requireContext(), getString(R.string.connected_to, name), Toast.LENGTH_SHORT).show();
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text);
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void openChat(String peer) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        startActivity(intent);
    }

    private void openWifiSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "No Wi-Fi settings screen found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        network.shutdown();
        super.onDestroy();
    }
}