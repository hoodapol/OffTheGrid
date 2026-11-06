package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(String peer);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(String peer);
    }

    public interface OnRequestListener {
        void onAccept(FriendStore.Friend friend);

        void onDecline(FriendStore.Friend friend);
    }

    public static final class Row {
        public static final int HEADER = 0;
        public static final int REQUEST = 1;
        public static final int CHAT = 2;

        final int type;
        final String title;
        final FriendStore.Friend friend;
        final Conversation conversation;

        private Row(int type, String title, FriendStore.Friend friend,
                    Conversation conversation) {
            this.type = type;
            this.title = title;
            this.friend = friend;
            this.conversation = conversation;
        }

        public static Row header(String title) {
            return new Row(HEADER, title, null, null);
        }

        public static Row request(FriendStore.Friend friend) {
            return new Row(REQUEST, null, friend, null);
        }

        public static Row chat(Conversation conversation) {
            return new Row(CHAT, null, null, conversation);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnConversationClickListener listener;
    private final OnConversationLongClickListener longListener;
    private final OnRequestListener requestListener;

    public ConversationAdapter(OnConversationClickListener listener,
                               OnConversationLongClickListener longListener,
                               OnRequestListener requestListener) {
        this.listener = listener;
        this.longListener = longListener;
        this.requestListener = requestListener;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
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
        if (viewType == Row.HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        }
        if (viewType == Row.REQUEST) {
            return new RequestHolder(inflater.inflate(R.layout.item_friend_request, parent, false));
        }
        return new ChatHolder(inflater.inflate(R.layout.item_conversation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(row.title);
        } else if (holder instanceof RequestHolder) {
            bindRequest((RequestHolder) holder, row.friend);
        } else {
            bindChat((ChatHolder) holder, row.conversation);
        }
    }

    private void bindRequest(RequestHolder holder, FriendStore.Friend friend) {
        holder.avatar.setText(initial(friend.name));
        holder.name.setText(friend.name);
        holder.accept.setOnClickListener(v -> requestListener.onAccept(friend));
        holder.decline.setOnClickListener(v -> requestListener.onDecline(friend));
    }

    private void bindChat(ChatHolder holder, Conversation item) {
        Context context = holder.itemView.getContext();
        holder.name.setText(item.peer);
        boolean unread = item.unread > 0;
        if (item.lastTimestamp == 0) {
            holder.preview.setText(R.string.friend_say_hi);
            holder.time.setText("");
        } else {
            holder.preview.setText(item.lastFromMe
                    ? context.getString(R.string.you_prefix, item.lastText)
                    : item.lastText);
            holder.time.setText(formatTime(context, item.lastTimestamp));
        }
        holder.preview.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.preview.setTextColor(ContextCompat.getColor(context,
                unread ? R.color.otg_text_primary : R.color.otg_text_secondary));
        if (unread) {
            holder.unread.setText(item.unread > 99 ? "99+" : String.valueOf(item.unread));
            holder.unread.setVisibility(View.VISIBLE);
        } else {
            holder.unread.setVisibility(View.GONE);
        }
        String flags = (item.pinned ? "📌" : "") + (item.muted ? " 🔕" : "");
        holder.flags.setText(flags.trim());
        holder.flags.setVisibility(flags.isEmpty() ? View.GONE : View.VISIBLE);
        bindStatus(holder, context, item);

        Bitmap bitmap = avatarFor(context, item.peer);
        if (bitmap != null) {
            holder.avatarImage.setImageBitmap(bitmap);
            holder.avatarImage.setVisibility(View.VISIBLE);
            holder.avatar.setVisibility(View.GONE);
        } else {
            holder.avatarImage.setVisibility(View.GONE);
            holder.avatar.setVisibility(View.VISIBLE);
            holder.avatar.setText(initial(item.peer));
        }
        holder.itemView.setOnClickListener(v -> listener.onConversationClick(item.peer));
        holder.itemView.setOnLongClickListener(v -> {
            longListener.onConversationLongClick(item.peer);
            return true;
        });
        AvatarCache.request(context, item.peer, () -> {
            int index = holder.getBindingAdapterPosition();
            if (index != RecyclerView.NO_POSITION) {
                notifyItemChanged(index);
            }
        });
    }

    private void bindStatus(ChatHolder holder, Context context, Conversation item) {
        if (item.online) {
            holder.status.setText(R.string.status_nearby);
            holder.status.setTextColor(
                    MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorPrimary));
        } else {
            holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.otg_text_secondary));
            if (item.lastSeen > 0) {
                CharSequence ago = DateUtils.getRelativeTimeSpanString(
                        item.lastSeen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                holder.status.setText(context.getString(R.string.status_last_seen, ago));
            } else {
                holder.status.setText(R.string.status_not_nearby);
            }
        }
    }

    private Bitmap avatarFor(Context context, String peer) {
        Bitmap bitmap = AvatarCache.cached(peer);
        if (bitmap != null) return bitmap;
        FriendStore.Friend friend = FriendStore.findByName(context, peer);
        if (friend != null && friend.avatar != null) {
            return ImageStore.thumbnail(context, friend.avatar);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return rows.size();
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

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvSectionTitle);
        }
    }

    static class RequestHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView name;
        final View accept;
        final View decline;

        RequestHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvRequestAvatar);
            name = itemView.findViewById(R.id.tvRequestName);
            accept = itemView.findViewById(R.id.btnRequestAccept);
            decline = itemView.findViewById(R.id.btnRequestDecline);
        }
    }

    static class ChatHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final ImageView avatarImage;
        final TextView name;
        final TextView flags;
        final TextView preview;
        final TextView status;
        final TextView time;
        final TextView unread;

        ChatHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvConversationAvatar);
            avatarImage = itemView.findViewById(R.id.ivConversationAvatar);
            name = itemView.findViewById(R.id.tvConversationName);
            flags = itemView.findViewById(R.id.tvConversationFlags);
            preview = itemView.findViewById(R.id.tvConversationPreview);
            status = itemView.findViewById(R.id.tvConversationStatus);
            time = itemView.findViewById(R.id.tvConversationTime);
            unread = itemView.findViewById(R.id.tvConversationUnread);
        }
    }
}