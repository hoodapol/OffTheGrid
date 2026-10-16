package com.example.offthegrid;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_PEER_NAME = "peer_name";

    private ActivityChatBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
        if (peerName == null) peerName = "Unknown";

        setTitle(peerName);
        binding.tvChatWith.setText("Chat with " + peerName);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        binding.tvMessages.append("Me: " + text + "\n");
        binding.etMessage.setText("");
        Log.d(TAG, "sent: " + text);
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
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}