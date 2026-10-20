package com.example.offthegrid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.ActivityChatBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_PEER_NAME = "peer_name";

    public static volatile String visiblePeer = null;
    private ActivityChatBinding binding;
    private MessageAdapter adapter;
    private String peerName;
    private boolean connected = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String peer = intent.getStringExtra(OffTheGridService.EXTRA_PEER);
            if (OffTheGridService.EVENT_MESSAGE_SAVED.equals(intent.getAction())
                    && peerName.equals(peer)) {
                loadMessages();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
        if (peerName == null) peerName = "Unknown";

        setTitle(peerName);
        binding.tvChatWith.setText(peerName);

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

    private void loadMessages() {
        List<Message> list = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                MessageStorage.CONTENT_URI,
                null,
                MessageStorage.COL_PEER + "=?",
                new String[]{peerName},
                MessageStorage.COL_TIMESTAMP + " ASC, " + MessageStorage.COL_ID + " ASC")) {
            if (cursor != null) {
                int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
                int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
                while (cursor.moveToNext()) {
                    boolean sent = MessageStorage.ME.equals(cursor.getString(senderIndex));
                    list.add(new Message(cursor.getString(textIndex), sent));
                }
            }
        }
        adapter.setMessages(list);
        if (adapter.getItemCount() > 0) {
            binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        Intent intent = new Intent(this, OffTheGridService.class);
        intent.setAction(OffTheGridService.ACTION_SEND);
        intent.putExtra(OffTheGridService.EXTRA_PEER, peerName);
        intent.putExtra(OffTheGridService.EXTRA_TEXT, text);
        startService(intent);
        binding.etMessage.setText("");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_incoming) {
            Intent intent = new Intent(this, OffTheGridService.class);
            intent.setAction(OffTheGridService.ACTION_SIMULATE_INCOMING);
            intent.putExtra(OffTheGridService.EXTRA_PEER, peerName);
            intent.putExtra(OffTheGridService.EXTRA_DELAY_MS, 5000L);
            startService(intent);
            return true;
        }
        if (item.getItemId() == R.id.action_toggle) {
            connected = !connected;
            binding.statusView.setConnected(connected);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        IntentFilter filter = new IntentFilter(OffTheGridService.EVENT_MESSAGE_SAVED);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visiblePeer = peerName;
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        visiblePeer = null;
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        unregisterReceiver(receiver);
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