package com.example.offthegrid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {
    public interface OnPeerClickListener {
        void onPeerClick(String peer);
    }

    private final List<String> peers = new ArrayList<>();
    private final OnPeerClickListener listener;

    public PeerAdapter(OnPeerClickListener listener) {
        this.listener = listener;
    }

    public void add(String peer) {
        peers.add(peer);
        notifyItemInserted(peers.size() - 1);
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
        holder.name.setText(peer);
        holder.itemView.setOnClickListener(v -> listener.onPeerClick(peer));
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvPeerName);
        }
    }
}