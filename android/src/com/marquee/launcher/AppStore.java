package com.marquee.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds installed apps, stores which ones the launcher shows, and works out each app's theme color. */
final class AppStore {

    static final class App {
        final String packageName;
        final String label;
        final Intent launchIntent;
        final Drawable banner; // may be null
        final Drawable icon;
        final long updatedAt;
        int color = DEFAULT_COLOR;
        int accent = DEFAULT_COLOR;
        boolean colorReady;
        Bitmap backdrop; // pre-rendered home screen background

        App(String packageName, String label, Intent launchIntent, Drawable banner, Drawable icon, long updatedAt) {
            this.packageName = packageName;
            this.label = label;
            this.launchIntent = launchIntent;
            this.banner = banner;
            this.icon = icon;
            this.updatedAt = updatedAt;
        }
    }

    static final int DEFAULT_COLOR = 0xFF2B3550;

    /** Pseudo package names for the two built-in entries at the end of the list. */
    static final String TV_SETTINGS = ":tv-settings";
    static final String CUSTOMIZE = ":customize";

    /** Shown by default, in this order, when they are installed. */
    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            "com.google.android.youtube.tv",
            "com.stremio.one",
            "com.stremio.app",
            "com.limelight.noir",
            "com.limelight",
            "com.google.android.youtube.tvmusic",
            "com.netflix.ninja",
            "com.android.vending");

    private static final String PREFS = "marquee";
    private static final String KEY_ORDER = "order";

    private final Context context;
    private final PackageManager pm;
    private final SharedPreferences prefs;

    AppStore(Context context) {
        this.context = context.getApplicationContext();
        this.pm = this.context.getPackageManager();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Every launchable app on the TV except this launcher, sorted by name. */
    List<App> loadInstalled() {
        Map<String, App> byPackage = new LinkedHashMap<>();
        addMatches(byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addMatches(byPackage, Intent.CATEGORY_LAUNCHER);
        List<App> apps = new ArrayList<>(byPackage.values());
        final Collator collator = Collator.getInstance();
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return collator.compare(a.label, b.label);
            }
        });
        return apps;
    }

    private void addMatches(Map<String, App> out, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> matches = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : matches) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || out.containsKey(pkg)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            long updatedAt = 0;
            try {
                PackageInfo info = pm.getPackageInfo(pkg, 0);
                updatedAt = info.lastUpdateTime;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            out.put(pkg, new App(pkg, String.valueOf(ri.loadLabel(pm)), launch,
                    ri.activityInfo.loadBanner(pm), ri.loadIcon(pm), updatedAt));
        }
    }

    /** Package names the user chose to show, in order. Falls back to the defaults on first run. */
    List<String> loadOrder(List<App> installed) {
        String saved = prefs.getString(KEY_ORDER, null);
        if (saved != null) {
            List<String> order = new ArrayList<>();
            for (String s : saved.split(",")) if (!s.isEmpty()) order.add(s);
            return order;
        }
        Map<String, App> byPackage = index(installed);
        List<String> order = new ArrayList<>();
        for (String pkg : DEFAULT_ORDER) if (byPackage.containsKey(pkg)) order.add(pkg);
        for (App app : installed) {
            if (order.size() >= 4) break;
            if (!order.contains(app.packageName)) order.add(app.packageName);
        }
        return order;
    }

    void saveOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : order) {
            if (sb.length() > 0) sb.append(',');
            sb.append(pkg);
        }
        prefs.edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    void resetOrder() {
        prefs.edit().remove(KEY_ORDER).apply();
    }

    /** The apps the home screen cycles through: chosen apps in order, then TV settings and Customize. */
    List<App> loadHomeList() {
        List<App> installed = loadInstalled();
        Map<String, App> byPackage = index(installed);
        List<App> list = new ArrayList<>();
        for (String pkg : loadOrder(installed)) {
            App app = byPackage.get(pkg);
            if (app != null) list.add(app);
        }
        addBuiltIns(list);
        return list;
    }

    private void addBuiltIns(List<App> list) {
        list.add(new App(TV_SETTINGS, "Settings",
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                null, null, 0));
        list.add(new App(CUSTOMIZE, "Customize",
                new Intent(context, SettingsActivity.class), null, null, 0));
    }

    static Map<String, App> index(List<App> apps) {
        Map<String, App> map = new HashMap<>();
        for (App app : apps) map.put(app.packageName, app);
        return map;
    }

    // ---- Theme color ------------------------------------------------------------------------

    /** Fills in app.color, app.accent and app.backdrop. Slow-ish, so call it off the main thread. */
    void prepare(App app) {
        if (app.colorReady) return;
        findColors(app);
        app.backdrop = Backdrop.render(app.color, app.accent);
        app.colorReady = true;
    }

    private void findColors(App app) {
        // The icon usually carries the brand color more faithfully than a banner with text on it.
        Drawable source = app.icon != null ? app.icon : app.banner;
        if (source == null) {
            app.color = app.packageName.equals(TV_SETTINGS) ? 0xFF3A4E66 : 0xFF4A3A70;
            app.accent = shiftHue(app.color, 40f);
            return;
        }
        String cacheKey = "colors2:" + app.packageName + ":" + app.updatedAt;
        int cached = prefs.getInt(cacheKey, 0);
        int cachedAccent = prefs.getInt(cacheKey + ":accent", 0);
        if (cached != 0 && cachedAccent != 0) {
            app.color = cached;
            app.accent = cachedAccent;
        } else {
            int[] found = themeColors(render(source, 48, 48));
            app.color = darkenForBackground(found[0]);
            app.accent = found[1] != 0 ? darkenForBackground(found[1]) : shiftHue(app.color, 35f);
            prefs.edit().putInt(cacheKey, app.color).putInt(cacheKey + ":accent", app.accent).apply();
        }
    }

    private static int shiftHue(int color, float degrees) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + degrees + 360f) % 360f;
        return Color.HSVToColor(hsv);
    }

    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Drawable copy = copyOf(d);
        copy.setBounds(0, 0, w, h);
        copy.draw(c);
        return bmp;
    }

    private static Drawable copyOf(Drawable d) {
        Drawable.ConstantState state = d.getConstantState();
        return state != null ? state.newDrawable().mutate() : d;
    }

    /**
     * Returns {main, accent}: the most prominent saturated hue in the bitmap (or its average color),
     * and a second hue at least 40 degrees away, or 0 when the icon has only one.
     */
    static int[] themeColors(Bitmap bmp) {
        final int bins = 24;
        float[] weight = new float[bins];
        float[][] sum = new float[bins][3];
        float[] hsv = new float[3];
        long avgR = 0, avgG = 0, avgB = 0, opaque = 0;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
            avgR += r; avgG += g; avgB += b; opaque++;
            Color.RGBToHSV(r, g, b, hsv);
            float s = hsv[1], v = hsv[2];
            if (v < 0.15f || s < 0.25f) continue;
            float wgt = s * s * v;
            int bin = ((int) (hsv[0] / (360f / bins))) % bins;
            weight[bin] += wgt;
            sum[bin][0] += r * wgt;
            sum[bin][1] += g * wgt;
            sum[bin][2] += b * wgt;
        }
        int best = -1;
        float bestScore = 0;
        for (int i = 0; i < bins; i++) {
            float score = weight[i] + 0.5f * (weight[(i + 1) % bins] + weight[(i + bins - 1) % bins]);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0 && weight[best] > px.length * 0.01f) {
            int second = -1;
            float secondWeight = 0;
            for (int i = 0; i < bins; i++) {
                int d = Math.abs(i - best);
                if (Math.min(d, bins - d) < 3) continue; // within 45 degrees of the main hue
                if (weight[i] > secondWeight) {
                    secondWeight = weight[i];
                    second = i;
                }
            }
            int accent = second >= 0 && secondWeight > weight[best] * 0.15f ? binColor(sum[second], weight[second]) : 0;
            return new int[]{binColor(sum[best], weight[best]), accent};
        }
        if (opaque == 0) return new int[]{DEFAULT_COLOR, 0};
        return new int[]{Color.rgb((int) (avgR / opaque), (int) (avgG / opaque), (int) (avgB / opaque)), 0};
    }

    private static int binColor(float[] rgbSum, float weight) {
        return Color.rgb((int) (rgbSum[0] / weight), (int) (rgbSum[1] / weight), (int) (rgbSum[2] / weight));
    }

    /** Keeps the hue but makes it deep enough that white text stays readable on top. */
    static int darkenForBackground(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.15f) {
            // Gray or white brand: use a neutral slate instead of muddy gray.
            return DEFAULT_COLOR;
        }
        hsv[1] = Math.max(0.55f, Math.min(hsv[1], 0.9f));
        hsv[2] = Math.min(hsv[2], 0.62f);
        return Color.HSVToColor(hsv);
    }
}
