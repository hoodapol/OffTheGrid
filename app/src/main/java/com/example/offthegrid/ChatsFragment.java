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

public class ChatsFragment extends Fragment implements FriendStore.Listener {

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
        adapter = new ConversationAdapter(this::openChat, this::confirmDeleteChat,
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
        loadRows();
        FriendSync.syncOnline(requireContext());
    }

    @Override
    public void onStop() {
        requireContext().getContentResolver().unregisterContentObserver(observer);
        FriendStore.removeListener(this);
        super.onStop();
    }

    @Override
    public void onFriendsChanged() {
        loadRows();
    }

    private void loadRows() {
        if (binding == null) return;
        Context context = requireContext();
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

        List<ConversationAdapter.Row> rows = new ArrayList<>();

        List<FriendStore.Friend> requests =
                FriendStore.list(context, FriendStore.STATE_PENDING_IN);
        if (!requests.isEmpty()) {
            rows.add(ConversationAdapter.Row.header(getString(R.string.section_requests)));
            for (FriendStore.Friend request : requests) {
                rows.add(ConversationAdapter.Row.request(request));
            }
        }

        Set<String> friendNames = new HashSet<>();
        List<Conversation> friendChats = new ArrayList<>();
        for (FriendStore.Friend friend : FriendStore.list(context, FriendStore.STATE_FRIEND)) {
            friendNames.add(friend.name);
            Conversation conversation = conversations.get(friend.name);
            friendChats.add(conversation != null
                    ? conversation : new Conversation(friend.name, "", 0, false));
        }
        Collections.sort(friendChats,
                (a, b) -> Long.compare(b.lastTimestamp, a.lastTimestamp));
        if (!friendChats.isEmpty()) {
            rows.add(ConversationAdapter.Row.header(getString(R.string.section_friends)));
            for (Conversation conversation : friendChats) {
                rows.add(ConversationAdapter.Row.chat(conversation));
            }
        }

        List<Conversation> others = new ArrayList<>();
        for (Conversation conversation : conversations.values()) {
            if (!friendNames.contains(conversation.peer)) {
                others.add(conversation);
            }
        }
        if (!others.isEmpty()) {
            if (!friendChats.isEmpty() || !requests.isEmpty()) {
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