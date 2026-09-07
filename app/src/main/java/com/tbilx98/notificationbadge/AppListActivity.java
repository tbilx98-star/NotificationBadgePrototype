package com.tbilx98.notificationbadge;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

public class AppListActivity extends Activity {

    private ArrayList<AppItem> apps;
    private Set<String> selected;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_app_list);

        selected = Prefs.getSelected(this);
        apps = new ArrayList<>();

        PackageManager pm = getPackageManager();

        for (ApplicationInfo ai :
                pm.getInstalledApplications(PackageManager.GET_META_DATA)) {

            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            if (getPackageName().equals(ai.packageName)) {
                continue;
            }

            CharSequence label = ai.loadLabel(pm);

            apps.add(
                    new AppItem(
                            ai.packageName,
                            label == null ? ai.packageName : label.toString(),
                            ai.loadIcon(pm)
                    )
            );
        }

        Collections.sort(
                apps,
                Comparator.comparing(a -> a.name.toLowerCase())
        );

        ((ListView) findViewById(R.id.list))
                .setAdapter(new Adapter());
    }

    private class Adapter extends BaseAdapter {

        public int getCount() {
            return apps.size();
        }

        public Object getItem(int p) {
            return apps.get(p);
        }

        public long getItemId(int p) {
            return p;
        }

        public View getView(
                int p,
                View convert,
                ViewGroup parent) {

            View v = convert;

            if (v == null) {
                v = LayoutInflater
                        .from(AppListActivity.this)
                        .inflate(
                                R.layout.item_app,
                                parent,
                                false
                        );
            }

            AppItem a = apps.get(p);

            ((ImageView) v.findViewById(R.id.icon))
                    .setImageDrawable(a.icon);

            ((TextView) v.findViewById(R.id.name))
                    .setText(a.name);

            CheckBox cb = v.findViewById(R.id.check);

            cb.setOnCheckedChangeListener(null);

            cb.setChecked(selected.contains(a.pkg));

            cb.setOnCheckedChangeListener(
                    (button, checked) -> {

                        if (checked) {
                            selected.add(a.pkg);
                        } else {
                            selected.remove(a.pkg);
                        }

                        Prefs.setSelected(
                                AppListActivity.this,
                                selected
                        );

                        sendBroadcast(
                                new Intent(
                                        BadgeAccessibilityService.ACTION_SELECTION_CHANGED
                                ).setPackage(getPackageName())
                        );
                    }
            );

            v.setOnClickListener(
                    x -> cb.setChecked(!cb.isChecked())
            );

            return v;
        }
    }

    private static class AppItem {

        String pkg;
        String name;
        Drawable icon;

        AppItem(
                String p,
                String n,
                Drawable i) {

            pkg = p;
            name = n;
            icon = i;
        }
    }
}
