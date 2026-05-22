package com.gree1d.reappzuku;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import static com.gree1d.reappzuku.AppConstants.*;
import static com.gree1d.reappzuku.PreferenceKeys.*;

public abstract class BaseActivity extends AppCompatActivity {
    protected static final String PREFERENCES_NAME = "AppPreferences";
    protected static final String KEY_THEME_COMPAT = "appTheme";
    protected SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);
        int theme = sharedPreferences.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean isSystemTheme = (theme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (isAmoled) {
            switch (accent) {
                case ACCENT_INDIGO:    setTheme(R.style.AppTheme_AccentIndigo_Amoled);    break;
                case ACCENT_CRIMSON:   setTheme(R.style.AppTheme_AccentCrimson_Amoled);   break;
                case ACCENT_FOREST:    setTheme(R.style.AppTheme_AccentForest_Amoled);    break;
                case ACCENT_SLATE:     setTheme(R.style.AppTheme_AccentSlate_Amoled);     break;
                case ACCENT_ROSE:      setTheme(R.style.AppTheme_AccentRose_Amoled);      break;
                case ACCENT_AMBER:     setTheme(R.style.AppTheme_AccentAmber_Amoled);     break;
                case ACCENT_TEAL:      setTheme(R.style.AppTheme_AccentTeal_Amoled);      break;
                case ACCENT_TERRACOTA: setTheme(R.style.AppTheme_AccentTerracota_Amoled); break;
                case ACCENT_MOCHA:     setTheme(R.style.AppTheme_AccentMocha_Amoled);     break;
                case ACCENT_OLIVE:     setTheme(R.style.AppTheme_AccentOlive_Amoled);     break;
                case ACCENT_STEEL:     setTheme(R.style.AppTheme_AccentSteel_Amoled);     break;
                case ACCENT_APRICOT:   setTheme(R.style.AppTheme_AccentApricot_Amoled);   break;
                case ACCENT_SKY:       setTheme(R.style.AppTheme_AccentSky_Amoled);       break;
                case ACCENT_PAPAYA:    setTheme(R.style.AppTheme_AccentPapaya_Amoled);    break;
                case ACCENT_LAVENDER:  setTheme(R.style.AppTheme_AccentLavender_Amoled);  break;
                case ACCENT_MINT:      setTheme(R.style.AppTheme_AccentMint_Amoled);      break;
                case ACCENT_PEACH:     setTheme(R.style.AppTheme_AccentPeach_Amoled);     break;
                case ACCENT_POWDER:    setTheme(R.style.AppTheme_AccentPowder_Amoled);    break;
                case ACCENT_FOG:       setTheme(R.style.AppTheme_AccentFog_Amoled);       break;

                default:               setTheme(R.style.AppTheme_AccentCustom_Amoled); break;
            }
        } else if (isSystemTheme || accent == ACCENT_SYSTEM) {
            DynamicColors.applyToActivityIfAvailable(this);
        } else {
            switch (accent) {
                case ACCENT_INDIGO:    setTheme(R.style.AppTheme_AccentIndigo);    break;
                case ACCENT_CRIMSON:   setTheme(R.style.AppTheme_AccentCrimson);   break;
                case ACCENT_FOREST:    setTheme(R.style.AppTheme_AccentForest);    break;
                case ACCENT_SLATE:     setTheme(R.style.AppTheme_AccentSlate);     break;
                case ACCENT_ROSE:      setTheme(R.style.AppTheme_AccentRose);      break;
                case ACCENT_AMBER:     setTheme(R.style.AppTheme_AccentAmber);     break;
                case ACCENT_TEAL:      setTheme(R.style.AppTheme_AccentTeal);      break;
                case ACCENT_TERRACOTA: setTheme(R.style.AppTheme_AccentTerracota); break;
                case ACCENT_MOCHA:     setTheme(R.style.AppTheme_AccentMocha);     break;
                case ACCENT_OLIVE:     setTheme(R.style.AppTheme_AccentOlive);     break;
                case ACCENT_STEEL:     setTheme(R.style.AppTheme_AccentSteel);     break;
                case ACCENT_APRICOT:   setTheme(R.style.AppTheme_AccentApricot);   break;
                case ACCENT_SKY:       setTheme(R.style.AppTheme_AccentSky);       break;
                case ACCENT_PAPAYA:    setTheme(R.style.AppTheme_AccentPapaya);    break;
                case ACCENT_LAVENDER:  setTheme(R.style.AppTheme_AccentLavender);  break;
                case ACCENT_MINT:      setTheme(R.style.AppTheme_AccentMint);      break;
                case ACCENT_PEACH:     setTheme(R.style.AppTheme_AccentPeach);     break;
                case ACCENT_POWDER:    setTheme(R.style.AppTheme_AccentPowder);    break;
                case ACCENT_FOG:       setTheme(R.style.AppTheme_AccentFog);       break;
                default:               setTheme(R.style.AppTheme_AccentCustom);    break;
            }
        }

        if (isAmoled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(theme);
        }

        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (accent == ACCENT_CUSTOM) {
            applyCustomAccentOverlay();
        }

        applyStatusBarAppearance(isAmoled, theme);
    }

    private void applyCustomAccentOverlay() {
        int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);

        int darkColor = darkenColor(color, 0.75f);

        getTheme().applyStyle(R.style.AppTheme_AccentCustomOverride, true);

    }

    public int getAccentColor() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            return sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        }
        return MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLUE);
    }

    public int getOnAccentColor() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int pref = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
            return pref == ACCENT_ON_BLACK ? Color.BLACK : Color.WHITE;
        }

        boolean isLightAccent = (accent == ACCENT_APRICOT || accent == ACCENT_SKY ||
                accent == ACCENT_PAPAYA || accent == ACCENT_LAVENDER ||
                accent == ACCENT_MINT || accent == ACCENT_PEACH ||
                accent == ACCENT_POWDER || accent == ACCENT_FOG);
        return isLightAccent ? Color.BLACK : Color.WHITE;
    }

    private static int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private void applyStatusBarAppearance(boolean isAmoled, int theme) {
        if (getWindow() == null) return;

        boolean isCurrentlyLight;
        if (isAmoled) {
            isCurrentlyLight = false;
        } else if (theme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isCurrentlyLight = (nightMode != Configuration.UI_MODE_NIGHT_YES);
        } else {
            isCurrentlyLight = (theme == AppCompatDelegate.MODE_NIGHT_NO);
        }

        View decorView = getWindow().getDecorView();
        int flags = decorView.getSystemUiVisibility();
        if (isCurrentlyLight) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decorView.setSystemUiVisibility(flags);

        androidx.core.view.WindowInsetsControllerCompat insetsController =
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), decorView);
        if (insetsController != null) {
            insetsController.setAppearanceLightNavigationBars(isCurrentlyLight);
        }
    }

    protected void applyTheme() {
    }

    protected void applyNavBarInsets(View bottomNav) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            int navBarHeight = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, 0, 0, navBarHeight);
            return insets;
        });
    }
}
