package com.example.offthegrid;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offthegrid.databinding.FragmentChatsBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatsFragment extends Fragment {

    private FragmentChatsBinding binding;
    private ConversationAdapter adapter;

    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            loadConversations();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        adapter = new ConversationAdapter(this::openChat, this::confirmDeleteChat);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().getContentResolver()
                .registerContentObserver(MessageStorage.CONTENT_URI, true, observer);
        loadConversations();
    }

    @Override
    public void onStop() {
        requireContext().getContentResolver().unregisterContentObserver(observer);
        super.onStop();
    }

    private void loadConversations() {
        if (binding == null) return;
        List<Conversation> list = new ArrayList<>();
        String latestPerPeer = MessageStorage.COL_ID + " IN (SELECT MAX(" + MessageStorage.COL_ID
                + ") FROM " + MessageStorage.TABLE + " GROUP BY " + MessageStorage.COL_PEER + ")";
        try (Cursor cursor = requireContext().getContentResolver().query(
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
                while (cursor.moveToNext()) {
                    list.add(new Conversation(
                            cursor.getString(peerIndex),
                            cursor.getString(textIndex),
                            cursor.getLong(timeIndex),
                            MessageStorage.ME.equals(cursor.getString(senderIndex))));
                }
            }
        }
        adapter.setConversations(list);
        binding.tvChatsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvChats.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
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