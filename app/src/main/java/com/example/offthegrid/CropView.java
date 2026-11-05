package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

public class CropView extends View {

    private static final float MAX_ZOOM = 5f;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF frame = new RectF();
    private final RectF bounds = new RectF();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector scrollDetector;

    private Bitmap bitmap;
    private float ratio = 1f;
    private boolean circle;
    private float minScale = 1f;
    private boolean ready;

    public CropView(Context context) {
        this(context, null);
    }

    public CropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dimPaint.setColor(0xAA000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (!ready) return true;
                        float current = currentScale();
                        float target = Math.max(minScale, Math.min(minScale * MAX_ZOOM,
                                current * detector.getScaleFactor()));
                        float change = target / current;
                        matrix.postScale(change, change, detector.getFocusX(),
                                detector.getFocusY());
                        keepInside();
                        invalidate();
                        return true;
                    }
                });
        scrollDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float distanceX, float distanceY) {
                        if (!ready) return true;
                        matrix.postTranslate(-distanceX, -distanceY);
                        keepInside();
                        invalidate();
                        return true;
                    }
                });
    }

    public void setSource(Bitmap source, float cropRatio, boolean roundGuide) {
        bitmap = source;
        ratio = cropRatio;
        circle = roundGuide;
        ready = false;
        layoutFrame();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutFrame();
    }

    private void layoutFrame() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        float margin = 24 * getResources().getDisplayMetrics().density;
        float width = getWidth() - 2 * margin;
        float height = width / ratio;
        float maxHeight = getHeight() * 0.8f;
        if (height > maxHeight) {
            height = maxHeight;
            width = height * ratio;
        }
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        frame.set(left, top, left + width, top + height);
        minScale = Math.max(width / bitmap.getWidth(), height / bitmap.getHeight());
        matrix.reset();
        matrix.postScale(minScale, minScale);
        matrix.postTranslate(
                frame.centerX() - bitmap.getWidth() * minScale / 2f,
                frame.centerY() - bitmap.getHeight() * minScale / 2f);
        ready = true;
    }

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void keepInside() {
        bounds.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(bounds);
        float dx = 0;
        float dy = 0;
        if (bounds.left > frame.left) {
            dx = frame.left - bounds.left;
        } else if (bounds.right < frame.right) {
            dx = frame.right - bounds.right;
        }
        if (bounds.top > frame.top) {
            dy = frame.top - bounds.top;
        } else if (bounds.bottom < frame.bottom) {
            dy = frame.bottom - bounds.bottom;
        }
        matrix.postTranslate(dx, dy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || !ready) return;
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        canvas.drawRect(0, 0, getWidth(), frame.top, dimPaint);
        canvas.drawRect(0, frame.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, dimPaint);
        canvas.drawRect(frame.right, frame.top, getWidth(), frame.bottom, dimPaint);
        canvas.drawRect(frame, borderPaint);
        if (circle) {
            canvas.drawOval(frame, borderPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        scrollDetector.onTouchEvent(event);
        return true;
    }

    public Bitmap crop(int outWidth, int outHeight) {
        if (bitmap == null || !ready) return null;
        Matrix inverse = new Matrix();
        if (!matrix.invert(inverse)) return null;
        RectF source = new RectF(frame);
        inverse.mapRect(source);
        Rect src = new Rect(
                Math.max(0, Math.round(source.left)),
                Math.max(0, Math.round(source.top)),
                Math.min(bitmap.getWidth(), Math.round(source.right)),
                Math.min(bitmap.getHeight(), Math.round(source.bottom)));
        if (src.width() <= 0 || src.height() <= 0) return null;
        Bitmap out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(bitmap, src, new Rect(0, 0, outWidth, outHeight), bitmapPaint);
        return out;
    }
}