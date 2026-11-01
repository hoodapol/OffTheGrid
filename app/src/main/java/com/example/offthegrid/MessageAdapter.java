package com.example.offthegrid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message);
    }

    private static final int TYPE_SENT = 0;
    private static final int TYPE_RECEIVED = 1;
    private static final int TYPE_NOTE = 2;

    private final List<Message> messages = new ArrayList<>();
    private final OnMessageLongClickListener listener;

    public MessageAdapter(OnMessageLongClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == TYPE_NOTE) {
            layout = R.layout.item_chat_note;
        } else if (viewType == TYPE_SENT) {
            layout = R.layout.item_message_sent;
        } else {
            layout = R.layout.item_message_received;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.text == null) return;
        Message message = messages.get(position - 1);
        holder.text.setText(message.getText());
        holder.text.setOnLongClickListener(v -> {
            listener.onMessageLongClick(message);
            return true;
        });
        if (holder.status != null) {
            boolean last = position == messages.size();
            if (message.isPending()) {
                holder.status.setText(R.string.status_pending);
                holder.status.setVisibility(View.VISIBLE);
            } else if (last) {
                holder.status.setText(R.string.status_delivered);
                holder.status.setVisibility(View.VISIBLE);
            } else {
                holder.status.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_NOTE;
        return messages.get(position - 1).isSent() ? TYPE_SENT : TYPE_RECEIVED;
    }

    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public boolean hasPending() {
        for (Message message : messages) {
            if (message.isPending()) return true;
        }
        return false;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView status;

        ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvText);
            status = itemView.findViewById(R.id.tvStatus);
        }
    }
}