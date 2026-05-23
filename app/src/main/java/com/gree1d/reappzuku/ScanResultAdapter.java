package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanResultAdapter extends RecyclerView.Adapter<ScanResultAdapter.ViewHolder> {

    private final Context                  context;
    private final List<ScanSystem.AppLoad> items;
    private final PackageManager           pm;

    public ScanResultAdapter(Context context, List<ScanSystem.AppLoad> items) {
        this.context = context;
        this.items   = items;
        this.pm      = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_scan_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanSystem.AppLoad load = items.get(position);

        boolean expanded = holder.findingsContainer.getVisibility() == View.VISIBLE;
        holder.appName.setText((expanded ? "▼ " : "▶ ") + load.appName);

        try {
            Drawable icon = pm.getApplicationIcon(load.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.findingsContainer.removeAllViews();
        buildFindingsView(holder.findingsContainer, load.findings);

        holder.header.setOnClickListener(v -> {
            boolean isExpanded = holder.findingsContainer.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                holder.findingsContainer.setVisibility(View.GONE);
                holder.appName.setText("▶ " + load.appName);
            } else {
                holder.findingsContainer.setVisibility(View.VISIBLE);
                holder.appName.setText("▼ " + load.appName);
            }
        });
    }

    private void buildFindingsView(LinearLayout container, List<ScanSystem.Finding> findings) {
        Map<ScanSystem.Category, List<String>> grouped = new LinkedHashMap<>();
        for (ScanSystem.Finding f : findings) {
            if (!grouped.containsKey(f.category)) grouped.put(f.category, new ArrayList<>());
            grouped.get(f.category).add(f.detail);
        }

        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (Map.Entry<ScanSystem.Category, List<String>> entry : grouped.entrySet()) {
            TextView title = (TextView) inflater.inflate(
                    R.layout.item_scan_category_title, container, false);
            title.setText(categoryTitle(entry.getKey()));
            container.addView(title);

            for (String detail : entry.getValue()) {
                if (detail == null || detail.trim().isEmpty()) continue;
                TextView sub = (TextView) inflater.inflate(
                        R.layout.item_scan_category_detail, container, false);
                sub.setText(detail);
                container.addView(sub);
            }
        }
    }

    private String categoryTitle(ScanSystem.Category cat) {
        switch (cat) {
            case WAKELOCK: return context.getString(R.string.scansystem_cat_wakelock);
            case NETWORK:  return context.getString(R.string.scansystem_cat_network);
            case FGS:      return context.getString(R.string.scansystem_cat_fgs);
            case ALARM:    return context.getString(R.string.scansystem_cat_alarm);
            case SENSOR:   return context.getString(R.string.scansystem_cat_sensor);
            case LOCATION: return context.getString(R.string.scansystem_cat_location);
            default:       return cat.name();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout header;
        final ImageView    appIcon;
        final TextView     appName;
        final LinearLayout findingsContainer;

        ViewHolder(View itemView) {
            super(itemView);
            header            = itemView.findViewById(R.id.scan_app_header);
            appIcon           = itemView.findViewById(R.id.scan_app_icon);
            appName           = itemView.findViewById(R.id.scan_app_name);
            findingsContainer = itemView.findViewById(R.id.scan_reasons_container);
        }
    }
}
