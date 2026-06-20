package com.gree1d.reappzuku.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.manager.SleepModeManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.R;

public class FilterAppsAdapter extends BaseAdapter implements Filterable {

    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }

    private final List<AppModel> allApps;
    private List<AppModel> filteredApps;
    private final LayoutInflater inflater;
    private final Context context;
    private AppFilter filter;

    private boolean showSystem = false;
    private boolean showUser = true;
    private boolean showRunningOnly = false;
    private CharSequence lastConstraint = "";

    private final boolean restrictionMode;
    private final boolean sleepMode;

    private final Map<String, BackgroundAppManager.RestrictionType> restrictionTypeMap;
    private final Map<String, SleepModeManager.FreezeType> freezeTypeMap;
    private final Map<String, SleepModeManager.FreezeMethod> freezeMethodMap;

    private final Map<String, Integer> manualOpsMaskMap;
    private final Map<String, Integer> manualBucketMap;

    private OnSelectionChangedListener selectionChangedListener;
    private int accentColor = 0;
    private SleepModeManager sleepModeManager;

    public void setAccentColor(int color) {
        this.accentColor = color;
    }

    private boolean hasAccent() {
        return accentColor != 0;
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps, Set<String> selectedApps) {
        this(context, apps, selectedApps, null, null, null, null, null, false, false);
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps,
                             Set<String> timerApps, Set<String> permanentApps,
                             boolean isSleepMode) {
        this(context, apps, timerApps, permanentApps, null, isSleepMode);
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps,
                             Set<String> timerApps, Set<String> permanentApps,
                             SleepModeManager sleepModeManager, boolean isSleepMode) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.restrictionMode = false;
        this.sleepMode = true;
        this.sleepModeManager = sleepModeManager;
        this.restrictionTypeMap = new HashMap<>();
        this.freezeTypeMap = new HashMap<>();
        this.freezeMethodMap = new HashMap<>();
        this.manualOpsMaskMap = new HashMap<>();
        this.manualBucketMap = new HashMap<>();

        for (AppModel app : apps) {
            String pkg = app.getPackageName();
            if (permanentApps.contains(pkg)) {
                app.setSelected(true);
                freezeTypeMap.put(pkg, SleepModeManager.FreezeType.PERMANENT);
            } else if (timerApps.contains(pkg)) {
                app.setSelected(true);
                freezeTypeMap.put(pkg, SleepModeManager.FreezeType.TIMER);
            }
            if (sleepModeManager != null) {
                freezeMethodMap.put(pkg, sleepModeManager.getFreezeMethod(pkg));
            }
        }

        Collections.sort(apps, (app1, app2) -> {
            if (app1.isSelected() != app2.isSelected()) {
                return app1.isSelected() ? -1 : 1;
            }
            if (app1.isSystemApp() != app2.isSystemApp()) {
                return app1.isSystemApp() ? 1 : -1;
            }
            return app1.getAppName().compareToIgnoreCase(app2.getAppName());
        });

        this.allApps = apps;
        this.filteredApps = new ArrayList<>();
        filterInitialList();
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps,
                             Set<String> selectedApps, Set<String> hardRestrictedApps) {
        this(context, apps, selectedApps, hardRestrictedApps, null, null, null, null, true, false);
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps,
                             Set<String> selectedApps,
                             Set<String> hardRestrictedApps,
                             Set<String> manualRestrictedApps,
                             Map<String, Integer> initialMasks) {
        this(context, apps, selectedApps, hardRestrictedApps, null, manualRestrictedApps, initialMasks, null, true, false);
    }

    public FilterAppsAdapter(Context context, List<AppModel> apps,
                             Set<String> selectedApps,
                             Set<String> hardRestrictedApps,
                             Set<String> mediumRestrictedApps,
                             Set<String> manualRestrictedApps,
                             Map<String, Integer> initialMasks,
                             Map<String, Integer> initialBuckets) {
        this(context, apps, selectedApps, hardRestrictedApps, mediumRestrictedApps, manualRestrictedApps, initialMasks, initialBuckets, true, false);
    }

    private FilterAppsAdapter(Context context, List<AppModel> apps,
                               Set<String> selectedApps,
                               Set<String> hardRestrictedApps,
                               Set<String> mediumRestrictedApps,
                               Set<String> manualRestrictedApps,
                               Map<String, Integer> initialMasks,
                               Map<String, Integer> initialBuckets,
                               boolean restrictionMode,
                               boolean sleepMode) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.restrictionMode = restrictionMode;
        this.sleepMode = sleepMode;
        this.restrictionTypeMap = new HashMap<>();
        this.freezeTypeMap = new HashMap<>();
        this.freezeMethodMap = new HashMap<>();
        this.manualOpsMaskMap = new HashMap<>();
        this.manualBucketMap = new HashMap<>();

        for (AppModel app : apps) {
            if (selectedApps.contains(app.getPackageName())) {
                app.setSelected(true);
            }
        }

        if (restrictionMode) {
            if (hardRestrictedApps != null) {
                for (String pkg : hardRestrictedApps) {
                    restrictionTypeMap.put(pkg, BackgroundAppManager.RestrictionType.HARD);
                }
            }
            if (mediumRestrictedApps != null) {
                for (String pkg : mediumRestrictedApps) {
                    restrictionTypeMap.put(pkg, BackgroundAppManager.RestrictionType.MEDIUM);
                }
            }
            if (manualRestrictedApps != null) {
                for (String pkg : manualRestrictedApps) {
                    restrictionTypeMap.put(pkg, BackgroundAppManager.RestrictionType.MANUAL);
                }
            }
            if (initialMasks != null) {
                manualOpsMaskMap.putAll(initialMasks);
            }
            if (initialBuckets != null) {
                manualBucketMap.putAll(initialBuckets);
            }
        }

        Collections.sort(apps, (app1, app2) -> {
            if (app1.isSelected() != app2.isSelected()) {
                return app1.isSelected() ? -1 : 1;
            }
            if (app1.isSystemApp() != app2.isSystemApp()) {
                return app1.isSystemApp() ? 1 : -1;
            }
            return app1.getAppName().compareToIgnoreCase(app2.getAppName());
        });

        this.allApps = apps;
        this.filteredApps = new ArrayList<>();
        filterInitialList();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public Set<String> getSelectedPackages() {
        Set<String> selected = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected()) selected.add(app.getPackageName());
        }
        return selected;
    }

    public Set<String> getTimerPackages() {
        Set<String> result = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected() && freezeTypeMap.getOrDefault(
                    app.getPackageName(), SleepModeManager.FreezeType.TIMER)
                    == SleepModeManager.FreezeType.TIMER) {
                result.add(app.getPackageName());
            }
        }
        return result;
    }

    public Set<String> getPermanentPackages() {
        Set<String> result = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected() && freezeTypeMap.get(app.getPackageName())
                    == SleepModeManager.FreezeType.PERMANENT) {
                result.add(app.getPackageName());
            }
        }
        return result;
    }

    public Map<String, SleepModeManager.FreezeMethod> getFreezeMethodMap() {
        return new HashMap<>(freezeMethodMap);
    }

    public Set<String> getMediumRestrictedPackages() {
        Set<String> medium = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected()
                    && restrictionTypeMap.get(app.getPackageName())
                       == BackgroundAppManager.RestrictionType.MEDIUM) {
                medium.add(app.getPackageName());
            }
        }
        return medium;
    }

    public Map<String, Integer> getManualBuckets() {
        return new HashMap<>(manualBucketMap);
    }

    public Set<String> getHardRestrictedPackages() {
        Set<String> hard = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected()
                    && restrictionTypeMap.get(app.getPackageName())
                       == BackgroundAppManager.RestrictionType.HARD) {
                hard.add(app.getPackageName());
            }
        }
        return hard;
    }

    public Set<String> getManualRestrictedPackages() {
        Set<String> manual = new HashSet<>();
        for (AppModel app : allApps) {
            if (app.isSelected()
                    && restrictionTypeMap.get(app.getPackageName())
                       == BackgroundAppManager.RestrictionType.MANUAL) {
                manual.add(app.getPackageName());
            }
        }
        return manual;
    }

    public Map<String, Integer> getManualOpsMasks() {
        return new HashMap<>(manualOpsMaskMap);
    }

    public void clearSelection() {
        for (AppModel app : allApps) {
            app.setSelected(false);
        }
        notifyDataSetChanged();
    }

    private void filterInitialList() {
        this.filteredApps.clear();
        for (AppModel app : allApps) {
            if (shouldShow(app)) this.filteredApps.add(app);
        }
    }

    public void setFilters(boolean showSystem, boolean showUser, boolean showRunningOnly) {
        this.showSystem = showSystem;
        this.showUser = showUser;
        this.showRunningOnly = showRunningOnly;
        getFilter().filter(lastConstraint);
    }

    private boolean shouldShow(AppModel app) {
        if (app.isSystemApp() && !showSystem) return false;
        if (!app.isSystemApp() && !showUser) return false;
        if (showRunningOnly && app.getAppRamBytes() <= 0) return false;
        return true;
    }

    @Override public int getCount() { return filteredApps.size(); }
    @Override public AppModel getItem(int position) { return filteredApps.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_filter_app, parent, false);
            holder = new ViewHolder();
            holder.appName        = convertView.findViewById(R.id.filter_app_name);
            holder.appStatus      = convertView.findViewById(R.id.filter_app_status);
            holder.appIcon        = convertView.findViewById(R.id.filter_app_icon);
            holder.checkBox       = convertView.findViewById(R.id.filter_app_checkbox);
            holder.restrictionType = convertView.findViewById(R.id.filter_restriction_type);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppModel app = getItem(position);
        holder.appName.setText(app.getAppName());

        String statusText = app.getBackgroundRestrictionStatusText(context);
        if (statusText.isEmpty()) {
            holder.appStatus.setVisibility(View.GONE);
        } else {
            holder.appStatus.setVisibility(View.VISIBLE);
            holder.appStatus.setText(statusText);
        }

        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.checkBox.setChecked(app.isSelected());
        if (hasAccent()) {
            holder.checkBox.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(accentColor));
        }

        if (holder.restrictionType != null) {
            if (restrictionMode && app.isSelected()) {
                BackgroundAppManager.RestrictionType type =
                        restrictionTypeMap.getOrDefault(app.getPackageName(),
                                BackgroundAppManager.RestrictionType.SOFT);
                holder.restrictionType.setVisibility(View.VISIBLE);
                holder.restrictionType.setText(badgeLabel(type));
                applyBadgeAccent(holder.restrictionType);
                holder.restrictionType.setOnClickListener(
                        v -> showRestrictionTypeDialog(app, holder.restrictionType));
            } else if (sleepMode && app.isSelected()) {
                SleepModeManager.FreezeType ft = freezeTypeMap.getOrDefault(
                        app.getPackageName(), SleepModeManager.FreezeType.TIMER);
                holder.restrictionType.setVisibility(View.VISIBLE);
                holder.restrictionType.setText(badgeLabelFreeze(ft));
                applyBadgeAccent(holder.restrictionType);
                holder.restrictionType.setOnClickListener(
                        v -> showFreezeTypeDialog(app, holder.restrictionType));
            } else {
                holder.restrictionType.setVisibility(View.GONE);
                holder.restrictionType.setOnClickListener(null);
            }
        }

        final ViewHolder h = holder;

        holder.checkBox.setOnClickListener(v -> {
            app.setSelected(h.checkBox.isChecked());
            if (!app.isSelected()) {
                if (restrictionMode) {
                    restrictionTypeMap.remove(app.getPackageName());
                    manualOpsMaskMap.remove(app.getPackageName());
                    manualBucketMap.remove(app.getPackageName());
                }
                if (sleepMode) {
                    freezeTypeMap.remove(app.getPackageName());
                }
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        });

        convertView.setOnClickListener(v -> {
            boolean newState = !h.checkBox.isChecked();
            h.checkBox.setChecked(newState);
            app.setSelected(newState);
            if (!newState) {
                if (restrictionMode) {
                    restrictionTypeMap.remove(app.getPackageName());
                    manualOpsMaskMap.remove(app.getPackageName());
                    manualBucketMap.remove(app.getPackageName());
                }
                if (sleepMode) {
                    freezeTypeMap.remove(app.getPackageName());
                }
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        });

        return convertView;
    }

    private String badgeLabel(BackgroundAppManager.RestrictionType type) {
        switch (type) {
            case HARD:   return context.getString(R.string.restriction_badge_hard);
            case MEDIUM: return context.getString(R.string.restriction_badge_medium);
            case MANUAL: return context.getString(R.string.restriction_badge_manual);
            default:     return context.getString(R.string.restriction_badge_soft);
        }
    }

    private String badgeLabelFreeze(SleepModeManager.FreezeType type) {
        if (type == SleepModeManager.FreezeType.PERMANENT) {
            return context.getString(R.string.freeze_badge_permanent);
        }
        return context.getString(R.string.freeze_badge_timer);
    }

    private void applyBadgeAccent(TextView badge) {
        if (!hasAccent()) return;
        badge.setTextColor(accentColor);
        android.graphics.drawable.Drawable bg = badge.getBackground();
        if (bg != null) {
            android.graphics.drawable.Drawable mutated = bg.mutate();
            if (mutated instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) mutated).setStroke(
                        (int) (badge.getContext().getResources()
                                .getDisplayMetrics().density * 1.5f),
                        accentColor);
                ((android.graphics.drawable.GradientDrawable) mutated)
                        .setColor(android.graphics.Color.argb(30,
                                android.graphics.Color.red(accentColor),
                                android.graphics.Color.green(accentColor),
                                android.graphics.Color.blue(accentColor)));
            }
        }
    }

    private void showFreezeTypeDialog(AppModel app, TextView chipView) {
        SleepModeManager.FreezeType current = freezeTypeMap.getOrDefault(
                app.getPackageName(), SleepModeManager.FreezeType.TIMER);
        SleepModeManager.FreezeMethod currentMethod = freezeMethodMap.getOrDefault(
                app.getPackageName(), SleepModeManager.FreezeMethod.DISABLE);
        boolean isSystem = app.isSystemApp();

        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(0, 16, 0, 8);

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(context);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        int paddingH = (int) (context.getResources().getDisplayMetrics().density * 24);
        int methodIndent = (int) (context.getResources().getDisplayMetrics().density * 16);

        android.widget.RadioButton timerBtn = new android.widget.RadioButton(context);
        timerBtn.setId(View.generateViewId());
        timerBtn.setText(context.getString(R.string.filter_freeze_timer_option));
        timerBtn.setPadding(paddingH, 24, paddingH, 24);
        timerBtn.setChecked(current == SleepModeManager.FreezeType.TIMER);
        radioGroup.addView(timerBtn);

        android.widget.RadioGroup timerMethodGroup = new android.widget.RadioGroup(context);
        timerMethodGroup.setOrientation(android.widget.RadioGroup.VERTICAL);

        android.widget.RadioButton timerSuspendBtn = new android.widget.RadioButton(context);
        timerSuspendBtn.setId(View.generateViewId());
        timerSuspendBtn.setText("pm suspend");
        timerSuspendBtn.setPadding(paddingH, 12, paddingH, 12);
        timerMethodGroup.addView(timerSuspendBtn);

        android.widget.RadioButton timerDisableBtn = new android.widget.RadioButton(context);
        timerDisableBtn.setId(View.generateViewId());
        timerDisableBtn.setText("pm disable");
        timerDisableBtn.setPadding(paddingH, 12, paddingH, 12);
        timerMethodGroup.addView(timerDisableBtn);

        android.widget.RadioGroup.LayoutParams timerMethodGroupParams =
                new android.widget.RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timerMethodGroupParams.setMarginStart(paddingH + methodIndent);
        radioGroup.addView(timerMethodGroup, timerMethodGroupParams);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton permanentBtn = new android.widget.RadioButton(context);
        permanentBtn.setId(View.generateViewId());
        permanentBtn.setText(context.getString(R.string.filter_freeze_permanent_option));
        permanentBtn.setPadding(paddingH, 24, paddingH, 24);
        permanentBtn.setChecked(current == SleepModeManager.FreezeType.PERMANENT);
        radioGroup.addView(permanentBtn);

        android.widget.RadioGroup permanentMethodGroup = new android.widget.RadioGroup(context);
        permanentMethodGroup.setOrientation(android.widget.RadioGroup.VERTICAL);

        android.widget.RadioButton permanentSuspendBtn = new android.widget.RadioButton(context);
        permanentSuspendBtn.setId(View.generateViewId());
        permanentSuspendBtn.setText("pm suspend");
        permanentSuspendBtn.setPadding(paddingH, 12, paddingH, 12);
        permanentMethodGroup.addView(permanentSuspendBtn);

        android.widget.RadioButton permanentDisableBtn = new android.widget.RadioButton(context);
        permanentDisableBtn.setId(View.generateViewId());
        permanentDisableBtn.setText("pm disable");
        permanentDisableBtn.setPadding(paddingH, 12, paddingH, 12);
        permanentMethodGroup.addView(permanentDisableBtn);

        android.widget.RadioGroup.LayoutParams permanentMethodGroupParams =
                new android.widget.RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        permanentMethodGroupParams.setMarginStart(paddingH + methodIndent);
        radioGroup.addView(permanentMethodGroup, permanentMethodGroupParams);

        if (isSystem) {
            timerMethodGroup.setVisibility(View.GONE);
            permanentMethodGroup.setVisibility(View.GONE);
        } else {
            if (currentMethod == SleepModeManager.FreezeMethod.SUSPEND) {
                timerSuspendBtn.setChecked(true);
                permanentSuspendBtn.setChecked(true);
            } else {
                timerDisableBtn.setChecked(true);
                permanentDisableBtn.setChecked(true);
            }
            timerMethodGroup.setVisibility(current == SleepModeManager.FreezeType.TIMER ? View.VISIBLE : View.GONE);
            permanentMethodGroup.setVisibility(current == SleepModeManager.FreezeType.PERMANENT ? View.VISIBLE : View.GONE);
        }

        timerBtn.setOnClickListener(v -> {
            if (isSystem) return;
            timerMethodGroup.setVisibility(View.VISIBLE);
            permanentMethodGroup.setVisibility(View.GONE);
        });
        permanentBtn.setOnClickListener(v -> {
            if (isSystem) return;
            permanentMethodGroup.setVisibility(View.VISIBLE);
            timerMethodGroup.setVisibility(View.GONE);
        });

        container.addView(radioGroup);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.filter_freeze_type_dialog_title))
                .setView(container)
                .setNegativeButton(context.getString(R.string.dialog_cancel), null)
                .setPositiveButton(context.getString(R.string.dialog_apply), (dialog, which) -> {
                    SleepModeManager.FreezeType chosenType = permanentBtn.isChecked()
                            ? SleepModeManager.FreezeType.PERMANENT
                            : SleepModeManager.FreezeType.TIMER;
                    boolean suspendChosen = chosenType == SleepModeManager.FreezeType.PERMANENT
                            ? permanentSuspendBtn.isChecked()
                            : timerSuspendBtn.isChecked();
                    SleepModeManager.FreezeMethod chosenMethod = (isSystem || suspendChosen)
                            ? SleepModeManager.FreezeMethod.SUSPEND
                            : SleepModeManager.FreezeMethod.DISABLE;
                    freezeTypeMap.put(app.getPackageName(), chosenType);
                    freezeMethodMap.put(app.getPackageName(), chosenMethod);
                    chipView.setText(badgeLabelFreeze(chosenType));
                    notifySelectionChanged();
                })
                .show();

        if (hasAccent()) {
            android.content.res.ColorStateList tint =
                    android.content.res.ColorStateList.valueOf(accentColor);
            timerBtn.setButtonTintList(tint);
            permanentBtn.setButtonTintList(tint);
            timerSuspendBtn.setButtonTintList(tint);
            timerDisableBtn.setButtonTintList(tint);
            permanentSuspendBtn.setButtonTintList(tint);
            permanentDisableBtn.setButtonTintList(tint);
        }
    }

    private void showRestrictionTypeDialog(AppModel app, TextView chipView) {
        BackgroundAppManager.RestrictionType current =
                restrictionTypeMap.getOrDefault(app.getPackageName(),
                        BackgroundAppManager.RestrictionType.SOFT);

        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(0, 16, 0, 8);

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(context);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        int paddingH = (int) (context.getResources().getDisplayMetrics().density * 24);

        android.widget.RadioButton softBtn = new android.widget.RadioButton(context);
        softBtn.setId(View.generateViewId());
        softBtn.setText(context.getString(R.string.filter_restriction_soft_option));
        softBtn.setPadding(paddingH, 24, paddingH, 24);
        softBtn.setChecked(current == BackgroundAppManager.RestrictionType.SOFT);
        radioGroup.addView(softBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton mediumBtn = new android.widget.RadioButton(context);
        mediumBtn.setId(View.generateViewId());
        mediumBtn.setText(context.getString(R.string.filter_restriction_medium_option));
        mediumBtn.setPadding(paddingH, 24, paddingH, 24);
        mediumBtn.setChecked(current == BackgroundAppManager.RestrictionType.MEDIUM);
        radioGroup.addView(mediumBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton hardBtn = new android.widget.RadioButton(context);
        hardBtn.setId(View.generateViewId());
        hardBtn.setText(context.getString(R.string.filter_restriction_hard_option));
        hardBtn.setPadding(paddingH, 24, paddingH, 24);
        hardBtn.setChecked(current == BackgroundAppManager.RestrictionType.HARD);
        radioGroup.addView(hardBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton manualBtn = new android.widget.RadioButton(context);
        manualBtn.setId(View.generateViewId());
        manualBtn.setText(context.getString(R.string.filter_restriction_manual_option));
        manualBtn.setPadding(paddingH, 24, paddingH, 24);
        manualBtn.setChecked(current == BackgroundAppManager.RestrictionType.MANUAL);
        radioGroup.addView(manualBtn);

        container.addView(radioGroup);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.filter_restriction_type_dialog_title))
                .setView(container)
                .setNegativeButton(context.getString(R.string.dialog_cancel), null)
                .setPositiveButton(context.getString(R.string.dialog_apply), (dialog, which) -> {
                    BackgroundAppManager.RestrictionType chosen;
                    if (hardBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.HARD;
                        restrictionTypeMap.put(app.getPackageName(), chosen);
                        manualOpsMaskMap.remove(app.getPackageName());
                        manualBucketMap.remove(app.getPackageName());
                        chipView.setText(badgeLabel(chosen));
                        notifySelectionChanged();
                    } else if (mediumBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.MEDIUM;
                        restrictionTypeMap.put(app.getPackageName(), chosen);
                        manualOpsMaskMap.remove(app.getPackageName());
                        manualBucketMap.remove(app.getPackageName());
                        chipView.setText(badgeLabel(chosen));
                        notifySelectionChanged();
                    } else if (manualBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.MANUAL;
                        restrictionTypeMap.put(app.getPackageName(), chosen);
                        int existingMask = manualOpsMaskMap.getOrDefault(
                                app.getPackageName(), 0x01);
                        int existingBucket = manualBucketMap.getOrDefault(
                                app.getPackageName(), 0);
                        showManualOpsDialog(app, chipView, existingMask, existingBucket);
                    } else {
                        chosen = BackgroundAppManager.RestrictionType.SOFT;
                        restrictionTypeMap.remove(app.getPackageName());
                        manualOpsMaskMap.remove(app.getPackageName());
                        manualBucketMap.remove(app.getPackageName());
                        chipView.setText(badgeLabel(chosen));
                        notifySelectionChanged();
                    }
                })
                .show();

        if (hasAccent()) {
            android.content.res.ColorStateList tint =
                    android.content.res.ColorStateList.valueOf(accentColor);
            softBtn.setButtonTintList(tint);
            mediumBtn.setButtonTintList(tint);
            hardBtn.setButtonTintList(tint);
            manualBtn.setButtonTintList(tint);
        }
    }

    private void showManualOpsDialog(AppModel app, TextView chipView, int currentMask, int currentBucket) {
        String[] ops = BackgroundAppManager.ALL_OPS;

        String[] labels = {
            context.getString(R.string.manual_op_run_any_in_background),
            context.getString(R.string.manual_op_run_in_background),
            context.getString(R.string.manual_op_start_foreground),
            context.getString(R.string.manual_op_fgs_from_background),
            context.getString(R.string.manual_op_wake_lock),
            context.getString(R.string.manual_op_alarm_wakeup),
            context.getString(R.string.manual_op_boot_completed),
            context.getString(R.string.manual_op_interact_across_profiles),
        };

        boolean[] checked = new boolean[ops.length];
        for (int i = 0; i < ops.length; i++) {
            checked[i] = (currentMask & (1 << i)) != 0;
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        android.widget.LinearLayout listLayout = new android.widget.LinearLayout(context);
        listLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int paddingH = (int) (context.getResources().getDisplayMetrics().density * 16);
        int paddingV = (int) (context.getResources().getDisplayMetrics().density * 4);

        CheckBox[] boxes = new CheckBox[ops.length];
        for (int i = 0; i < ops.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(context);
            cb.setText(labels[i]);
            cb.setChecked(checked[i]);
            cb.setPadding(paddingH, paddingV * 3, paddingH, paddingV * 3);
            cb.setOnCheckedChangeListener((btn, isChecked) -> checked[idx] = isChecked);
            boxes[i] = cb;
            listLayout.addView(cb);
        }

        View divider = new View(context);
        android.widget.LinearLayout.LayoutParams divParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(paddingH, paddingV * 3, paddingH, paddingV * 3);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(0x44888888);
        listLayout.addView(divider);

        final int[] selectedBucket = {currentBucket};

        CheckBox cbRare = new CheckBox(context);
        cbRare.setText(context.getString(R.string.manual_bucket_rare));
        cbRare.setChecked(currentBucket == 40);
        cbRare.setPadding(paddingH, paddingV * 3, paddingH, paddingV * 3);
        listLayout.addView(cbRare);

        CheckBox cbRestricted = new CheckBox(context);
        cbRestricted.setText(context.getString(R.string.manual_bucket_restricted));
        cbRestricted.setChecked(currentBucket == 45);
        cbRestricted.setPadding(paddingH, paddingV * 3, paddingH, paddingV * 3);
        listLayout.addView(cbRestricted);

        cbRare.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                selectedBucket[0] = 40;
                cbRestricted.setChecked(false);
            } else if (!cbRestricted.isChecked()) {
                selectedBucket[0] = 0;
            }
        });
        cbRestricted.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                selectedBucket[0] = 45;
                cbRare.setChecked(false);
            } else if (!cbRare.isChecked()) {
                selectedBucket[0] = 0;
            }
        });

        scrollView.addView(listLayout);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.filter_manual_ops_dialog_title,
                        app.getAppName()))
                .setView(scrollView)
                .setNegativeButton(context.getString(R.string.dialog_cancel), (d, w) -> {
                    if (!manualOpsMaskMap.containsKey(app.getPackageName())) {
                        restrictionTypeMap.remove(app.getPackageName());
                        chipView.setText(badgeLabel(BackgroundAppManager.RestrictionType.SOFT));
                        notifySelectionChanged();
                    }
                })
                .setPositiveButton(context.getString(R.string.dialog_apply), (d, w) -> {
                    int mask = 0;
                    for (int i = 0; i < ops.length; i++) {
                        if (checked[i]) mask |= (1 << i);
                    }
                    if (mask == 0) {
                        restrictionTypeMap.remove(app.getPackageName());
                        manualOpsMaskMap.remove(app.getPackageName());
                        manualBucketMap.remove(app.getPackageName());
                        chipView.setText(badgeLabel(BackgroundAppManager.RestrictionType.SOFT));
                    } else {
                        manualOpsMaskMap.put(app.getPackageName(), mask);
                        if (selectedBucket[0] != 0) {
                            manualBucketMap.put(app.getPackageName(), selectedBucket[0]);
                        } else {
                            manualBucketMap.remove(app.getPackageName());
                        }
                        chipView.setText(badgeLabel(BackgroundAppManager.RestrictionType.MANUAL));
                    }
                    notifySelectionChanged();
                })
                .show();

        if (hasAccent()) {
            android.content.res.ColorStateList tint =
                    android.content.res.ColorStateList.valueOf(accentColor);
            for (CheckBox cb : boxes) cb.setButtonTintList(tint);
            cbRare.setButtonTintList(tint);
            cbRestricted.setButtonTintList(tint);
        }
    }

    private View makeDivider(int paddingH) {
        View divider = new View(context);
        android.widget.LinearLayout.LayoutParams p =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(paddingH, 4, paddingH, 4);
        divider.setLayoutParams(p);
        divider.setBackgroundColor(0x22888888);
        return divider;
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) selectionChangedListener.onSelectionChanged();
    }

    @Override
    public Filter getFilter() {
        if (filter == null) filter = new AppFilter();
        return filter;
    }

    private class AppFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            lastConstraint = constraint;
            FilterResults results = new FilterResults();
            List<AppModel> filteredList = new ArrayList<>();
            String filterString = (constraint != null && constraint.length() > 0)
                    ? constraint.toString().toLowerCase().trim() : "";
            for (AppModel app : allApps) {
                if (!shouldShow(app)) continue;
                if (filterString.isEmpty()
                        || app.getAppName().toLowerCase().contains(filterString)
                        || app.getPackageName().toLowerCase().contains(filterString)) {
                    filteredList.add(app);
                }
            }
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredApps = (List<AppModel>) results.values;
            notifyDataSetChanged();
        }
    }

    public static class ViewHolder {
        public TextView appName;
        public TextView appStatus;
        public ImageView appIcon;
        public CheckBox checkBox;
        public TextView restrictionType;
    }
}
