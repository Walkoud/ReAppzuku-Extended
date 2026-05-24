package com.gree1d.reappzuku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

public class QuarterCircleMenu extends View {

    public interface OnItemClickListener {
        void onItemClick(int index);
    }

    private static final int   SEGMENT_COUNT = 4;
    private static final float START_ANGLE   = 180f;
    private static final float SWEEP_ANGLE   = 90f;

    private final Paint   segmentPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint   iconPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint   dividerPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Drawable[] icons        = new Drawable[SEGMENT_COUNT];
    private int[]      iconTints    = new int[SEGMENT_COUNT];
    private int        pressedIndex  = -1;
    private int        segmentColor  = 0xFF1A237E;
    private int        pressedColor  = 0xFF283593;
    private int        dividerColor  = 0x33FFFFFF;
    private float      radius        = 0f;
    private float      innerRadius   = 0f;

    private OnItemClickListener listener;

    public QuarterCircleMenu(Context context) {
        super(context);
        init();
    }

    public QuarterCircleMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        segmentPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(1f * getResources().getDisplayMetrics().density);
    }

    public void setSegmentColor(int color) {
        segmentColor = color;
        pressedColor = blendWithWhite(color, 0.15f);
        invalidate();
    }

    public void setDividerColor(int color) {
        dividerColor = color;
        invalidate();
    }

    public void setIcon(int index, Drawable drawable) {
        if (index >= 0 && index < SEGMENT_COUNT) {
            icons[index] = drawable;
            invalidate();
        }
    }

    public void setIconTint(int index, int color) {
        if (index >= 0 && index < SEGMENT_COUNT) {
            iconTints[index] = color;
            invalidate();
        }
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        radius      = Math.min(w, h);
        innerRadius = radius * 0.28f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float segmentSweep = SWEEP_ANGLE / SEGMENT_COUNT;

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float startAngle = START_ANGLE + i * segmentSweep;

            segmentPaint.setColor(pressedIndex == i ? pressedColor : segmentColor);

            Path path = buildSegmentPath(startAngle, segmentSweep);
            canvas.drawPath(path, segmentPaint);

            dividerPaint.setColor(dividerColor);
            canvas.drawPath(path, dividerPaint);

            drawIcon(canvas, i, startAngle + segmentSweep / 2f);
        }
    }

    private Path buildSegmentPath(float startAngle, float sweepAngle) {
        Path path = new Path();
        float cx = 0f, cy = 0f;

        double startRad = Math.toRadians(startAngle);
        float ix = cx + (float)(innerRadius * Math.cos(startRad));
        float iy = cy + (float)(innerRadius * Math.sin(startRad));
        path.moveTo(ix, iy);

        android.graphics.RectF outerRect = new android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        path.arcTo(outerRect, startAngle, sweepAngle, false);

        double endRad = Math.toRadians(startAngle + sweepAngle);
        float innerEndX = cx + (float)(innerRadius * Math.cos(endRad));
        float innerEndY = cy + (float)(innerRadius * Math.sin(endRad));
        path.lineTo(innerEndX, innerEndY);

        android.graphics.RectF innerRect = new android.graphics.RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);
        path.arcTo(innerRect, startAngle + sweepAngle, -sweepAngle, false);

        path.close();
        return path;
    }

    private void drawIcon(Canvas canvas, int index, float midAngle) {
        if (icons[index] == null) return;

        float iconRadius = (radius + innerRadius) / 2f;
        double rad = Math.toRadians(midAngle);
        float cx = (float)(iconRadius * Math.cos(rad));
        float cy = (float)(iconRadius * Math.sin(rad));

        float dp = getResources().getDisplayMetrics().density;
        int size = (int)(20 * dp);
        int left  = (int)(cx - size / 2f);
        int top   = (int)(cy - size / 2f);
        int right  = left + size;
        int bottom = top  + size;

        icons[index].setBounds(left, top, right, bottom);
        if (iconTints[index] != 0) {
            icons[index].setTint(iconTints[index]);
        }
        icons[index].draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int   hitIndex = getSegmentAt(x, y);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressedIndex = hitIndex;
                invalidate();
                return hitIndex >= 0;

            case MotionEvent.ACTION_UP:
                int released = pressedIndex;
                pressedIndex = -1;
                invalidate();
                if (released >= 0 && released == hitIndex && listener != null) {
                    listener.onItemClick(released);
                }
                return released >= 0;

            case MotionEvent.ACTION_CANCEL:
                pressedIndex = -1;
                invalidate();
                return false;
        }
        return false;
    }

    private int getSegmentAt(float x, float y) {
        float dist = (float) Math.sqrt(x * x + y * y);
        if (dist < innerRadius || dist > radius) return -1;

        double angle = Math.toDegrees(Math.atan2(y, x));
        if (angle < 0) angle += 360;

        double relative = angle - START_ANGLE;
        if (relative < 0) relative += 360;
        if (relative > SWEEP_ANGLE) return -1;

        float segmentSweep = SWEEP_ANGLE / SEGMENT_COUNT;
        return (int)(relative / segmentSweep);
    }

    private int blendWithWhite(int color, float ratio) {
        int r = (int)(((color >> 16) & 0xFF) * (1 - ratio) + 255 * ratio);
        int g = (int)(((color >> 8)  & 0xFF) * (1 - ratio) + 255 * ratio);
        int b = (int)(((color)       & 0xFF) * (1 - ratio) + 255 * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
