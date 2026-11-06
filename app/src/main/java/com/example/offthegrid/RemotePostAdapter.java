package com.example.offthegrid;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RemotePostAdapter extends RecyclerView.Adapter<RemotePostAdapter.ViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(RemoteProfile.RemotePost post);
    }

    private final List<RemoteProfile.RemotePost> posts = new ArrayList<>();
    private final OnPostClickListener listener;

    public RemotePostAdapter(OnPostClickListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<RemoteProfile.RemotePost> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RemoteProfile.RemotePost post = posts.get(position);
        if (post.image != null) {
            holder.image.setImageBitmap(post.image);
            holder.image.setVisibility(View.VISIBLE);
            holder.note.setVisibility(View.GONE);
            holder.caption.setText(post.text);
            holder.caption.setVisibility(post.text.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.note.setText(post.text);
            holder.note.setVisibility(View.VISIBLE);
            holder.caption.setVisibility(View.GONE);
        }
        holder.date.setText(DateFormat.getMediumDateFormat(holder.itemView.getContext())
                .format(new Date(post.timestamp)));
        holder.badge.setVisibility(
                post.visibility == PostStorage.VISIBILITY_FRIENDS ? View.VISIBLE : View.GONE);
        holder.itemView.setRotation(position % 2 == 0 ? -2f : 2f);
        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView note;
        final TextView caption;
        final TextView date;
        final TextView badge;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivPostImage);
            note = itemView.findViewById(R.id.tvPostNote);
            caption = itemView.findViewById(R.id.tvPostText);
            date = itemView.findViewById(R.id.tvPostDate);
            badge = itemView.findViewById(R.id.tvPostBadge);
        }
    }
}