package com.example.offthegrid;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.ActivityDiscoverBinding;

public class DiscoverActivity extends AppCompatActivity {
    private static final String TAG = "DiscoverActivity";

    private ActivityDiscoverBinding binding;
    private PeerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityDiscoverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new PeerAdapter(this::openChat);
        binding.rvPeers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPeers.setAdapter(adapter);
        adapter.add("Test Peer");

        binding.btnWifi.setOnClickListener(v -> openWifiSettings());
    }

    private void openChat(String peer) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        startActivity(intent);
    }

    private void openWifiSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No Wi-Fi settings screen found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}