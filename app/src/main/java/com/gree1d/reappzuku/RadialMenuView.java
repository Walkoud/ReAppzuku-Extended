package com.gree1d.reappzuku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

public class RadialMenuView extends View {

    public interface OnItemClickListener {
        void onItemClick(int index);
    }

    private static final int   SECTOR_COLOR     = 0xCC3A3A3A;
    private static final int   DIVIDER_COLOR    = Color.BLACK;
    private static final int   ICON_TINT        = Color.WHITE;
    private static final float START_ANGLE      = 90f;
    private static final float SWEEP            = 90f;
    private static final float ICON_HALF_DP     = 14f;
    private static final float CELL_PADDING_DP  = 5f;
    private static final float DIVIDER_WIDTH_DP = 2f;

    private final Paint sectorPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] iconResIds = new int[0];
    private OnItemClickListener listener;

    private float centerX;
    private float centerY;
    private float outerRadius;

    public RadialMenuView(Context context) {
        super(context);
        init();
    }

    public RadialMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadialMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        sectorPaint.setColor(SECTOR_COLOR);
        sectorPaint.setStyle(Paint.Style.FILL);

        float dp = getResources().getDisplayMetrics().density;
        dividerPaint.setColor(DIVIDER_COLOR);
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(DIVIDER_WIDTH_DP * dp);
    }

    public void setItems(int[] iconResIds) {
        this.iconResIds = iconResIds;
        invalidate();
    }

    public void setCenter(float x, float y, float radius) {
        centerX = x;
        centerY = y;
        outerRadius = radius;
        invalidate();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (iconResIds.length == 0 || outerRadius == 0f) return;

        float dp       = getResources().getDisplayMetrics().density;
        float iconHalf = ICON_HALF_DP * dp;
        float padding  = CELL_PADDING_DP * dp;
        float cellHalf = iconHalf + padding;

        int n = iconResIds.length;
        float sectorAngle = SWEEP / n;

        for (int i = 0; i < n; i++) {
            float startAngle  = START_ANGLE + i * sectorAngle;
            float midAngleDeg = startAngle + sectorAngle / 2f;
            float midAngleRad = (float) Math.toRadians(midAngleDeg);

            float midRadius = outerRadius - cellHalf;

            float iconCx = centerX + (float) Math.cos(midAngleRad) * midRadius;
            float iconCy = centerY + (float) Math.sin(midAngleRad) * midRadius;

            float inner = midRadius - cellHalf;
            float outer = midRadius + cellHalf;
            if (inner < 0) inner = 0;

            Path path = buildSectorPath(startAngle, sectorAngle, inner, outer);
            canvas.drawPath(path, sectorPaint);
            canvas.drawPath(path, dividerPaint);

            Drawable drawable = ContextCompat.getDrawable(getContext(), iconResIds[i]);
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTint(ICON_TINT);
                drawable.setBounds(
                        Math.round(iconCx - iconHalf),
                        Math.round(iconCy - iconHalf),
                        Math.round(iconCx + iconHalf),
                        Math.round(iconCy + iconHalf));
                drawable.draw(canvas);
            }
        }
    }

    private Path buildSectorPath(float startAngleDeg, float sweepDeg,
                                  float innerR, float outerR) {
        Path path = new Path();
        RectF outerRect = new RectF(
                centerX - outerR, centerY - outerR,
                centerX + outerR, centerY + outerR);
        RectF innerRect = new RectF(
                centerX - innerR, centerY - innerR,
                centerX + innerR, centerY + innerR);

        path.arcTo(outerRect, startAngleDeg, sweepDeg, false);

        float endRad = (float) Math.toRadians(startAngleDeg + sweepDeg);
        path.lineTo(
                centerX + (float) Math.cos(endRad) * innerR,
                centerY + (float) Math.sin(endRad) * innerR);

        path.arcTo(innerRect, startAngleDeg + sweepDeg, -sweepDeg, false);
        path.close();
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            float x  = event.getX();
            float y  = event.getY();
            float dx = x - centerX;
            float dy = y - centerY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            float dp      = getResources().getDisplayMetrics().density;
            float cellHalf = (ICON_HALF_DP + CELL_PADDING_DP) * dp;
            float minDist  = outerRadius - cellHalf * 2f;
            float maxDist  = outerRadius;

            if (dist < minDist || dist > maxDist) {
                setVisibility(View.GONE);
                return true;
            }

            double angleRad = Math.atan2(dy, dx);
            float angleDeg  = (float) Math.toDegrees(angleRad);
            if (angleDeg < 0) angleDeg += 360f;

            float relative = angleDeg - START_ANGLE;
            if (relative < 0) relative += 360f;

            if (relative >= 0f && relative <= SWEEP && iconResIds.length > 0) {
                int n = iconResIds.length;
                int index = Math.min((int) (relative / (SWEEP / n)), n - 1);
                setVisibility(View.GONE);
                if (listener != null) listener.onItemClick(index);
            } else {
                setVisibility(View.GONE);
            }
            return true;
        }

        return false;
    }
}
