package com.calmlauncher;

import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * The screensaver: the scenery full screen with a very large thin clock that drifts slowly
 * across the screen so nothing stays in one place. Pick it under
 * Settings > Device Preferences > Screen saver.
 */
public class CalmDream extends DreamService {

    private static final int WHITE = 0xFFFFFFFF;

    private ValueAnimator drift;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0D0F2E);

        SceneView scene = new SceneView(this);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00050614, 0x8C050614}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, dp(280), Gravity.BOTTOM));

        final LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 100);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setShadowLayer(dp(14), 0, dp(2), 0x73000000);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setPadding(dp(5), 0, 0, 0);
        date.setShadowLayer(dp(8), 0, dp(1), 0x66000000);
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        clp.setMargins(dp(60), 0, 0, dp(64));
        root.addView(clock, clp);

        final TextView caption = new TextView(this);
        caption.setTextColor(0xBFFFFFFF);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        caption.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        caption.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cap.setMargins(0, 0, dp(60), dp(48));
        root.addView(caption, cap);
        scene.setCaptionListener(s -> caption.setText(s));

        setContentView(root);

        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(90_000);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new AccelerateDecelerateInterpolator());
        drift.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            clock.setTranslationX(f * dp(260));
            clock.setTranslationY(-f * dp(70));
        });
        drift.start();
    }

    @Override
    public void onDetachedFromWindow() {
        if (drift != null) drift.cancel();
        super.onDetachedFromWindow();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
