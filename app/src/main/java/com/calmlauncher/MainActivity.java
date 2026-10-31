package com.calmlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calm Launcher: a clock and one "Daily" row of pinned apps over a slow-moving scenic background.
 * The last tile, "Other apps", opens a popup with everything else.
 * Long-press OK on an app tile for its menu.
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
    private static final int COLUMNS = 6;

    // Faint brand glows behind the three streaming tiles.
    private static final int[] TINT_NETFLIX = {0x6BE50914, 0x386E000C};
    private static final int[] TINT_PRIME = {0x6600A8E1, 0x380A3C78};
    private static final int[] TINT_YOUTUBE = {0x5CFF2828, 0x33780A0A};

    private Prefs prefs;
    private SceneView scene;
    private TextView caption;
    private LinearLayout dailyRow;
    private HorizontalScrollView dailyScroll;
    private List<AppRepo.App> apps = new ArrayList<>();
    private String focusKey;
    private Dialog appsDialog;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            rebuild();
        }
    };

    /** How an app is drawn on the home row. */
    private static final class Look {
        final Drawable icon;
        final int iconDp;
        final int[] tint;

        Look(Drawable icon, int iconDp, int[] tint) {
            this.icon = icon;
            this.iconDp = iconDp;
            this.tint = tint;
        }
    }

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
        dismissApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here closes the popup and jumps back to the first tile.
        dismissApps();
        dailyScroll.scrollTo(0, 0);
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
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM));

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

        // the Daily row, bottom left
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, 0, 0, dp(40));

        TextView label = new TextView(this);
        label.setText("Daily");
        label.setTextColor(0xCCFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.leftMargin = dp(60);
        column.addView(label, llp);

        dailyRow = new LinearLayout(this);
        dailyRow.setOrientation(LinearLayout.HORIZONTAL);
        dailyRow.setPadding(dp(60), dp(14), dp(60), dp(4));
        dailyRow.setClipChildren(false);
        dailyRow.setClipToPadding(false);
        dailyScroll = new HorizontalScrollView(this);
        dailyScroll.setHorizontalScrollBarEnabled(false);
        dailyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dailyScroll.setClipChildren(false);
        dailyScroll.setClipToPadding(false);
        dailyScroll.addView(dailyRow, new ViewGroup.LayoutParams(-2, -2));
        column.addView(dailyScroll, new LinearLayout.LayoutParams(-1, -2));

        root.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }

    // ---- home row -----------------------------------------------------------------------

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
        for (String pkg : daily) {
            AppRepo.App a = byPkg.get(pkg);
            if (a == null || hidden.contains(pkg)) continue;
            Look look = lookFor(a);
            dailyRow.addView(makeTile(a.pkg, a.label, look, () -> launch(a.launch), () -> appMenu(a, true)));
        }
        dailyRow.addView(makeTile("other", "Other apps",
                new Look(getDrawable(R.drawable.ic_apps), 36, null), this::showOtherApps, null));

        View target = focusKey == null ? null : findTile(focusKey);
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    /** Own white marks for the streaming trio, the app's own icon for anything else. */
    private Look lookFor(AppRepo.App a) {
        String p = a.pkg;
        if (p.startsWith("com.netflix.")) return new Look(getDrawable(R.drawable.ic_netflix), 42, TINT_NETFLIX);
        if (p.startsWith("com.amazon.avod") || p.startsWith("com.amazon.amazonvideo")) {
            return new Look(getDrawable(R.drawable.ic_prime), 42, TINT_PRIME);
        }
        if (p.startsWith("com.google.android.youtube")) {
            return new Look(getDrawable(R.drawable.ic_youtube), 42, TINT_YOUTUBE);
        }
        return new Look(a.icon, 40, null);
    }

    private View makeTile(final String key, CharSequence label, Look look,
                          final Runnable open, final Runnable menu) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.rightMargin = dp(18);
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
        if (look.tint != null) {
            View tint = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, look.tint);
            g.setCornerRadius(dp(15));
            tint.setBackground(g);
            box.addView(tint, new FrameLayout.LayoutParams(-1, -1));
        }
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(look.icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new FrameLayout.LayoutParams(dp(look.iconDp), dp(look.iconDp), Gravity.CENTER));
        column.addView(box, new LinearLayout.LayoutParams(dp(160), dp(90)));

        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(174));
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
        return dailyRow.getChildCount() > 0 ? ((ViewGroup) dailyRow.getChildAt(0)).getChildAt(0) : null;
    }

    private View findTile(String key) {
        return dailyRow.findViewWithTag(key);
    }

    // ---- the Other apps popup -----------------------------------------------------------

    private void showOtherApps() {
        dismissApps();
        Set<String> hidden = prefs.hidden();
        List<String> daily = prefs.daily();

        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        appsDialog = d;
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0x99050614));
        w.setLayout(-1, -1);

        FrameLayout frame = new FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(28), dp(24), dp(28), dp(28));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setCornerRadius(dp(22));
        sheetBg.setColor(0xC7181A34);
        sheetBg.setStroke(dp(1), GLASS_EDGE);
        sheet.setBackground(sheetBg);

        FrameLayout header = new FrameLayout(this);
        TextView title = new TextView(this);
        title.setText("Other apps");
        title.setTextColor(WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView hint = new TextView(this);
        hint.setText("Press Back to close");
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        header.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.bottomMargin = dp(18);
        sheet.addView(header, hlp);

        // every app that is not pinned or hidden, then Settings
        List<View> tiles = new ArrayList<>();
        for (final AppRepo.App a : apps) {
            if (daily.contains(a.pkg) || hidden.contains(a.pkg)) continue;
            tiles.add(sheetTile(d, a.label, a.icon, () -> launch(a.launch), () -> appMenu(a, false)));
        }
        tiles.add(sheetTile(d, "Settings", getDrawable(R.drawable.ic_settings), this::openSettings, this::restoreHidden));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout row = null;
        int rows = 0;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.bottomMargin = dp(12);
                grid.addView(row, rlp);
                rows++;
            }
            row.addView(tiles.get(i));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(grid);
        int gridHeight = Math.min(rows * 96 + 12, 300);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-2, dp(gridHeight)));

        frame.addView(sheet, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        d.setContentView(frame);
        d.setOnDismissListener(x -> {
            appsDialog = null;
            View other = findTile("other");
            if (other != null) other.requestFocus();
        });
        sheet.setAlpha(0f);
        sheet.setTranslationY(dp(12));
        sheet.animate().alpha(1f).translationY(0).setDuration(300).start();
        d.setOnShowListener(x -> {
            if (!tiles.isEmpty()) ((ViewGroup) tiles.get(0)).requestFocus();
        });
        d.show();
    }

    private void dismissApps() {
        if (appsDialog != null && appsDialog.isShowing()) appsDialog.dismiss();
        appsDialog = null;
    }

    private View sheetTile(final Dialog d, CharSequence label, Drawable icon,
                           final Runnable open, final Runnable menu) {
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(15));
        bg.setColor(GLASS);
        bg.setStroke(dp(1), GLASS_EDGE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(bg);
        box.setFocusable(true);
        box.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(76));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(7);
        box.addView(name, nlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(84));
        lp.rightMargin = dp(12);
        box.setLayoutParams(lp);

        box.setOnClickListener(v -> {
            d.dismiss();
            open.run();
        });
        box.setOnLongClickListener(v -> {
            d.dismiss();
            menu.run();
            return true;
        });
        box.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f)
                    .translationY(focused ? -dp(3) : 0).setDuration(200).start();
            bg.setColor(focused ? GLASS_FOCUS : GLASS);
            bg.setStroke(focused ? dp(2) : dp(1), focused ? WHITE : GLASS_EDGE);
        });
        return box;
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

    private void restoreHidden() {
        Set<String> hidden = prefs.hidden();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setHidden(new HashSet<String>());
        Toast.makeText(this, hidden.size() + " hidden app(s) restored", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
