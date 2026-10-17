package com.example.offthegrid;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityChatBinding;
import androidx.recyclerview.widget.LinearLayoutManager;
public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_PEER_NAME = "peer_name";

    private ActivityChatBinding binding;
    private MessageAdapter adapter;

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

        adapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);
        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        adapter.add(new Message(text, true));
        binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        binding.etMessage.setText("");
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