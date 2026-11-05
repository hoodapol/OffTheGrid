package com.example.offthegrid;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AvatarTileAdapter extends RecyclerView.Adapter<AvatarTileAdapter.ViewHolder> {

    public interface OnTileClickListener {
        void onTileClick(Bitmap bitmap);
    }

    private final Bitmap[] bitmaps;
    private final OnTileClickListener listener;

    public AvatarTileAdapter(int count, OnTileClickListener listener) {
        this.bitmaps = new Bitmap[count];
        this.listener = listener;
    }

    public void clear() {
        for (int i = 0; i < bitmaps.length; i++) {
            bitmaps[i] = null;
        }
        notifyDataSetChanged();
    }

    public void setBitmap(int position, Bitmap bitmap) {
        bitmaps[position] = bitmap;
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_avatar_tile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Bitmap bitmap = bitmaps[position];
        if (bitmap != null) {
            holder.image.setImageBitmap(bitmap);
            holder.image.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(v -> listener.onTileClick(bitmap));
        } else {
            holder.image.setImageDrawable(null);
            holder.image.setVisibility(View.INVISIBLE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return bitmaps.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivAvatarTile);
        }
    }
}