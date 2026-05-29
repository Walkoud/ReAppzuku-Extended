package com.gree1d.reappzuku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet;
import com.github.mikephil.charting.renderer.PieChartRenderer;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

public class PieChartRender extends PieChartRenderer {

    // Darkness at the start edge (shadow of the cut)
    private static final float EDGE_DARK  = 0.40f;
    // Fraction of sweep angle that the shadow covers before full colour
    private static final float SHADOW_PCT = 0.18f;

    private final Path  mPath      = new Path();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        mFillPaint.setStyle(Paint.Style.FILL);

        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setColor(Color.BLACK);
        mEdgePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override
    protected void drawDataSet(Canvas c, IPieDataSet dataSet) {
        final float rotationAngle = mChart.getRotationAngle();
        final float phaseX        = mAnimator.getPhaseX();
        final float phaseY        = mAnimator.getPhaseY();

        final MPPointF center  = mChart.getCenterCircleBox();
        final float    radius  = mChart.getRadius();
        final float    holeRad = radius * (mChart.getHoleRadius() / 100f);
        final float    cx      = center.x;
        final float    cy      = center.y;

        final float sliceW     = radius - holeRad;
        final float lineStroke = Math.max(2f, sliceW * 0.04f);
        mEdgePaint.setStrokeWidth(lineStroke);

        final float[]       drawAngles = mChart.getDrawAngles();
        final float         sliceSpace = dataSet.getSliceSpace();
        final List<Integer> colors     = dataSet.getColors();

        final RectF outerRect = new RectF(cx - radius,  cy - radius,  cx + radius,  cy + radius);
        final RectF innerRect = new RectF(cx - holeRad, cy - holeRad, cx + holeRad, cy + holeRad);

        float angle = 0f;

        for (int j = 0; j < dataSet.getEntryCount(); j++) {
            final float sweepAngle = drawAngles[j] * phaseX;
            final PieEntry entry   = (PieEntry) dataSet.getEntryForIndex(j);

            if (entry == null || entry.getValue() == 0f) {
                angle += sweepAngle;
                continue;
            }

            final float effectiveSweep = sweepAngle * phaseY;
            final float startAngle     = rotationAngle + angle + sliceSpace / 2f;
            final float arcSweep       = Math.max(0f, effectiveSweep - sliceSpace);

            if (arcSweep <= 0f) {
                angle += sweepAngle;
                continue;
            }

            final int   color    = colors.get(j % colors.size());
            final float endAngle = startAngle + arcSweep;
            final float startRad = (float) Math.toRadians(startAngle);
            final float endRad   = (float) Math.toRadians(endAngle);

            // ── Clean donut segment path ──────────────────────────────────
            mPath.reset();
            mPath.moveTo(cx + holeRad * (float) Math.cos(startRad),
                         cy + holeRad * (float) Math.sin(startRad));
            mPath.lineTo(cx + radius * (float) Math.cos(startRad),
                         cy + radius * (float) Math.sin(startRad));
            mPath.arcTo(outerRect, startAngle, arcSweep, false);
            mPath.lineTo(cx + holeRad * (float) Math.cos(endRad),
                         cy + holeRad * (float) Math.sin(endRad));
            mPath.arcTo(innerRect, endAngle, -arcSweep, false);
            mPath.close();

            // ── SweepGradient: dark at start edge → full colour ───────────
            // shadow fades over SHADOW_PCT of the arc, then stays full colour
            int darkColor = darken(color, EDGE_DARK);

            float shadowDeg = arcSweep * SHADOW_PCT;

            SweepGradient sg = new SweepGradient(
                    cx, cy,
                    new int[]  { darkColor, color,            color },
                    new float[]{ 0f,        shadowDeg / 360f, arcSweep / 360f }
            );
            Matrix m = new Matrix();
            m.postRotate(startAngle, cx, cy);
            sg.setLocalMatrix(m);

            mFillPaint.setShader(sg);
            c.drawPath(mPath, mFillPaint);

            // ── Black divider line at start edge only ─────────────────────
            float inset = lineStroke * 0.5f;
            c.drawLine(
                    cx + (holeRad + inset) * (float) Math.cos(startRad),
                    cy + (holeRad + inset) * (float) Math.sin(startRad),
                    cx + (radius  - inset) * (float) Math.cos(startRad),
                    cy + (radius  - inset) * (float) Math.sin(startRad),
                    mEdgePaint);

            angle += sweepAngle;
        }

        MPPointF.recycleInstance(center);
    }

    private static int darken(int color, float fraction) {
        final int r = (int)(Color.red(color)   * (1f - fraction));
        final int g = (int)(Color.green(color) * (1f - fraction));
        final int b = (int)(Color.blue(color)  * (1f - fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
