package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
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

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    private final List<Post> posts = new ArrayList<>();
    private final OnPostClickListener listener;

    public PostAdapter(OnPostClickListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<Post> newPosts) {
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
        Post post = posts.get(position);
        Context context = holder.itemView.getContext();
        Bitmap bitmap = post.hasImage() ? ImageStore.thumbnail(context, post.image) : null;
        if (bitmap != null) {
            holder.image.setImageBitmap(bitmap);
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
        holder.date.setText(
                DateFormat.getMediumDateFormat(context).format(new Date(post.timestamp)));
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