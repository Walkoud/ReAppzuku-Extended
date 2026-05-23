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

import java.util.List;

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

        boolean expanded = holder.reasonsContainer.getVisibility() == View.VISIBLE;
        holder.appName.setText((expanded ? "▼ " : "▶ ") + load.appName);

        try {
            Drawable icon = pm.getApplicationIcon(load.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.reasonsContainer.removeAllViews();
        for (String reason : load.reasons) {
            TextView tv = new TextView(context);
            tv.setText("• " + reason);
            tv.setTextAppearance(context, android.R.style.TextAppearance_Small);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (2 * context.getResources().getDisplayMetrics().density);
            tv.setLayoutParams(lp);
            holder.reasonsContainer.addView(tv);
        }

        holder.header.setOnClickListener(v -> {
            boolean isExpanded = holder.reasonsContainer.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                holder.reasonsContainer.setVisibility(View.GONE);
                holder.appName.setText("▶ " + load.appName);
            } else {
                holder.reasonsContainer.setVisibility(View.VISIBLE);
                holder.appName.setText("▼ " + load.appName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout header;
        final ImageView    appIcon;
        final TextView     appName;
        final LinearLayout reasonsContainer;

        ViewHolder(View itemView) {
            super(itemView);
            header           = itemView.findViewById(R.id.scan_app_header);
            appIcon          = itemView.findViewById(R.id.scan_app_icon);
            appName          = itemView.findViewById(R.id.scan_app_name);
            reasonsContainer = itemView.findViewById(R.id.scan_reasons_container);
        }
    }
}
