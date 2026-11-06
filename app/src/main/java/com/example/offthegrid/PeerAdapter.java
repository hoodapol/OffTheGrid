package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {

    public interface OnPeerClickListener {
        void onPeerClick(String peer);
    }

    private final List<String> peers = new ArrayList<>();
    private final OnPeerClickListener listener;

    public PeerAdapter(OnPeerClickListener listener) {
        this.listener = listener;
    }

    public void setAll(List<String> newPeers) {
        peers.clear();
        peers.addAll(newPeers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String peer = peers.get(position);
        Context context = holder.itemView.getContext();
        holder.name.setText(peer);
        showAvatar(holder, peer);
        holder.itemView.setOnClickListener(v -> listener.onPeerClick(peer));
        holder.avatarArea.setOnClickListener(v -> openProfile(context, peer));
        AvatarCache.request(context, peer, () -> {
            int index = holder.getBindingAdapterPosition();
            if (index != RecyclerView.NO_POSITION) {
                notifyItemChanged(index);
            }
        });
    }

    private void showAvatar(ViewHolder holder, String peer) {
        Bitmap bitmap = AvatarCache.cached(peer);
        if (bitmap != null) {
            holder.avatar.setImageBitmap(bitmap);
            holder.avatar.setVisibility(View.VISIBLE);
            holder.initial.setVisibility(View.GONE);
        } else {
            holder.avatar.setVisibility(View.GONE);
            holder.initial.setVisibility(View.VISIBLE);
            holder.initial.setText(peer.isEmpty()
                    ? "" : peer.substring(0, 1).toUpperCase(Locale.getDefault()));
        }
    }

    private void openProfile(Context context, String peer) {
        Intent intent = new Intent(context, OtherProfileActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        context.startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View avatarArea;
        final TextView initial;
        final ImageView avatar;
        final TextView name;

        ViewHolder(View itemView) {
            super(itemView);
            avatarArea = itemView.findViewById(R.id.avatarArea);
            initial = itemView.findViewById(R.id.tvPeerInitial);
            avatar = itemView.findViewById(R.id.ivPeerAvatar);
            name = itemView.findViewById(R.id.tvPeerName);
        }
    }
}