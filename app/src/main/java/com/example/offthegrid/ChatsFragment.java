package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.FragmentChatsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatsFragment extends Fragment
        implements FriendStore.Listener, ChatStateStore.Listener, PeerRegistry.Listener {

    private FragmentChatsBinding binding;
    private ConversationAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            loadRows();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        adapter = new ConversationAdapter(this::openChat, this::showChatOptions,
                new ConversationAdapter.OnRequestListener() {
                    @Override
                    public void onAccept(FriendStore.Friend friend) {
                        acceptRequest(friend);
                    }

                    @Override
                    public void onDecline(FriendStore.Friend friend) {
                        declineRequest(friend);
                    }
                });
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().getContentResolver()
                .registerContentObserver(MessageStorage.CONTENT_URI, true, observer);
        FriendStore.addListener(this);
        ChatStateStore.addListener(this);
        PeerRegistry.addListener(this);
        ChatStateStore.startTracking(requireContext());
        loadRows();
        FriendSync.syncOnline(requireContext());
    }

    @Override
    public void onStop() {
        requireContext().getContentResolver().unregisterContentObserver(observer);
        FriendStore.removeListener(this);
        ChatStateStore.removeListener(this);
        PeerRegistry.removeListener(this);
        super.onStop();
    }

    @Override
    public void onFriendsChanged() {
        loadRows();
    }

    @Override
    public void onChatStateChanged() {
        loadRows();
    }

    @Override
    public void onPeersChanged() {
        main.post(this::loadRows);
    }

    private void loadRows() {
        Context context = getContext();
        if (binding == null || context == null) return;
        Map<String, Conversation> conversations = new LinkedHashMap<>();
        String latestPerPeer = MessageStorage.COL_ID + " IN (SELECT MAX(" + MessageStorage.COL_ID
                + ") FROM " + MessageStorage.TABLE + " GROUP BY " + MessageStorage.COL_PEER + ")";
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                null,
                latestPerPeer,
                null,
                MessageStorage.COL_TIMESTAMP + " DESC, " + MessageStorage.COL_ID + " DESC")) {
            if (cursor != null) {
                int peerIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_PEER);
                int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
                int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
                int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
                int imageIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_IMAGE);
                while (cursor.moveToNext()) {
                    String preview = cursor.isNull(imageIndex)
                            ? cursor.getString(textIndex)
                            : getString(R.string.photo_preview);
                    String peer = cursor.getString(peerIndex);
                    conversations.put(peer, new Conversation(
                            peer,
                            preview,
                            cursor.getLong(timeIndex),
                            MessageStorage.ME.equals(cursor.getString(senderIndex))));
                }
            }
        }

        Set<String> friendNames = new HashSet<>();
        for (FriendStore.Friend friend : FriendStore.list(context, FriendStore.STATE_FRIEND)) {
            friendNames.add(friend.name);
            if (!conversations.containsKey(friend.name)) {
                conversations.put(friend.name, new Conversation(friend.name, "", 0, false));
            }
        }

        List<Conversation> pinned = new ArrayList<>();
        List<Conversation> friends = new ArrayList<>();
        List<Conversation> others = new ArrayList<>();
        for (Conversation conversation : conversations.values()) {
            decorate(context, conversation);
            if (conversation.pinned) {
                pinned.add(conversation);
            } else if (friendNames.contains(conversation.peer)) {
                friends.add(conversation);
            } else {
                others.add(conversation);
            }
        }
        sortByRecent(pinned);
        sortByRecent(friends);
        sortByRecent(others);

        List<ConversationAdapter.Row> rows = new ArrayList<>();

        List<FriendStore.Friend> requests =
                FriendStore.list(context, FriendStore.STATE_PENDING_IN);
        if (!requests.isEmpty()) {
            rows.add(ConversationAdapter.Row.header(getString(R.string.section_requests)));
            for (FriendStore.Friend request : requests) {
                rows.add(ConversationAdapter.Row.request(request));
            }
        }
        if (!pinned.isEmpty()) {
            rows.add(ConversationAdapter.Row.header(getString(R.string.section_pinned)));
            for (Conversation conversation : pinned) {
                rows.add(ConversationAdapter.Row.chat(conversation));
            }
        }
        if (!friends.isEmpty()) {
            rows.add(ConversationAdapter.Row.header(getString(R.string.section_friends)));
            for (Conversation conversation : friends) {
                rows.add(ConversationAdapter.Row.chat(conversation));
            }
        }
        if (!others.isEmpty()) {
            if (!rows.isEmpty()) {
                rows.add(ConversationAdapter.Row.header(getString(R.string.section_others)));
            }
            for (Conversation conversation : others) {
                rows.add(ConversationAdapter.Row.chat(conversation));
            }
        }

        adapter.setRows(rows);
        binding.tvChatsEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvChats.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void decorate(Context context, Conversation conversation) {
        ChatStateStore.State state = ChatStateStore.get(context, conversation.peer);
        conversation.pinned = state.pinned;
        conversation.muted = state.muted;
        conversation.unread = ChatStateStore.countUnread(
                context, conversation.peer, state.lastRead);
        conversation.online = PeerRegistry.contains(conversation.peer);
        long lastReceived = conversation.lastFromMe ? 0 : conversation.lastTimestamp;
        conversation.lastSeen = Math.max(state.lastSeen, lastReceived);
    }

    private void sortByRecent(List<Conversation> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.lastTimestamp, a.lastTimestamp));
    }

    private void acceptRequest(FriendStore.Friend friend) {
        String key = PeerRegistry.keyFor(friend.device);
        if (key == null) {
            Toast.makeText(requireContext(), R.string.friend_need_nearby,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            boolean ok = PeerClient.sendFriend(app, key, FriendActions.ACCEPT);
            main.post(() -> {
                if (ok) {
                    FriendStore.upsert(app, friend.device, key, FriendStore.STATE_FRIEND);
                    FriendSync.syncOnline(app);
                } else {
                    Toast.makeText(app, R.string.friend_request_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void declineRequest(FriendStore.Friend friend) {
        String key = PeerRegistry.keyFor(friend.device);
        Context app = requireContext().getApplicationContext();
        if (key != null) {
            new Thread(() -> PeerClient.sendFriend(app, key, FriendActions.DECLINE)).start();
        }
        FriendStore.remove(app, friend.device);
    }

    private void showChatOptions(String peer) {
        Context context = requireContext();
        ChatStateStore.State state = ChatStateStore.get(context, peer);
        CharSequence[] items = {
                getString(state.pinned ? R.string.chat_unpin : R.string.chat_pin),
                getString(state.muted ? R.string.chat_unmute : R.string.chat_mute),
                getString(R.string.chat_delete_option)
        };
        new AlertDialog.Builder(context)
                .setTitle(peer)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        ChatStateStore.setPinned(context, peer, !state.pinned);
                    } else if (which == 1) {
                        ChatStateStore.setMuted(context, peer, !state.muted);
                    } else {
                        confirmDeleteChat(peer);
                    }
                })
                .show();
    }

    private void confirmDeleteChat(String peer) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_chat_title, peer))
                .setMessage(R.string.delete_chat_body)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        MessageSink.deleteChat(requireContext(), peer))
                .setNegativeButton(R.string.cancel, null)
                .show();
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