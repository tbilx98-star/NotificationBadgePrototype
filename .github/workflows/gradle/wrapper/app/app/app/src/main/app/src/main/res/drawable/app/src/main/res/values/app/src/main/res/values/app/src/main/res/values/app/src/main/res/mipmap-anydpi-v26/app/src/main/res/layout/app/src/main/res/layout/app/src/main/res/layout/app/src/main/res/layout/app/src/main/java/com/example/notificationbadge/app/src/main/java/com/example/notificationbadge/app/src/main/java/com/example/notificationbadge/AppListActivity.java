package com.example.notificationbadge;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListActivity extends AppCompatActivity {
    private List<AppItem> appList = new ArrayList<>();
    private SharedPreferences prefs;
    private Set<String> selectedPackages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        prefs = getSharedPreferences("BadgePrefs", MODE_PRIVATE);
        selectedPackages = new HashSet<>(prefs.getStringSet("selectedApps", new HashSet<>()));

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            if (pm.getLaunchIntentForPackage(packageInfo.packageName) != null) {
                String appName = pm.getApplicationLabel(packageInfo).toString();
                boolean isSelected = selectedPackages.contains(packageInfo.packageName);
                appList.add(new AppItem(appName, packageInfo.packageName, isSelected));
            }
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new AppAdapter(appList));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Set<String> newSelected = new HashSet<>();
        for (AppItem item : appList) {
            if (item.selected) {
                newSelected.add(item.packageName);
            }
        }
        prefs.edit().putStringSet("selectedApps", newSelected).apply();
    }

    static class AppItem {
        String name;
        String packageName;
        boolean selected;
        AppItem(String name, String packageName, boolean selected) {
            this.name = name;
            this.packageName = packageName;
            this.selected = selected;
        }
    }

    class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        private List<AppItem> items;
        AppAdapter(List<AppItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = items.get(position);
            holder.textName.setText(item.name);
            holder.checkBox.setChecked(item.selected);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> item.selected = isChecked);
            holder.itemView.setOnClickListener(v -> {
                holder.checkBox.setChecked(!holder.checkBox.isChecked());
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView textName;
            ViewHolder(View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.checkbox_app);
                textName = itemView.findViewById(R.id.textview_app_name);
            }
        }
    }
}
