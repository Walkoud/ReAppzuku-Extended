package com.gree1d.reappzuku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet;
import com.github.mikephil.charting.renderer.PieChartRenderer;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

public class PieChartRender extends PieChartRenderer {

    private static final float CAP_RADIUS_FRACTION = 0.028f;
    private static final float GRADIENT_CENTER_LIGHTEN = 0.55f;

    private final Path mArcPath = new Path();
    private final Paint mCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        mCapPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void drawDataSet(Canvas c, IPieDataSet dataSet) {
        float angle = 0;
        float rotationAngle = mChart.getRotationAngle();
        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        final RectF circleBox = mChart.getCircleBox();
        final MPPointF center = mChart.getCenterCircleBox();
        final float radius = mChart.getRadius();
        final float holeRadius = radius * (mChart.getHoleRadius() / 100f);
        final float sliceWidth = radius - holeRadius;
        final float capRadius = sliceWidth * CAP_RADIUS_FRACTION;
        final float midRadius = holeRadius + sliceWidth / 2f;

        final int entryCount = dataSet.getEntryCount();
        final float[] drawAngles = mChart.getDrawAngles();
        final float sliceSpace = dataSet.getSliceSpace();

        List<Integer> colors = dataSet.getColors();

        mRenderPaint.setStyle(Paint.Style.FILL);
        mRenderPaint.setShader(null);

        for (int j = 0; j < entryCount; j++) {
            float sweepAngle = drawAngles[j] * phaseX;
            PieEntry entry = (PieEntry) dataSet.getEntryForIndex(j);
            if (entry == null || entry.getValue() == 0f) {
                angle += sweepAngle;
                continue;
            }

            float effectiveSweep = sweepAngle * phaseY;
            float startAngle = rotationAngle + angle + sliceSpace / 2f;
            float arcSweep = Math.max(0f, effectiveSweep - sliceSpace);

            if (arcSweep <= 0f) {
                angle += sweepAngle;
                continue;
            }

            int sliceColor = colors.get(j % colors.size());

            float cx = center.x;
            float cy = center.y;

            RadialGradient gradient = new RadialGradient(
                    cx, cy,
                    radius,
                    lighten(sliceColor, GRADIENT_CENTER_LIGHTEN),
                    sliceColor,
                    Shader.TileMode.CLAMP
            );
            mRenderPaint.setShader(gradient);

            mArcPath.reset();
            mArcPath.moveTo(
                    cx + holeRadius * (float) Math.cos(Math.toRadians(startAngle)),
                    cy + holeRadius * (float) Math.sin(Math.toRadians(startAngle))
            );
            mArcPath.arcTo(circleBox, startAngle, arcSweep);
            RectF innerBox = new RectF(
                    cx - holeRadius, cy - holeRadius,
                    cx + holeRadius, cy + holeRadius
            );
            mArcPath.arcTo(innerBox, startAngle + arcSweep, -arcSweep);
            mArcPath.close();

            c.drawPath(mArcPath, mRenderPaint);

            mRenderPaint.setShader(null);
            mCapPaint.setColor(sliceColor);

            float startRad = (float) Math.toRadians(startAngle);
            float endRad   = (float) Math.toRadians(startAngle + arcSweep);

            drawCap(c, cx + midRadius * (float) Math.cos(startRad),
                       cy + midRadius * (float) Math.sin(startRad), capRadius);
            drawCap(c, cx + midRadius * (float) Math.cos(endRad),
                       cy + midRadius * (float) Math.sin(endRad),   capRadius);

            angle += sweepAngle;
        }

        MPPointF.recycleInstance(center);
    }

    private void drawCap(Canvas c, float x, float y, float r) {
        c.drawCircle(x, y, r, mCapPaint);
    }

    private static int lighten(int color, float fraction) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, r + (int) ((255 - r) * fraction));
        g = Math.min(255, g + (int) ((255 - g) * fraction));
        b = Math.min(255, b + (int) ((255 - b) * fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
