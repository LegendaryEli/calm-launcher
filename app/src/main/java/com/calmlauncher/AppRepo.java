package com.calmlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the apps that can be opened from a TV home screen. */
final class AppRepo {
    static final class App {
        final String pkg;
        final CharSequence label;
        final Intent launch;
        final Drawable icon;

        App(String pkg, CharSequence label, Intent launch, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.launch = launch;
            this.icon = icon;
        }
    }

    // The home screen has its own Settings tile, so the system app is not listed twice.
    private static final String[] SKIP = {
            "com.android.tv.settings", "com.android.settings"
    };

    static List<App> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (byPkg.containsKey(pkg) || pkg.equals(ctx.getPackageName()) || skipped(pkg)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                byPkg.put(pkg, new App(pkg, ri.loadLabel(pm), launch, ri.loadIcon(pm)));
            }
        }
        List<App> apps = new ArrayList<>(byPkg.values());
        Collections.sort(apps, (a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        return apps;
    }

    private static boolean skipped(String pkg) {
        for (String s : SKIP) {
            if (s.equals(pkg)) return true;
        }
        return false;
    }
}
