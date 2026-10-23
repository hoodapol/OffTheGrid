package com.example.offthegrid;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
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

public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private PeerAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        adapter = new PeerAdapter(this::openChat);
        binding.rvPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPeers.setAdapter(adapter);
        adapter.add("Test Peer");
        binding.btnWifi.setOnClickListener(v -> openWifiSettings());
        return binding.getRoot();
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
}