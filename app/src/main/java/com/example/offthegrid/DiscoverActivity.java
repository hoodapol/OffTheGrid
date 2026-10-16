package com.example.offthegrid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityDiscoverBinding;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;

import android.provider.Settings;
import android.widget.Toast;
import android.content.ActivityNotFoundException;
public class DiscoverActivity extends AppCompatActivity {
    private static final String TAG = "DiscoverActivity";

    private ActivityDiscoverBinding binding;
    private final List<String> peers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityDiscoverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        peers.add("Test Peer");
        binding.listPeers.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, peers));

        binding.listPeers.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peers.get(position));
            startActivity(intent);
        });

        binding.btnWifi.setOnClickListener(v -> openWifiSettings());
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