package com.example.offthegrid;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(String peer);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(String peer);
    }

    private final List<Conversation> items = new ArrayList<>();
    private final OnConversationClickListener listener;
    private final OnConversationLongClickListener longListener;

    public ConversationAdapter(OnConversationClickListener listener,
                               OnConversationLongClickListener longListener) {
        this.listener = listener;
        this.longListener = longListener;
    }

    public void setConversations(List<Conversation> conversations) {
        items.clear();
        items.addAll(conversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation item = items.get(position);
        Context context = holder.itemView.getContext();
        holder.avatar.setText(initial(item.peer));
        holder.name.setText(item.peer);
        holder.preview.setText(item.lastFromMe
                ? context.getString(R.string.you_prefix, item.lastText)
                : item.lastText);
        holder.time.setText(formatTime(context, item.lastTimestamp));
        holder.itemView.setOnClickListener(v -> listener.onConversationClick(item.peer));
        holder.itemView.setOnLongClickListener(v -> {
            longListener.onConversationLongClick(item.peer);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String initial(String name) {
        return name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private static String formatTime(Context context, long timestamp) {
        Date date = new Date(timestamp);
        return DateUtils.isToday(timestamp)
                ? DateFormat.getTimeFormat(context).format(date)
                : DateFormat.getDateFormat(context).format(date);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView name;
        final TextView preview;
        final TextView time;

        ViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvConversationAvatar);
            name = itemView.findViewById(R.id.tvConversationName);
            preview = itemView.findViewById(R.id.tvConversationPreview);
            time = itemView.findViewById(R.id.tvConversationTime);
        }
    }
}