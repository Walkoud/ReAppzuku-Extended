package com.gree1d.reappzuku.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

public final class FocusHighlightUtil {

    private static final int COLOR_DARK_THEME  = 0xFFC9A6F7;
    private static final int COLOR_LIGHT_THEME = 0xFF7111F0;

    private FocusHighlightUtil() {}

    public static void apply(View view) {
        apply(view, 8, 2);
    }

    public static void apply(View view, int cornerRadiusDp, int strokeWidthDp) {
        Context context = view.getContext();
        int focusColor = isNightMode(context) ? COLOR_DARK_THEME : COLOR_LIGHT_THEME;

        float density = context.getResources().getDisplayMetrics().density;
        int cornerRadiusPx = (int) (cornerRadiusDp * density);
        int strokeWidthPx = (int) (strokeWidthDp * density);

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(cornerRadiusPx);
        focused.setStroke(strokeWidthPx, focusColor);
        focused.setColor((focusColor & 0x00FFFFFF) | 0x22000000);

        GradientDrawable defaultState = new GradientDrawable();
        defaultState.setShape(GradientDrawable.RECTANGLE);
        defaultState.setCornerRadius(cornerRadiusPx);
        defaultState.setColor(Color.TRANSPARENT);

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[] { android.R.attr.state_focused }, focused);
        stateList.addState(new int[] {}, defaultState);

        view.setBackground(stateList);
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
    }

    private static boolean isNightMode(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
