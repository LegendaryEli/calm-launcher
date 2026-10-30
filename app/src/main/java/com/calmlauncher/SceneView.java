package com.calmlauncher;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen background. Photos dropped into assets/scenes crossfade with a slow zoom;
 * with no photos it paints a Mount Fuji scene that cycles dusk, night and dawn.
 * Everything is drawn in a 1920x1080 space and scaled to the view.
 */
public class SceneView extends View {

    interface CaptionListener {
        void onCaption(String caption);
    }

    private static final long PERIOD_MS = 20_000;
    private static final long PHOTO_FADE_MS = 2_500;
    private static final float DRAWN_FADE_S = 6f;
    private static final int PHOTO_WIDTH = 1920;
    private static final int BASE = 0xFF0D0F2E;

    // sky0..3, glow, fujiTop, fujiBase, cap, hillFar, hillNear, lakeTop, lakeMid, lakeBot, torii
    private static final int[][] PAL = {
            {0xFF0D0F2E, 0xFF2F2756, 0xFFA9577A, 0xFFF2A67C, 0xFFFFB487, 0xFF3B3A6B, 0xFF6D5A8C,
                    0xFFF6DBE3, 0xFF5A4A82, 0xFF2C2551, 0xFFC9788C, 0xFF4A3A70, 0xFF14122E, 0xFFB4435A},
            {0xFF03050E, 0xFF0A1030, 0xFF18215A, 0xFF2B3472, 0xFF4A5AA8, 0xFF0F1534, 0xFF202A5A,
                    0xFFB9C6F0, 0xFF1B2250, 0xFF0D1130, 0xFF2A3578, 0xFF121A4A, 0xFF050716, 0xFF6A2A40},
            {0xFF1A2750, 0xFF6A6AA6, 0xFFF0A0A0, 0xFFFFD7A0, 0xFFFFE0A8, 0xFF56649A, 0xFF9A87B0,
                    0xFFFFF1EA, 0xFF8A7FB4, 0xFF4A4A80, 0xFFF0B8A8, 0xFF6A6AA0, 0xFF1C1D44, 0xFFC85A5A}
    };
    private static final String[] NAMES = {"dusk", "night", "dawn"};
    private static final float[] STAR_ALPHA = {0.5f, 1f, 0.12f};
    private static final float[][] STARS = {
            {140, 70}, {330, 190}, {520, 60}, {700, 150}, {880, 90}, {1040, 210}, {1210, 70},
            {1390, 160}, {1560, 60}, {1730, 190}, {1850, 90}, {420, 260}, {1120, 280}, {1660, 290}
    };
    private static final float[][] SHIMMER = {
            {1080, 832, 320}, {1180, 850, 220}, {980, 872, 420}, {1240, 896, 360},
            {1100, 926, 500}, {1320, 960, 420}, {900, 1000, 600}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final long t0 = SystemClock.uptimeMillis();

    private final Path fujiBody = new Path();
    private final Path fujiCap = new Path();
    private final Path hillFar = new Path();
    private final Path hillNear = new Path();
    private final Path torii = new Path();
    private final RectF petal = new RectF();

    private List<String> photos = new ArrayList<>();
    private Bitmap cur;
    private Bitmap nxt;
    private int curIdx;
    private int nxtIdx;
    private long curStart;
    private long introStart;
    private long fadeStart;
    private boolean loading;

    private String caption = "";
    private CaptionListener listener;

    public SceneView(Context context) {
        super(context);
        buildPaths();
        try {
            String[] all = context.getAssets().list("scenes");
            if (all != null) {
                for (String name : all) {
                    String n = name.toLowerCase(Locale.ROOT);
                    if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                        photos.add(name);
                    }
                }
                Collections.sort(photos);
            }
        } catch (IOException ignored) {
            // No scenes folder: the drawn scene is used.
        }
    }

    void setCaptionListener(CaptionListener l) {
        listener = l;
        if (l != null && !caption.isEmpty()) l.onCaption(caption);
    }

    private void setCaption(String s) {
        if (s.equals(caption)) return;
        caption = s;
        if (listener != null) listener.onCaption(s);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (!photos.isEmpty()) {
            drawPhotos(c, now);
        } else {
            drawScene(c, now);
        }
        postInvalidateDelayed(33);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        loader.shutdownNow();
    }

    // ---- photos -------------------------------------------------------------------------

    private void drawPhotos(Canvas c, long now) {
        int w = getWidth();
        int h = getHeight();
        c.drawColor(BASE);
        if (cur == null) {
            if (!loading) load(0, true);
            return;
        }
        if (fadeStart == 0 && nxt == null && !loading && photos.size() > 1) {
            load((curIdx + 1) % photos.size(), false);
        }
        if (fadeStart == 0 && nxt != null && now - curStart >= PERIOD_MS) {
            fadeStart = now;
            setCaption(captionFor(nxtIdx));
        }
        float fade = 0f;
        if (fadeStart != 0) {
            fade = (now - fadeStart) / (float) PHOTO_FADE_MS;
            if (fade >= 1f) {
                Bitmap old = cur;
                cur = nxt;
                curIdx = nxtIdx;
                curStart = fadeStart;
                nxt = null;
                fadeStart = 0;
                fade = 0f;
                old.recycle();
            }
        }
        float intro = Math.min(1f, (now - introStart) / 1500f);
        drawCover(c, cur, w, h, zoom(now - curStart), Math.round(255 * intro));
        if (nxt != null && fade > 0f) {
            drawCover(c, nxt, w, h, zoom(now - fadeStart), Math.round(255 * fade));
        }
        c.drawColor(0x38050614);
    }

    private static float zoom(long elapsedMs) {
        return 1f + 0.08f * Math.min(1f, elapsedMs / (float) (PERIOD_MS + PHOTO_FADE_MS));
    }

    private void drawCover(Canvas c, Bitmap b, int w, int h, float zoom, int alpha) {
        float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
        c.save();
        c.translate(w / 2f, h / 2f);
        c.scale(s, s);
        paint.setShader(null);
        paint.setAlpha(alpha);
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, paint);
        paint.setAlpha(255);
        c.restore();
    }

    private void load(final int idx, final boolean first) {
        loading = true;
        final String name = photos.get(idx);
        try {
            loader.execute(() -> {
                final Bitmap b = decode(name);
                post(() -> {
                    loading = false;
                    if (b == null) {
                        if (first) photos = new ArrayList<>(); // nothing readable: fall back to the drawn scene
                        return;
                    }
                    if (first) {
                        cur = b;
                        curIdx = idx;
                        introStart = SystemClock.uptimeMillis();
                        curStart = introStart;
                        setCaption(captionFor(idx));
                    } else {
                        nxt = b;
                        nxtIdx = idx;
                    }
                });
            });
        } catch (RuntimeException e) {
            loading = false; // executor already shut down
        }
    }

    private Bitmap decode(String name) {
        String path = "scenes/" + name;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContext().getAssets().open(path)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= PHOTO_WIDTH) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            try (InputStream in = getContext().getAssets().open(path)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** "03-Kyoto_bamboo.jpg" becomes "Kyoto bamboo". */
    private String captionFor(int idx) {
        String n = photos.get(idx);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replaceAll("^\\d+[-_. ]+", "").replaceAll("[-_]+", " ").trim();
        if (n.isEmpty()) return "";
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ---- drawn scene --------------------------------------------------------------------

    private void buildPaths() {
        fujiBody.moveTo(600, 820);
        fujiBody.cubicTo(780, 740, 940, 560, 1050, 440);
        fujiBody.lineTo(1140, 350);
        fujiBody.lineTo(1230, 440);
        fujiBody.cubicTo(1340, 560, 1500, 740, 1680, 820);
        fujiBody.close();

        fujiCap.moveTo(1058, 430);
        fujiCap.quadTo(1090, 420, 1100, 405);
        fujiCap.lineTo(1118, 388);
        fujiCap.lineTo(1140, 352);
        fujiCap.lineTo(1162, 388);
        fujiCap.lineTo(1182, 405);
        fujiCap.quadTo(1192, 420, 1222, 430);
        fujiCap.lineTo(1200, 440);
        fujiCap.lineTo(1180, 424);
        fujiCap.lineTo(1160, 446);
        fujiCap.lineTo(1140, 424);
        fujiCap.lineTo(1120, 448);
        fujiCap.lineTo(1100, 424);
        fujiCap.lineTo(1080, 440);
        fujiCap.close();

        hillFar.moveTo(0, 770);
        hillNear.moveTo(0, 800);
        for (int k = 0; k < 2; k++) {
            float dx = 1920f * k;
            hillFar.cubicTo(200 + dx, 690, 420 + dx, 730, 640 + dx, 700);
            hillFar.cubicTo(860 + dx, 670, 1080 + dx, 740, 1300 + dx, 710);
            hillFar.cubicTo(1520 + dx, 680, 1740 + dx, 740, 1920 + dx, 770);
            hillNear.cubicTo(240 + dx, 745, 480 + dx, 790, 720 + dx, 770);
            hillNear.cubicTo(960 + dx, 750, 1200 + dx, 800, 1440 + dx, 775);
            hillNear.cubicTo(1680 + dx, 755, 1800 + dx, 790, 1920 + dx, 800);
        }
        hillFar.lineTo(3840, 900);
        hillFar.lineTo(0, 900);
        hillFar.close();
        hillNear.lineTo(3840, 900);
        hillNear.lineTo(0, 900);
        hillNear.close();

        torii.moveTo(-112, -172);
        torii.quadTo(0, -150, 112, -172);
        torii.lineTo(104, -148);
        torii.quadTo(0, -128, -104, -148);
        torii.close();
        torii.addRect(-100, -146, 100, -138, Path.Direction.CW);
        torii.addRect(-86, -112, 86, -102, Path.Direction.CW);
        torii.addRect(-72, -146, -56, 0, Path.Direction.CW);
        torii.addRect(56, -146, 72, 0, Path.Direction.CW);
    }

    private int blend(int k, int a, int b, float f) {
        return (Integer) argb.evaluate(f, PAL[a][k], PAL[b][k]);
    }

    private static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(255 * a) << 24);
    }

    private void drawScene(Canvas c, long now) {
        float t = (now - t0) / 1000f;
        float cycle = PERIOD_MS / 1000f;
        int s = (int) (t / cycle) % 3;
        int n = (s + 1) % 3;
        float f = Math.max(0f, ((t % cycle) - (cycle - DRAWN_FADE_S)) / DRAWN_FADE_S);
        setCaption("Mount Fuji, " + NAMES[f > 0.5f ? n : s]);

        int[] col = new int[PAL[0].length];
        for (int k = 0; k < col.length; k++) col[k] = blend(k, s, n, f);
        float stars = STAR_ALPHA[s] + (STAR_ALPHA[n] - STAR_ALPHA[s]) * f;

        c.drawColor(BASE);
        c.save();
        c.scale(getWidth() / 1920f, getHeight() / 1080f);
        float pan = 1.02f + 0.07f * (0.5f - 0.5f * (float) Math.cos(2 * Math.PI * t / 96f));
        c.scale(pan, pan, 1920 * 0.62f, 1080 * 0.62f);

        // sky
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, 860,
                new int[]{col[0], col[1], col[2], col[3]}, new float[]{0f, 0.45f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 860, paint);
        paint.setShader(null);

        // stars
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < STARS.length; i++) {
            float tw = 0.2f + 0.8f * (0.5f + 0.5f * (float) Math.sin(t * 1.6f + i * 1.7f));
            paint.setAlpha(Math.round(255 * Math.min(1f, stars * tw)));
            c.drawCircle(STARS[i][0], STARS[i][1], 2f, paint);
        }
        paint.setAlpha(255);

        // glow behind the mountain
        paint.setShader(new RadialGradient(1320, 800, 760,
                new int[]{alpha(col[4], 0.85f), alpha(col[4], 0f)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(1320, 800, 760, paint);

        // Mount Fuji
        LinearGradient fuji = new LinearGradient(0, 350, 0, 820, col[5], col[6], Shader.TileMode.CLAMP);
        c.save();
        c.translate(180, 0);
        paint.setShader(fuji);
        c.drawPath(fujiBody, paint);
        paint.setShader(null);
        paint.setColor(col[7]);
        paint.setAlpha(235);
        c.drawPath(fujiCap, paint);
        c.restore();
        paint.setAlpha(255);

        // drifting hills
        paint.setColor(col[8]);
        c.save();
        c.translate(-((t * 12f) % 1920f), 0);
        c.drawPath(hillFar, paint);
        c.restore();
        paint.setColor(col[9]);
        c.save();
        c.translate(-((t * 21f) % 1920f), 0);
        c.drawPath(hillNear, paint);
        c.restore();

        // lake and the mountain's reflection
        paint.setShader(new LinearGradient(0, 800, 0, 1080,
                new int[]{col[10], col[11], col[12]}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 800, 1920, 1080, paint);
        c.save();
        c.clipRect(0, 800, 1920, 1080);
        c.translate(180, 1600);
        c.scale(1, -1);
        paint.setShader(fuji);
        paint.setAlpha(56);
        c.drawPath(fujiBody, paint);
        c.restore();
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < SHIMMER.length; i++) {
            float ph = (float) Math.sin(t * 0.9f + i * 1.3f);
            paint.setAlpha(Math.round(255 * (0.12f + 0.19f * (0.5f + 0.5f * ph))));
            float dx = 30f * ph;
            c.drawRect(SHIMMER[i][0] + dx, SHIMMER[i][1], SHIMMER[i][0] + dx + SHIMMER[i][2], SHIMMER[i][1] + 2, paint);
        }
        paint.setAlpha(255);

        // torii standing in the water
        paint.setColor(col[13]);
        c.save();
        c.translate(1590, 930);
        c.save();
        c.scale(1, -1);
        paint.setAlpha(66);
        c.drawPath(torii, paint);
        c.restore();
        paint.setAlpha(255);
        c.drawPath(torii, paint);
        c.restore();

        // sakura petals
        for (int i = 0; i < 14; i++) {
            float x0 = ((i * 53 + 7) % 100) / 100f * 1920f;
            float size = 10 + (i * 7) % 12;
            float dur = 16 + (i * 5) % 11;
            float p = ((t + (i * 3.7f) % 20f) / dur) % 1f;
            float a = p < 0.08f ? p / 0.08f * 0.95f : (p > 0.85f ? (1f - p) / 0.15f * 0.9f : 0.9f);
            c.save();
            c.translate(x0 - 320f * p, -40f + 1160f * p);
            c.rotate(520f * p);
            paint.setColor(0xFFF2A0BB);
            paint.setAlpha(Math.round(255 * a));
            petal.set(-size / 2f, -size / 3f, size / 2f, size / 3f);
            c.drawOval(petal, paint);
            c.restore();
        }
        paint.setAlpha(255);
        c.restore();
    }
}
