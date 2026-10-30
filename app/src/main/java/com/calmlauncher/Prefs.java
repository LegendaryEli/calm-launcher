package com.calmlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pinned ("Daily") order and hidden apps, kept in SharedPreferences. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("calm", Context.MODE_PRIVATE);
    }

    /** Ordered pinned packages, or null before the first run. */
    List<String> daily() {
        String s = sp.getString("daily", null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    void setDaily(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        sp.edit().putString("daily", b.toString()).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", new HashSet<String>()));
    }

    void setHidden(Set<String> hidden) {
        sp.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
    }
}
