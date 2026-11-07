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

public class PeerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnPeerClickListener {
        void onPeerClick(String peer);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PEER = 1;

    private static final class Row {
        final int type;
        final String value;
        final int title;

        Row(int type, String value, int title) {
            this.type = type;
            this.value = value;
            this.title = title;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnPeerClickListener listener;

    public PeerAdapter(OnPeerClickListener listener) {
        this.listener = listener;
    }

    public void setAll(List<String> friends, List<String> others) {
        rows.clear();
        if (!friends.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, null, R.string.section_friends));
            for (String peer : friends) {
                rows.add(new Row(TYPE_PEER, peer, 0));
            }
        }
        if (!others.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, null, R.string.section_others));
            for (String peer : others) {
                rows.add(new Row(TYPE_PEER, peer, 0));
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        }
        return new ViewHolder(inflater.inflate(R.layout.item_peer, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_HEADER) {
            ((HeaderHolder) viewHolder).title.setText(row.title);
            return;
        }
        ViewHolder holder = (ViewHolder) viewHolder;
        String peer = row.value;
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
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvSectionTitle);
        }
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