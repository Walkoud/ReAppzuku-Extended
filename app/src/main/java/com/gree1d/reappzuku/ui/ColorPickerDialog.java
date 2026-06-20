package com.gree1d.reappzuku.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.gree1d.reappzuku.R;

public class ColorPickerDialog {

    public interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    public static void show(Context context, int initialColor, OnColorPickedListener listener) {
        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);

        final float[] currentHsv = { hsv[0], hsv[1], hsv[2] };

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_color_picker, null);

        HueSaturationView hsvView = view.findViewById(R.id.color_picker_hsv);
        HueBarView hueBar         = view.findViewById(R.id.color_picker_hue);
        View previewView          = view.findViewById(R.id.color_picker_preview);

        hsvView.setHsv(currentHsv[0], currentHsv[1], currentHsv[2]);
        hueBar.setHue(currentHsv[0]);
        previewView.setBackgroundColor(Color.HSVToColor(currentHsv));

        hueBar.setOnHueChangedListener(hue -> {
            currentHsv[0] = hue;
            hsvView.setHue(hue);
            previewView.setBackgroundColor(Color.HSVToColor(currentHsv));
        });

        hsvView.setOnSatValChangedListener((sat, val) -> {
            currentHsv[1] = sat;
            currentHsv[2] = val;
            previewView.setBackgroundColor(Color.HSVToColor(currentHsv));
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.settings_accent_custom_picker_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    if (listener != null) listener.onColorPicked(Color.HSVToColor(currentHsv));
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        dialog.show();
    }

    public static class HueSaturationView extends View {

        public interface OnSatValChangedListener {
            void onChanged(float sat, float val);
        }

        private float hue = 0f;
        private float sat = 1f;
        private float val = 1f;
        private OnSatValChangedListener listener;

        private final Paint paintSat = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintVal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintMarker = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public HueSaturationView(Context ctx) { super(ctx); paintMarker.setStyle(Paint.Style.STROKE); }
        public HueSaturationView(Context ctx, AttributeSet attrs) { super(ctx, attrs); paintMarker.setStyle(Paint.Style.STROKE); }

        public void setHsv(float h, float s, float v) {
            hue = h; sat = s; val = v;
            invalidate();
        }

        public void setHue(float h) {
            hue = h;
            invalidate();
        }

        public void setOnSatValChangedListener(OnSatValChangedListener l) { listener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            rect.set(0, 0, w, h);

            paintSat.setShader(new LinearGradient(0, 0, w, 0,
                    Color.WHITE, Color.HSVToColor(new float[]{hue, 1f, 1f}),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paintSat);

            paintVal.setShader(new LinearGradient(0, 0, 0, h,
                    Color.TRANSPARENT, Color.BLACK,
                    Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paintVal);

            float cx = sat * w;
            float cy = (1f - val) * h;
            paintMarker.setColor(Color.WHITE);
            paintMarker.setStrokeWidth(3f);
            canvas.drawCircle(cx, cy, 12f, paintMarker);
            paintMarker.setColor(Color.BLACK);
            paintMarker.setStrokeWidth(1.5f);
            canvas.drawCircle(cx, cy, 12f, paintMarker);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                sat = Math.max(0f, Math.min(1f, event.getX() / getWidth()));
                val = Math.max(0f, Math.min(1f, 1f - event.getY() / getHeight()));
                invalidate();
                if (listener != null) listener.onChanged(sat, val);
            }
            return true;
        }
    }

    public static class HueBarView extends View {

        public interface OnHueChangedListener {
            void onHueChanged(float hue);
        }

        private float hue = 0f;
        private OnHueChangedListener listener;
        private final Paint paintRainbow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintMarkerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintMarkerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HueBarView(Context ctx) { super(ctx); initPaints(); }
        public HueBarView(Context ctx, AttributeSet attrs) { super(ctx, attrs); initPaints(); }

        private void initPaints() {
            paintMarkerFill.setStyle(Paint.Style.FILL);
            paintMarkerFill.setColor(Color.WHITE);
            paintMarkerStroke.setStyle(Paint.Style.STROKE);
            paintMarkerStroke.setColor(Color.DKGRAY);
            paintMarkerStroke.setStrokeWidth(2f);
        }

        public void setHue(float h) { hue = h; invalidate(); }
        public void setOnHueChangedListener(OnHueChangedListener l) { listener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;

            int[] colors = new int[7];
            for (int i = 0; i < 7; i++) {
                colors[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
            }
            paintRainbow.setShader(new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP));
            float r = h / 2f;
            canvas.drawRoundRect(0, 0, w, h, r, r, paintRainbow);

            float cx = hue / 360f * w;
            canvas.drawCircle(cx, r, r - 2, paintMarkerFill);
            canvas.drawCircle(cx, r, r - 2, paintMarkerStroke);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                hue = Math.max(0f, Math.min(360f, event.getX() / getWidth() * 360f));
                invalidate();
                if (listener != null) listener.onHueChanged(hue);
            }
            return true;
        }
    }
}
