package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offthegrid.databinding.ActivityFriendsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FriendsActivity extends AppCompatActivity
        implements FriendStore.Listener, PeerRegistry.Listener {

    private ActivityFriendsBinding binding;
    private FriendAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnFriendsBack.setOnClickListener(v -> finish());
        adapter = new FriendAdapter(new FriendAdapter.OnFriendClickListener() {
            @Override
            public void onProfile(String name) {
                openProfile(name);
            }

            @Override
            public void onUnfriend(String name) {
                confirmUnfriend(name);
            }
        });
        binding.rvFriends.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFriends.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FriendStore.addListener(this);
        PeerRegistry.addListener(this);
        load();
    }

    @Override
    protected void onStop() {
        FriendStore.removeListener(this);
        PeerRegistry.removeListener(this);
        super.onStop();
    }

    @Override
    public void onFriendsChanged() {
        main.post(this::load);
    }

    @Override
    public void onPeersChanged() {
        main.post(this::load);
    }

    private void load() {
        if (binding == null) return;
        List<FriendAdapter.Row> rows = new ArrayList<>();
        for (FriendStore.Friend friend : FriendStore.list(this, FriendStore.STATE_FRIEND)) {
            rows.add(new FriendAdapter.Row(friend.name, PeerRegistry.contains(friend.name)));
        }
        Collections.sort(rows, (a, b) -> {
            if (a.online != b.online) return a.online ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        adapter.setRows(rows);
        binding.layoutFriendsEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvFriends.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmUnfriend(String name) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.friend_unfriend_title, name))
                .setMessage(R.string.friend_unfriend_body)
                .setPositiveButton(R.string.friend_unfriend, (dialog, which) -> unfriend(name))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void unfriend(String name) {
        Context app = getApplicationContext();
        FriendStore.Friend friend = FriendStore.findByName(app, name);
        if (friend == null) return;
        String device = friend.device;
        String key = PeerRegistry.contains(name) ? name : PeerRegistry.keyFor(device);
        FriendStore.remove(app, device);
        if (key != null) {
            new Thread(() -> PeerClient.sendFriend(app, key, FriendActions.REMOVE)).start();
        }
    }

    private void openProfile(String name) {
        Intent intent = new Intent(this, OtherProfileActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, name);
        startActivity(intent);
    }

    private static final class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.Holder> {

        interface OnFriendClickListener {
            void onProfile(String name);

            void onUnfriend(String name);
        }

        static final class Row {
            final String name;
            final boolean online;

            Row(String name, boolean online) {
                this.name = name;
                this.online = online;
            }
        }

        private final List<Row> rows = new ArrayList<>();
        private final OnFriendClickListener listener;

        FriendAdapter(OnFriendClickListener listener) {
            this.listener = listener;
        }

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            Context context = holder.itemView.getContext();
            holder.name.setText(row.name);
            holder.dot.setVisibility(row.online ? View.VISIBLE : View.GONE);
            holder.status.setText(row.online
                    ? R.string.friends_status_online : R.string.friends_status_offline);
            Bitmap bitmap = AvatarCache.cached(row.name);
            if (bitmap != null) {
                holder.avatar.setImageBitmap(bitmap);
                holder.avatar.setVisibility(View.VISIBLE);
                holder.initial.setVisibility(View.GONE);
            } else {
                holder.avatar.setVisibility(View.GONE);
                holder.initial.setVisibility(View.VISIBLE);
                holder.initial.setText(row.name.isEmpty()
                        ? "" : row.name.substring(0, 1).toUpperCase(Locale.getDefault()));
            }
            holder.itemView.setOnClickListener(v -> listener.onProfile(row.name));
            holder.unfriend.setOnClickListener(v -> listener.onUnfriend(row.name));
            AvatarCache.request(context, row.name, () -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) {
                    notifyItemChanged(index);
                }
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView unfriend;
            final TextView initial;
            final ImageView avatar;
            final TextView name;
            final View dot;
            final TextView status;

            Holder(View itemView) {
                super(itemView);
                unfriend = itemView.findViewById(R.id.btnFriendUnfriend);
                initial = itemView.findViewById(R.id.tvFriendInitial);
                avatar = itemView.findViewById(R.id.ivFriendAvatar);
                name = itemView.findViewById(R.id.tvFriendName);
                dot = itemView.findViewById(R.id.dotFriend);
                status = itemView.findViewById(R.id.tvFriendStatus);
            }
        }
    }
}