package com.example.offthegrid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.ActivityChatBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_PEER_NAME = "peer_name";
    private static final long PING_INTERVAL_MS = 5000;

    public static volatile String visiblePeer = null;
    private ActivityChatBinding binding;
    private MessageAdapter adapter;
    private String peerName;
    private int pingGeneration;
    private final ExecutorService pinger = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

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

        adapter = new MessageAdapter(this::confirmDeleteMessage);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void pingLoop(int generation) {
        pinger.execute(() -> {
            boolean online = PeerClient.ping(getApplicationContext(), peerName);
            main.post(() -> {
                if (generation != pingGeneration) return;
                binding.statusView.setConnected(online);
                main.postDelayed(() -> {
                    if (generation == pingGeneration) {
                        pingLoop(generation);
                    }
                }, PING_INTERVAL_MS);
            });
        });
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
                int idIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_ID);
                int uidIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_UID);
                int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
                int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
                while (cursor.moveToNext()) {
                    boolean sent = MessageStorage.ME.equals(cursor.getString(senderIndex));
                    list.add(new Message(cursor.getLong(idIndex), cursor.getString(uidIndex),
                            cursor.getString(textIndex), sent));
                }
            }
        }
        adapter.setMessages(list);
        binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
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

    private void confirmDeleteMessage(Message message) {
        if (message.isSent() && message.getUid() != null) {
            CharSequence[] choices = {
                    getString(R.string.delete_for_me),
                    getString(R.string.delete_for_everyone)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_message_title)
                    .setItems(choices, (dialog, which) -> {
                        if (which == 0) {
                            deleteForMe(message);
                        } else {
                            unsend(message);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_message_title)
                    .setMessage(R.string.delete_message_body)
                    .setPositiveButton(R.string.delete, (dialog, which) -> deleteForMe(message))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void deleteForMe(Message message) {
        MessageSink.deleteMessage(this, message.getId());
        loadMessages();
    }

    private void unsend(Message message) {
        Intent intent = new Intent(this, OffTheGridService.class);
        intent.setAction(OffTheGridService.ACTION_UNSEND);
        intent.putExtra(OffTheGridService.EXTRA_PEER, peerName);
        intent.putExtra(OffTheGridService.EXTRA_UID, message.getUid());
        intent.putExtra(OffTheGridService.EXTRA_ROW_ID, message.getId());
        startService(intent);
    }

    private void confirmDeleteChat() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_chat_title, peerName))
                .setMessage(R.string.delete_chat_body)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    MessageSink.deleteChat(this, peerName);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete_chat) {
            confirmDeleteChat();
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
        pingGeneration++;
        pingLoop(pingGeneration);
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
        pingGeneration++;
        main.removeCallbacksAndMessages(null);
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
        pinger.shutdownNow();
        super.onDestroy();
    }
}