package com.example.offthegrid;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.ActivityChatBinding;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_PEER_NAME = "peer_name";
    private static final long PING_INTERVAL_MS = 5000;
    private static final long TYPING_RESEND_MS = 2500;
    private static final long TYPING_IDLE_MS = 4000;
    private static final int QUOTE_LIMIT = 80;

    public static volatile String visiblePeer = null;
    private ActivityChatBinding binding;
    private MessageAdapter adapter;
    private String peerName;
    private String pendingSave;
    private int pingGeneration;
    private boolean typingActive;
    private long newestShown = Long.MIN_VALUE;
    private long lastTypingSent;
    private final ExecutorService pinger = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService typingWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService reactWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable stopTyping = this::stopTypingNow;

    private final TextWatcher typingWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            main.removeCallbacks(stopTyping);
            if (s.toString().trim().isEmpty()) {
                stopTypingNow();
                return;
            }
            long now = System.currentTimeMillis();
            if (!typingActive || now - lastTypingSent > TYPING_RESEND_MS) {
                typingActive = true;
                lastTypingSent = now;
                sendTyping(true);
            }
            main.postDelayed(stopTyping, TYPING_IDLE_MS);
        }
    };

    private final TypingTracker.Listener typingListener = peer -> {
        if (peerName != null && peerName.equals(peer)) {
            main.post(this::refreshTitle);
        }
    };

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    onPhotoPicked(data.getData());
                }
            });

    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                String image = pendingSave;
                pendingSave = null;
                if (granted && image != null) {
                    saveToGallery(image);
                } else {
                    Toast.makeText(this, R.string.storage_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
            });

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

        adapter = new MessageAdapter(new MessageAdapter.OnMessageActionListener() {
            @Override
            public void onMessageLongClick(Message message) {
                showMessageActions(message);
            }

            @Override
            public void onMessageHeart(Message message) {
                toggleHeart(message);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnAttach.setOnClickListener(v -> pickPhoto());
        binding.chatHeader.setOnClickListener(v -> openProfile());
        binding.etMessage.addTextChangedListener(typingWatcher);
    }

    private void refreshTitle() {
        binding.tvChatWith.setText(peerName);
        boolean typing = TypingTracker.isTyping(peerName);
        boolean atBottom = !binding.rvMessages.canScrollVertically(1);
        adapter.setTyping(typing);
        if (typing && atBottom) {
            binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void applyAvatar() {
        if (isFinishing() || isDestroyed()) return;
        Bitmap bitmap = AvatarCache.cached(peerName);
        if (bitmap != null) {
            binding.ivChatAvatar.setImageBitmap(bitmap);
            binding.ivChatAvatar.setVisibility(View.VISIBLE);
        } else {
            binding.ivChatAvatar.setVisibility(View.GONE);
        }
    }

    private void sendTyping(boolean typing) {
        Context app = getApplicationContext();
        String peer = peerName;
        typingWorker.execute(() -> PeerClient.sendTyping(app, peer, typing));
    }

    private void stopTypingNow() {
        main.removeCallbacks(stopTyping);
        if (typingActive) {
            typingActive = false;
            sendTyping(false);
        }
    }

    private void openProfile() {
        Intent intent = new Intent(this, OtherProfileActivity.class);
        intent.putExtra(EXTRA_PEER_NAME, peerName);
        startActivity(intent);
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        picker.launch(Intent.createChooser(intent, getString(R.string.pick_photo)));
    }

    private void onPhotoPicked(Uri uri) {
        if (uri == null) return;
        Context app = getApplicationContext();
        String peer = peerName;
        imageWorker.execute(() -> {
            String name = ImageStore.saveFromUri(app, uri);
            if (name == null) {
                main.post(() -> Toast.makeText(app, R.string.photo_failed,
                        Toast.LENGTH_LONG).show());
                return;
            }
            Intent intent = new Intent(app, OffTheGridService.class);
            intent.setAction(OffTheGridService.ACTION_SEND_IMAGE);
            intent.putExtra(OffTheGridService.EXTRA_PEER, peer);
            intent.putExtra(OffTheGridService.EXTRA_IMAGE, name);
            try {
                app.startService(intent);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Could not start service for photo", e);
                ImageStore.delete(app, name);
            }
        });
    }

    private void pingLoop(int generation) {
        pinger.execute(() -> {
            boolean online = PeerClient.ping(getApplicationContext(), peerName);
            main.post(() -> {
                if (generation != pingGeneration) return;
                binding.statusView.setConnected(online);
                applyAvatar();
                if (online) {
                    if (adapter.hasPending()) {
                        retryPending();
                    }
                    flushReactions();
                }
                main.postDelayed(() -> {
                    if (generation == pingGeneration) {
                        pingLoop(generation);
                    }
                }, PING_INTERVAL_MS);
            });
        });
    }

    private void retryPending() {
        Intent intent = new Intent(this, OffTheGridService.class);
        intent.setAction(OffTheGridService.ACTION_RETRY);
        intent.putExtra(OffTheGridService.EXTRA_PEER, peerName);
        startService(intent);
    }

    private void loadMessages() {
        List<Message> list = new ArrayList<>();
        Map<String, MessageMetaStore.Reply> replies = MessageMetaStore.loadReplies(this);
        Map<String, Set<String>> hearts = MessageMetaStore.loadHearts(this);
        String myId = Profile.getId(this);
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
                int statusIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_STATUS);
                int imageIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_IMAGE);
                int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
                while (cursor.moveToNext()) {
                    boolean sent = MessageStorage.ME.equals(cursor.getString(senderIndex));
                    String uid = cursor.getString(uidIndex);
                    MessageMetaStore.Reply reply = uid == null ? null : replies.get(uid);
                    Set<String> people = uid == null ? null : hearts.get(uid);
                    boolean mine = people != null && people.contains(myId);
                    boolean theirs = false;
                    if (people != null) {
                        for (String who : people) {
                            if (!who.equals(myId)) {
                                theirs = true;
                                break;
                            }
                        }
                    }
                    list.add(new Message(cursor.getLong(idIndex), uid,
                            cursor.getString(textIndex), sent, cursor.getInt(statusIndex),
                            cursor.getString(imageIndex), reply == null ? null : reply.quote,
                            mine, theirs, cursor.getLong(timeIndex)));
                }
            }
        }
        long newest = list.isEmpty() ? -1 : list.get(list.size() - 1).getId();
        boolean stickToBottom = newest != newestShown;
        newestShown = newest;
        adapter.setMessages(list);
        if (stickToBottom) {
            binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        }
        ChatStateStore.markRead(this, peerName);
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        startSend(text, null, null);
        binding.etMessage.setText("");
        stopTypingNow();
    }

    private void startSend(String text, String replyId, String quote) {
        Intent intent = new Intent(this, OffTheGridService.class);
        intent.setAction(OffTheGridService.ACTION_SEND);
        intent.putExtra(OffTheGridService.EXTRA_PEER, peerName);
        intent.putExtra(OffTheGridService.EXTRA_TEXT, text);
        if (replyId != null) {
            intent.putExtra(OffTheGridService.EXTRA_REPLY_ID, replyId);
            intent.putExtra(OffTheGridService.EXTRA_REPLY_QUOTE, quote);
        }
        startService(intent);
    }

    private void toggleHeart(Message message) {
        if (!message.canHeart()) return;
        Context app = getApplicationContext();
        String myId = Profile.getId(app);
        String uid = message.getUid();
        String peer = peerName;
        boolean on = !message.isHeartedByMe();
        MessageMetaStore.setHeart(app, uid, myId, on);
        PendingReactions.put(app, peer, uid, on);
        loadMessages();
        flushReactions();
    }

    private void flushReactions() {
        Context app = getApplicationContext();
        String peer = peerName;
        reactWorker.execute(() -> {
            for (PendingReactions.Item item : PendingReactions.list(app, peer)) {
                if (!MessageMetaStore.messageExists(app, peer, item.uid)) {
                    PendingReactions.remove(app, peer, item.uid, item.on);
                    continue;
                }
                boolean ok = PeerClient.sendReaction(app, peer, item.uid,
                        item.on ? MessageMetaStore.HEART : "", "msg");
                if (!ok) return;
                PendingReactions.remove(app, peer, item.uid, item.on);
            }
        });
    }

    private String quoteOf(Message message) {
        if (message.hasImage()) return getString(R.string.photo_preview);
        String text = message.getText() == null ? "" : message.getText().trim();
        return text.length() > QUOTE_LIMIT ? text.substring(0, QUOTE_LIMIT) + "…" : text;
    }

    private void showReplyDialog(Message message) {
        String quote = quoteOf(message);
        EditText input = new EditText(this);
        input.setHint(R.string.reply_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.reply_title)
                .setMessage("↩ " + quote)
                .setPositiveButton(R.string.send, (d, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        startSend(text, message.getUid(), quote);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        dialog.setView(input, pad, pad / 2, pad, 0);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    private void copyText(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.chat_copy_done, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMessageActions(Message message) {
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (message.getUid() != null) {
            labels.add(getString(R.string.reply_action));
            actions.add(() -> showReplyDialog(message));
        }
        String text = message.getText();
        if (!message.hasImage() && text != null && !text.trim().isEmpty()) {
            labels.add(getString(R.string.chat_copy_action));
            actions.add(() -> copyText(text));
        }
        if (message.canHeart()) {
            labels.add(getString(message.isHeartedByMe()
                    ? R.string.action_unheart : R.string.action_heart));
            actions.add(() -> toggleHeart(message));
        }
        if (message.hasImage()) {
            labels.add(getString(R.string.save_to_gallery));
            actions.add(() -> requestSave(message.getImage()));
        }
        labels.add(getString(R.string.delete_for_me));
        actions.add(() -> deleteForMe(message));
        if (message.isSent() && message.getUid() != null) {
            labels.add(getString(R.string.delete_for_everyone));
            actions.add(() -> unsend(message));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.message_actions_title)
                .setItems(labels.toArray(new CharSequence[0]),
                        (dialog, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void requestSave(String image) {
        boolean needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        if (needsPermission) {
            pendingSave = image;
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            saveToGallery(image);
        }
    }

    private void saveToGallery(String image) {
        Context app = getApplicationContext();
        imageWorker.execute(() -> {
            boolean ok = GallerySaver.save(app, image);
            main.post(() -> Toast.makeText(app,
                    ok ? R.string.photo_saved : R.string.photo_save_failed,
                    Toast.LENGTH_SHORT).show());
        });
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

    private void exportXml() {
        Context app = getApplicationContext();
        String peer = peerName;
        imageWorker.execute(() -> {
            ChatXml.Summary summary = null;
            try {
                summary = ChatXml.export(app, peer);
            } catch (IOException | XmlPullParserException e) {
                Log.w(TAG, "XML export failed", e);
            }
            ChatXml.Summary result = summary;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.export_done_title)
                        .setMessage(getString(R.string.export_done_body, result.total,
                                result.sent, result.received, result.path))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
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
        if (item.getItemId() == R.id.action_export_xml) {
            exportXml();
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
        TypingTracker.addListener(typingListener);
        refreshTitle();
        applyAvatar();
        AvatarCache.request(this, peerName, this::applyAvatar);
        loadMessages();
        pingGeneration++;
        pingLoop(pingGeneration);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visiblePeer = peerName;
        ChatStateStore.markRead(this, peerName);
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
        stopTypingNow();
        TypingTracker.removeListener(typingListener);
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
        imageWorker.shutdownNow();
        typingWorker.shutdown();
        reactWorker.shutdown();
        super.onDestroy();
    }
}