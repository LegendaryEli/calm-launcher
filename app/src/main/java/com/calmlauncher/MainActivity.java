package com.calmlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calm Launcher: a clock, a "Daily" row of pinned apps and an "Other apps" row,
 * over a slow-moving scenic background. Long-press OK on a tile for its menu.
 */
public class MainActivity extends Activity {

    // First-run Daily picks: the first installed package in each group wins.
    private static final String[][] DAILY_DEFAULTS = {
            {"com.netflix.ninja", "com.netflix.mediaclient"},
            {"com.amazon.amazonvideo.livingroom", "com.amazon.avod", "com.amazon.avod.thirdpartyclient"},
            {"com.google.android.youtube.tv", "com.google.android.youtube"}
    };

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GLASS = 0x1FFFFFFF;
    private static final int GLASS_FOCUS = 0x42FFFFFF;
    private static final int GLASS_EDGE = 0x29FFFFFF;

    private Prefs prefs;
    private SceneView scene;
    private TextView caption;
    private TextView dailyLabel;
    private LinearLayout dailyRow;
    private LinearLayout otherRow;
    private HorizontalScrollView dailyScroll;
    private HorizontalScrollView otherScroll;
    private List<AppRepo.App> apps = new ArrayList<>();
    private String focusKey;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            rebuild();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(buildRoot());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        rebuild();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(packageReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here jumps back to the first tile.
        dailyScroll.scrollTo(0, 0);
        otherScroll.scrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    // ---- layout -------------------------------------------------------------------------

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0D0F2E);

        scene = new SceneView(this);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80050614, 0x00050614}));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(160), Gravity.TOP));

        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00050614, 0x9E050614}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(330), Gravity.BOTTOM));

        // clock and date, top left
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 68);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setShadowLayer(dp(8), 0, dp(1), 0x59000000);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xE0FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setShadowLayer(dp(6), 0, dp(1), 0x59000000);
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clp.setMargins(dp(60), dp(34), 0, 0);
        root.addView(clock, clp);

        // scene caption, top right
        caption = new TextView(this);
        caption.setTextColor(0xCCFFFFFF);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        caption.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        caption.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cap.setMargins(0, dp(52), dp(60), 0);
        root.addView(caption, cap);
        scene.setCaptionListener(s -> caption.setText(s));

        // the two rows, bottom left
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, 0, 0, dp(26));

        dailyLabel = sectionLabel("Daily");
        column.addView(dailyLabel);
        dailyRow = newRow();
        dailyScroll = newScroll(dailyRow);
        column.addView(dailyScroll, new LinearLayout.LayoutParams(-1, -2));

        TextView other = sectionLabel("Other apps");
        ((LinearLayout.LayoutParams) other.getLayoutParams()).topMargin = dp(26);
        column.addView(other);
        otherRow = newRow();
        otherScroll = newScroll(otherRow);
        column.addView(otherScroll, new LinearLayout.LayoutParams(-1, -2));

        root.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(60);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(60), dp(14), dp(60), dp(4));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private HorizontalScrollView newScroll(LinearLayout row) {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.addView(row, new ViewGroup.LayoutParams(-2, -2));
        return sv;
    }

    // ---- content ------------------------------------------------------------------------

    private void rebuild() {
        apps = AppRepo.load(this);
        Map<String, AppRepo.App> byPkg = new HashMap<>();
        for (AppRepo.App a : apps) byPkg.put(a.pkg, a);

        List<String> daily = prefs.daily();
        if (daily == null) {
            daily = new ArrayList<>();
            for (String[] group : DAILY_DEFAULTS) {
                for (String pkg : group) {
                    if (byPkg.containsKey(pkg)) {
                        daily.add(pkg);
                        break;
                    }
                }
            }
            prefs.setDaily(daily);
        }
        Set<String> hidden = prefs.hidden();

        dailyRow.removeAllViews();
        otherRow.removeAllViews();
        for (String pkg : daily) {
            AppRepo.App a = byPkg.get(pkg);
            if (a != null && !hidden.contains(pkg)) dailyRow.addView(appTile(a, true));
        }
        for (AppRepo.App a : apps) {
            if (daily.contains(a.pkg) || hidden.contains(a.pkg)) continue;
            otherRow.addView(appTile(a, false));
        }
        otherRow.addView(makeTile("all", "All apps", getDrawable(R.drawable.ic_apps), false,
                this::showAllApps, null));
        otherRow.addView(makeTile("settings", "Settings", getDrawable(R.drawable.ic_settings), false,
                this::openSettings, this::restoreHidden));
        dailyLabel.setVisibility(dailyRow.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        dailyScroll.setVisibility(dailyRow.getChildCount() == 0 ? View.GONE : View.VISIBLE);

        View target = focusKey == null ? null : findTile(focusKey);
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    private View appTile(final AppRepo.App app, final boolean pinned) {
        return makeTile(app.pkg, app.label, pinned ? dailyIcon(app) : app.icon, pinned,
                () -> launch(app.launch), () -> appMenu(app, pinned));
    }

    /** Thin white line icons for the usual streaming trio, the app's own icon for anything else. */
    private Drawable dailyIcon(AppRepo.App a) {
        String p = a.pkg;
        if (p.startsWith("com.netflix.")) return getDrawable(R.drawable.ic_netflix);
        if (p.startsWith("com.amazon.avod") || p.startsWith("com.amazon.amazonvideo")) {
            return getDrawable(R.drawable.ic_prime);
        }
        if (p.startsWith("com.google.android.youtube")) return getDrawable(R.drawable.ic_youtube);
        return a.icon;
    }

    private View makeTile(final String key, CharSequence label, Drawable icon, boolean big,
                          final Runnable open, final Runnable menu) {
        int w = big ? 160 : 64;
        int h = big ? 90 : 64;
        int iconSize = big ? 36 : 28;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.rightMargin = dp(big ? 18 : 14);
        column.setLayoutParams(clp);

        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(15));
        bg.setColor(GLASS);
        bg.setStroke(dp(1), GLASS_EDGE);

        FrameLayout box = new FrameLayout(this);
        box.setBackground(bg);
        box.setFocusable(true);
        box.setClickable(true);
        box.setTag(key);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER));
        column.addView(box, new LinearLayout.LayoutParams(dp(w), dp(h)));

        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(w + 14));
        name.setAlpha(0f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(9);
        column.addView(name, nlp);

        box.setOnClickListener(v -> open.run());
        if (menu != null) {
            box.setOnLongClickListener(v -> {
                menu.run();
                return true;
            });
        }
        box.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f)
                    .translationY(focused ? -dp(3) : 0).setDuration(220).start();
            bg.setColor(focused ? GLASS_FOCUS : GLASS);
            bg.setStroke(focused ? dp(2) : dp(1), focused ? WHITE : GLASS_EDGE);
            name.animate().alpha(focused ? 1f : 0f).setDuration(200).start();
            if (focused) focusKey = key;
        });
        return column;
    }

    private View firstTile() {
        if (dailyRow.getChildCount() > 0) return ((ViewGroup) dailyRow.getChildAt(0)).getChildAt(0);
        if (otherRow.getChildCount() > 0) return ((ViewGroup) otherRow.getChildAt(0)).getChildAt(0);
        return null;
    }

    private View findTile(String key) {
        View v = dailyRow.findViewWithTag(key);
        return v != null ? v : otherRow.findViewWithTag(key);
    }

    // ---- actions ------------------------------------------------------------------------

    private void launch(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void appMenu(final AppRepo.App app, final boolean pinned) {
        String[] items = {pinned ? "Remove from Daily" : "Add to Daily", "Hide", "App info"};
        dialog().setTitle(app.label).setItems(items, (d, which) -> {
            List<String> daily = new ArrayList<>(prefs.daily());
            if (which == 0) {
                if (pinned) daily.remove(app.pkg);
                else daily.add(app.pkg);
                prefs.setDaily(daily);
                focusKey = app.pkg;
                rebuild();
            } else if (which == 1) {
                Set<String> hidden = prefs.hidden();
                hidden.add(app.pkg);
                prefs.setHidden(hidden);
                daily.remove(app.pkg);
                prefs.setDaily(daily);
                focusKey = null;
                rebuild();
            } else {
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }).show();
    }

    private void showAllApps() {
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).label.toString();
        dialog().setTitle("All apps")
                .setItems(labels, (d, which) -> launch(apps.get(which).launch))
                .show();
    }

    private void restoreHidden() {
        Set<String> hidden = prefs.hidden();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setHidden(new java.util.HashSet<String>());
        Toast.makeText(this, hidden.size() + " hidden app(s) restored", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
