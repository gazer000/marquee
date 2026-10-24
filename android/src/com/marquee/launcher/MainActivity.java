package com.marquee.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final float NAME_SP = 60f;
    // Per distance from the selection: vertical offset (dp), scale relative to NAME_SP, alpha.
    private static final float[] ROW_Y = {0f, 86f, 136f, 178f, 214f};
    private static final float[] ROW_SCALE = {1f, 0.46f, 0.38f, 0.32f, 0.28f};
    private static final float[] ROW_ALPHA = {1f, 0.55f, 0.26f, 0f, 0f};
    private static final float NAMES_LEFT_DP = 112f;
    private static final float RAIL_HEIGHT_DP = 136f;
    private static final long MOVE_MS = 240;
    private static final long FADE_MS = 360;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Interpolator ease = new DecelerateInterpolator(1.8f);

    private AppStore store;
    private List<AppStore.App> items = new ArrayList<>();
    private int index;

    private FrameLayout names;
    // One name view per app, kept across reloads so its text is rendered once, not on every move.
    private final Map<String, TextView> nameViews = new HashMap<>();
    private ImageView backdropBack, backdropFront;
    private Bitmap shownBackdrop;
    private Bitmap defaultBackdrop;
    private View railThumb;
    private TextView clock, date, netText;
    private WifiIcon wifiIcon;

    private ConnectivityManager connectivity;
    private WifiManager wifi;
    private NetworkCapabilities lastCaps;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (hasWindowFocus()) reload();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            lastCaps = caps;
            main.post(updateNetwork);
        }

        @Override
        public void onLost(Network network) {
            lastCaps = null;
            main.post(updateNetwork);
        }
    };

    private final Runnable updateNetwork = new Runnable() {
        @Override
        public void run() {
            renderNetwork();
            main.removeCallbacks(this);
            main.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        connectivity = getSystemService(ConnectivityManager.class);
        wifi = getSystemService(WifiManager.class);
        defaultBackdrop = Backdrop.render(AppStore.DEFAULT_COLOR, 0xFF3A2F55);
        setContentView(buildUi());
        // The backdrop covers the whole screen, so skip painting the window background under it.
        getWindow().setBackgroundDrawable(null);
        hideSystemBars(getWindow());

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter, Context.RECEIVER_EXPORTED);

        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f, Context.RECEIVER_EXPORTED);
        updateClock();
        main.post(updateNetwork);
        // Picks up changes made on the Customize screen, and apps installed while we were away.
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
        main.removeCallbacks(updateNetwork);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            connectivity.unregisterNetworkCallback(networkCallback);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        worker.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home jumps back to the first app.
        if (hasWindowFocus() && Intent.ACTION_MAIN.equals(intent.getAction()) && index != 0 && !items.isEmpty()) {
            select(0);
        }
    }

    // ---- UI ---------------------------------------------------------------------------------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);

        backdropBack = makeBackdropView();
        backdropFront = makeBackdropView();
        backdropBack.setImageBitmap(defaultBackdrop);
        shownBackdrop = defaultBackdrop;
        backdropFront.setAlpha(0f);
        root.addView(backdropBack, match());
        root.addView(backdropFront, match());

        // Position rail: a faint track with a bright segment marking where you are in the list.
        View railTrack = new View(this);
        railTrack.setBackground(rounded(0x26FFFFFF, dp(2)));
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(dp(3), dp(RAIL_HEIGHT_DP),
                Gravity.START | Gravity.CENTER_VERTICAL);
        trackLp.leftMargin = dp(72);
        root.addView(railTrack, trackLp);
        railThumb = new View(this);
        railThumb.setBackground(rounded(0xE6FFFFFF, dp(2)));
        root.addView(railThumb, new FrameLayout.LayoutParams(dp(3), dp(24), Gravity.START | Gravity.CENTER_VERTICAL));
        ((FrameLayout.LayoutParams) railThumb.getLayoutParams()).leftMargin = dp(72);

        // App names, center left.
        names = new FrameLayout(this);
        names.setClipChildren(false);
        FrameLayout.LayoutParams namesLp = match();
        namesLp.leftMargin = dp(NAMES_LEFT_DP);
        namesLp.rightMargin = dp(80);
        root.addView(names, namesLp);

        // Status, top right: the time, and below it the date and network.
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.END);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        statusLp.topMargin = dp(40);
        statusLp.rightMargin = dp(64);
        root.addView(status, statusLp);

        clock = text(40, 0xFFFFFFFF, 500, 32);
        clock.setLetterSpacing(-0.02f);
        clock.setFontFeatureSettings("tnum");
        clock.setIncludeFontPadding(false);
        status.addView(clock, wrapEnd());

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoLp = wrapEnd();
        infoLp.topMargin = dp(8);
        status.addView(info, infoLp);
        date = text(14, 0xC0FFFFFF, 500, 14);
        info.addView(date);
        TextView dot = text(14, 0x66FFFFFF, 500, 14);
        dot.setText("·");
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dotLp.leftMargin = dp(10);
        dotLp.rightMargin = dp(10);
        info.addView(dot, dotLp);
        wifiIcon = new WifiIcon(this);
        info.addView(wifiIcon, new LinearLayout.LayoutParams(dp(16), dp(13)));
        netText = text(14, 0xC0FFFFFF, 500, 14);
        LinearLayout.LayoutParams netLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        netLp.leftMargin = dp(7);
        info.addView(netText, netLp);

        // Key hints, bottom left, lined up with the names.
        LinearLayout hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        hints.setAlpha(0.7f);
        FrameLayout.LayoutParams hintsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        hintsLp.leftMargin = dp(NAMES_LEFT_DP);
        hintsLp.bottomMargin = dp(44);
        root.addView(hints, hintsLp);
        addHint(hints, "▲▼", "Browse");
        addHint(hints, "OK", "Open");
        addHint(hints, "Hold OK", "Customize");

        return root;
    }

    private ImageView makeBackdropView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        return v;
    }

    private void addHint(LinearLayout parent, String key, String label) {
        TextView chip = text(11, 0xE6FFFFFF, 600, 14);
        chip.setText(key);
        chip.setLetterSpacing(0.04f);
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(5));
        outline.setStroke(dp(1), 0x80FFFFFF);
        chip.setBackground(outline);
        chip.setPadding(dp(7), dp(2), dp(7), dp(3));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) chipLp.leftMargin = dp(28);
        parent.addView(chip, chipLp);
        TextView name = text(13, 0xB3FFFFFF, 500, 14);
        name.setText(label);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.leftMargin = dp(8);
        parent.addView(name, nameLp);
    }

    private TextView makeNameView(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_SP);
        t.setTypeface(Fonts.inter(this, 700, 32));
        t.setLetterSpacing(-0.03f);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.62f));
        t.setPivotX(0f);
        t.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int top, int r, int bottom, int ol, int ot, int or, int ob) {
                v.setPivotY((bottom - top) / 2f);
            }
        });
        // Rendered once into a GPU texture; moving, scaling and fading it then costs almost nothing.
        t.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        t.setAlpha(0f);
        return t;
    }

    private TextView text(float sp, int color, int weight, int opticalSize) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.inter(this, weight, opticalSize));
        t.setSingleLine(true);
        return t;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static LinearLayout.LayoutParams wrapEnd() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    static void hideSystemBars(Window window) {
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController c = window.getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // ---- Data -------------------------------------------------------------------------------

    private void reload() {
        String selected = items.isEmpty() ? null : items.get(index).packageName;
        Map<String, AppStore.App> previous = AppStore.index(items);
        List<AppStore.App> fresh = store.loadHomeList();
        // Reuse colors and backdrops already worked out for apps that have not changed.
        for (int i = 0; i < fresh.size(); i++) {
            AppStore.App old = previous.get(fresh.get(i).packageName);
            if (old != null && old.updatedAt == fresh.get(i).updatedAt) fresh.set(i, old);
        }
        items = fresh;
        index = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).packageName.equals(selected)) index = i;
        }

        Map<String, AppStore.App> current = AppStore.index(items);
        for (Iterator<Map.Entry<String, TextView>> it = nameViews.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, TextView> e = it.next();
            if (!current.containsKey(e.getKey())) {
                names.removeView(e.getValue());
                it.remove();
            }
        }
        for (AppStore.App app : items) {
            TextView t = nameViews.get(app.packageName);
            if (t == null) {
                t = makeNameView(app.label);
                nameViews.put(app.packageName, t);
                names.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
            } else if (!TextUtils.equals(t.getText(), app.label)) {
                t.setText(app.label);
            }
        }
        layoutNames(false);
        showBackdrop(false);

        final List<AppStore.App> snapshot = new ArrayList<>(items);
        final int first = index;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                // Selected app first so its backdrop appears as soon as possible.
                prepareAndShow(snapshot.get(Math.min(first, snapshot.size() - 1)));
                for (AppStore.App app : snapshot) prepareAndShow(app);
            }
        });
    }

    private void prepareAndShow(final AppStore.App app) {
        if (app.colorReady) return;
        store.prepare(app);
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!items.isEmpty() && items.get(index) == app) showBackdrop(true);
            }
        });
    }

    // ---- Selection --------------------------------------------------------------------------

    private void select(int newIndex) {
        int n = items.size();
        if (n == 0) return;
        index = ((newIndex % n) + n) % n;
        layoutNames(true);
        showBackdrop(true);
    }

    /** Signed distance from the selection, wrapping around the list: 0 is selected, -1 above, 1 below. */
    private int offsetOf(int i) {
        int n = items.size();
        int d = ((i - index) % n + n) % n;
        return d > n / 2 ? d - n : d;
    }

    private void layoutNames(boolean animate) {
        for (int i = 0; i < items.size(); i++) {
            TextView t = nameViews.get(items.get(i).packageName);
            int offset = offsetOf(i);
            Object tag = t.getTag();
            int previous = tag instanceof Integer ? (Integer) tag : offset;
            t.setTag(offset);
            t.animate().cancel();
            if (!animate) {
                applyState(t, offset);
                continue;
            }
            if (Math.abs(offset - previous) > 1) {
                // Wrapped around the end of the list: fade in from just beyond its new place.
                applyState(t, offset + (offset >= 0 ? 1 : -1));
            }
            t.animate()
                    .translationY(rowY(offset)).scaleX(rowScale(offset)).scaleY(rowScale(offset))
                    .alpha(rowAlpha(offset))
                    .setDuration(MOVE_MS)
                    .setInterpolator(ease)
                    .start();
        }
        moveRail(animate);
    }

    private void moveRail(boolean animate) {
        int n = items.size();
        if (n == 0) return;
        float track = dp(RAIL_HEIGHT_DP);
        int thumb = Math.max(dp(14), Math.round(track / n));
        if (railThumb.getLayoutParams().height != thumb) {
            railThumb.getLayoutParams().height = thumb;
            railThumb.requestLayout();
        }
        float y = n == 1 ? 0 : -track / 2 + thumb / 2f + index * (track - thumb) / (n - 1);
        railThumb.animate().cancel();
        if (animate) {
            railThumb.animate().translationY(y).setDuration(MOVE_MS).setInterpolator(ease).start();
        } else {
            railThumb.setTranslationY(y);
        }
    }

    private void applyState(View t, int offset) {
        t.setTranslationY(rowY(offset));
        t.setScaleX(rowScale(offset));
        t.setScaleY(rowScale(offset));
        t.setAlpha(rowAlpha(offset));
    }

    private float rowY(int offset) {
        int a = Math.min(Math.abs(offset), ROW_Y.length - 1);
        return Math.signum(offset) * dp(ROW_Y[a]) - dp(10);
    }

    private float rowScale(int offset) {
        return ROW_SCALE[Math.min(Math.abs(offset), ROW_SCALE.length - 1)];
    }

    private float rowAlpha(int offset) {
        return ROW_ALPHA[Math.min(Math.abs(offset), ROW_ALPHA.length - 1)];
    }

    /** Cross-fades to the selected app's pre-rendered backdrop: two textures, nothing redrawn. */
    private void showBackdrop(boolean animate) {
        if (items.isEmpty()) return;
        AppStore.App app = items.get(index);
        Bitmap target = app.backdrop != null ? app.backdrop : defaultBackdrop;
        backdropFront.animate().cancel();
        if (backdropFront.getAlpha() >= 0.5f && backdropFront.getDrawable() != null) {
            // A fade was mostly done: treat its image as the current one.
            backdropBack.setImageDrawable(backdropFront.getDrawable());
        }
        backdropFront.setAlpha(0f);
        if (target == shownBackdrop) {
            backdropBack.setImageBitmap(target);
            return;
        }
        shownBackdrop = target;
        if (!animate) {
            backdropBack.setImageBitmap(target);
            return;
        }
        backdropFront.setImageBitmap(target);
        backdropFront.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(ease)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        backdropBack.setImageDrawable(backdropFront.getDrawable());
                        backdropFront.setAlpha(0f);
                    }
                })
                .start();
    }

    private void launchSelected() {
        if (items.isEmpty()) return;
        AppStore.App app = items.get(index);
        try {
            startActivity(app.launchIntent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openCustomize() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ---- Keys -------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                select(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                select(index + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                event.startTracking();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                openCustomize();
                return true;
            case KeyEvent.KEYCODE_BACK:
                return true; // Nothing to go back to from home.
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isSelectKey(keyCode)) {
            openCustomize();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isSelectKey(keyCode)) {
            if (event.isTracking() && !event.isCanceled()) launchSelected();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyUp(keyCode, event);
    }

    private static boolean isSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    // ---- Status -----------------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        boolean is24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(is24 ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEE, MMM d", now));
    }

    @SuppressWarnings("deprecation")
    private void renderNetwork() {
        NetworkCapabilities caps = lastCaps;
        if (caps == null) {
            Network active = connectivity.getActiveNetwork();
            caps = active == null ? null : connectivity.getNetworkCapabilities(active);
        }
        if (caps == null) {
            wifiIcon.setLevel(-1, false);
            netText.setText("Offline");
            return;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            wifiIcon.setLevel(4, true);
            netText.setText("Ethernet");
            return;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo info = caps.getTransportInfo() instanceof WifiInfo ? (WifiInfo) caps.getTransportInfo() : null;
            if (info == null || info.getRssi() <= -127) info = wifi.getConnectionInfo();
            int rssi = info.getRssi();
            // calculateSignalLevel gives 0..max; map to 1..4 segments.
            int max = wifi.getMaxSignalLevel();
            int level = max > 0 ? 1 + Math.round(3f * wifi.calculateSignalLevel(rssi) / max) : 3;
            wifiIcon.setLevel(level, false);
            StringBuilder sb = new StringBuilder();
            int freq = info.getFrequency();
            if (freq >= 5925) sb.append("6 GHz");
            else if (freq >= 4900) sb.append("5 GHz");
            else if (freq > 0) sb.append("2.4 GHz");
            else sb.append("Wi-Fi");
            if (info.getLinkSpeed() > 0) sb.append("  ").append(info.getLinkSpeed()).append(" Mbps");
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if (!validated) sb.append(" · no internet");
            netText.setText(sb);
            return;
        }
        wifiIcon.setLevel(3, false);
        netText.setText("Connected");
    }
}
