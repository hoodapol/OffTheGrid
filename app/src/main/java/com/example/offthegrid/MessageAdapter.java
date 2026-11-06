package com.example.offthegrid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    public interface OnMessageActionListener {
        void onMessageLongClick(Message message);

        void onMessageHeart(Message message);
    }

    private static final int TYPE_SENT = 0;
    private static final int TYPE_RECEIVED = 1;
    private static final int TYPE_NOTE = 2;
    private static final int TYPE_DATE = 3;
    private static final int QUOTE_ALPHA = 170;
    private static final long SECONDS_LIMIT = 100000000000L;

    private static final class Row {
        final int type;
        final Message message;
        final long time;
        final boolean last;

        Row(int type, Message message, long time, boolean last) {
            this.type = type;
            this.message = message;
            this.time = time;
            this.last = last;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnMessageActionListener listener;

    public MessageAdapter(OnMessageActionListener listener) {
        this.listener = listener;
        rows.add(new Row(TYPE_NOTE, null, 0, false));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == TYPE_NOTE) {
            layout = R.layout.item_chat_note;
        } else if (viewType == TYPE_DATE) {
            layout = R.layout.item_chat_date;
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
        Row row = rows.get(position);
        Context context = holder.itemView.getContext();
        if (row.type == TYPE_NOTE) return;
        if (row.type == TYPE_DATE) {
            holder.date.setText(dateLabel(context, row.time));
            return;
        }
        Message message = row.message;
        Bitmap bitmap = message.hasImage()
                ? ImageStore.thumbnail(context, message.getImage())
                : null;
        if (bitmap != null) {
            holder.text.setVisibility(View.GONE);
            holder.text.setOnTouchListener(null);
            holder.image.setVisibility(View.VISIBLE);
            holder.image.setImageBitmap(bitmap);
            attachGestures(holder.image, message);
            showImageHearts(holder, message);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.image.setOnTouchListener(null);
            if (holder.hearts != null) holder.hearts.setVisibility(View.GONE);
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(message.hasImage()
                    ? context.getString(R.string.photo_missing)
                    : bubbleText(holder.text, message));
            attachGestures(holder.text, message);
        }
        if (holder.status != null) {
            if (message.isPending()) {
                holder.status.setText(R.string.status_pending);
                holder.status.setVisibility(View.VISIBLE);
            } else if (row.last) {
                holder.status.setText(R.string.status_delivered);
                holder.status.setVisibility(View.VISIBLE);
            } else {
                holder.status.setVisibility(View.GONE);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachGestures(View view, Message message) {
        GestureDetector detector = new GestureDetector(view.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        listener.onMessageHeart(message);
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        listener.onMessageLongClick(message);
                    }
                });
        view.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void showImageHearts(ViewHolder holder, Message message) {
        if (holder.hearts == null) return;
        int count = (message.isHeartedByMe() ? 1 : 0) + (message.isHeartedByThem() ? 1 : 0);
        if (count == 0) {
            holder.hearts.setVisibility(View.GONE);
            return;
        }
        holder.hearts.setText(heartsText(holder.hearts, count));
        holder.hearts.setVisibility(View.VISIBLE);
    }

    private SpannableStringBuilder heartsText(TextView view, int count) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int size = Math.round(view.getTextSize() * 1.2f);
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(" ");
            int start = builder.length();
            builder.append(" ");
            Drawable heart = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_heart_ios);
            if (heart != null) {
                if (heart.getConstantState() != null) {
                    heart = heart.getConstantState().newDrawable().mutate();
                }
                heart.setBounds(0, 0, size, size);
                builder.setSpan(new ImageSpan(heart, ImageSpan.ALIGN_BASELINE), start,
                        start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private CharSequence bubbleText(TextView view, Message message) {
        boolean hearts = message.isHeartedByMe() || message.isHeartedByThem();
        if (!message.hasReply() && !hearts) return message.getText();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (message.hasReply()) {
            String quote = "↩ " + message.getReplyQuote();
            builder.append(quote);
            int end = quote.length();
            builder.setSpan(new StyleSpan(Typeface.ITALIC), 0, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.85f), 0, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(
                            ColorUtils.setAlphaComponent(view.getCurrentTextColor(), QUOTE_ALPHA)),
                    0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append("\n");
        }
        builder.append(message.getText());
        if (hearts) {
            builder.append("\n");
            int count = (message.isHeartedByMe() ? 1 : 0) + (message.isHeartedByThem() ? 1 : 0);
            builder.append(heartsText(view, count));
        }
        return builder;
    }

    private static long millis(long time) {
        return time < SECONDS_LIMIT ? time * 1000 : time;
    }

    private static long dayOf(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis(time));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static String dateLabel(Context context, long time) {
        long millis = millis(time);
        if (DateUtils.isToday(millis)) {
            return context.getString(R.string.chat_date_today);
        }
        if (DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)) {
            return context.getString(R.string.chat_date_yesterday);
        }
        return DateFormat.getMediumDateFormat(context).format(new Date(millis));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    public void setMessages(List<Message> newMessages) {
        rows.clear();
        rows.add(new Row(TYPE_NOTE, null, 0, false));
        long previousDay = -1;
        for (int i = 0; i < newMessages.size(); i++) {
            Message message = newMessages.get(i);
            long time = message.getTimestamp();
            if (time > 0) {
                long day = dayOf(time);
                if (day != previousDay) {
                    rows.add(new Row(TYPE_DATE, null, time, false));
                    previousDay = day;
                }
            }
            boolean last = i == newMessages.size() - 1;
            rows.add(new Row(message.isSent() ? TYPE_SENT : TYPE_RECEIVED, message, time, last));
        }
        notifyDataSetChanged();
    }

    public boolean hasPending() {
        for (Row row : rows) {
            if (row.message != null && row.message.isPending()) return true;
        }
        return false;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final ImageView image;
        final TextView status;
        final TextView hearts;
        final TextView date;

        ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvText);
            image = itemView.findViewById(R.id.ivImage);
            status = itemView.findViewById(R.id.tvStatus);
            hearts = itemView.findViewById(R.id.tvImageHearts);
            date = itemView.findViewById(R.id.tvDate);
        }
    }
}