package com.example.offthegrid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

public class StatusView extends View {
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean connected = false;

    public StatusView(Context context) {
        super(context);
        init(context);
    }

    public StatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true);
        textPaint.setColor(ContextCompat.getColor(context, value.resourceId));
        textPaint.setTextSize(dp(14) * getResources().getConfiguration().fontScale);
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize((int) dp(140), widthMeasureSpec);
        int height = resolveSize((int) dp(32), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() / 2f;
        dotPaint.setColor(connected ? 0xFF2E7D32 : 0xFF9E9E9E);
        canvas.drawCircle(dp(10), centerY, dp(6), dotPaint);

        String label = connected ? "Connected" : "Offline";
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, dp(24), baseline, textPaint);
    }
}