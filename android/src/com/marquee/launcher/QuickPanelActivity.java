package com.marquee.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Answers the remote's Config button, over whatever app is in front. HomeRedirectService catches
 * the key (SystemUI only hands TOGGLE_NOTIFICATION_HANDLER_PANEL to built-in system apps, so the
 * intent filter below never fires on the TCL TV, but it does on stock builds).
 *
 * It opens TCL's own quick-settings bar (picture mode, sleep timer and the rest) from TV Settings.
 * If that is unavailable, it shows Marquee's bar instead: left/right picks a setting, up/down changes
 * it on the spot, Back or Config closes it, and it hides itself after a few seconds.
 */
public class QuickPanelActivity extends Activity {

    private static final long IDLE_MS = 8000;
    private static final long APPLY_DELAY_MS = 220;
    private static final ComponentName TCL_QUICK_SETTINGS =
            new ComponentName("com.android.tv.settings", "com.tcl.settings.ShowWindowService");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Tile> tiles = new ArrayList<>();
    private int focus;

    private final Runnable idle = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    /** One setting on the bar, or with control == null, a shortcut that OK opens. */
    private final class Tile {
        final TvControls.Control control;
        final Intent shortcut;
        final LinearLayout view;
        final TextView label, value, up, down;
        int pos;
        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                if (!control.apply(pos)) {
                    pos = control.current();
                    render();
                }
            }
        };

        Tile(TvControls.Control control, String name, Intent shortcut) {
            this.control = control;
            this.shortcut = shortcut;
            view = new LinearLayout(QuickPanelActivity.this);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setPadding(dp(20), dp(10), dp(20), dp(12));
            label = text(13, 0x99FFFFFF, 600, 14);
            label.setText((control != null ? control.label : name).toUpperCase());
            label.setLetterSpacing(0.08f);
            up = text(11, 0x00FFFFFF, 600, 14);
            up.setText("▲");
            value = text(26, 0xFFFFFFFF, 650, 32);
            down = text(11, 0x00FFFFFF, 600, 14);
            down.setText("▼");
            view.addView(label);
            view.addView(up, topMargin(6));
            view.addView(value);
            view.addView(down);
            if (control != null) pos = control.current();
            render();
        }

        void render() {
            value.setText(control != null ? control.valueAt(pos) : "Open");
            boolean focused = tiles.indexOf(this) == focus;
            int arrows = focused && control != null ? 0x80FFFFFF : 0x00FFFFFF;
            up.setTextColor(arrows);
            down.setTextColor(arrows);
            value.setTextColor(focused ? 0xFFFFFFFF : 0xB3FFFFFF);
            label.setTextColor(focused ? 0xCCFFFFFF : 0x80FFFFFF);
            GradientDrawable bg = null;
            if (focused) {
                bg = new GradientDrawable();
                bg.setColor(0x1FFFFFFF);
                bg.setStroke(dp(2), 0xD9FFFFFF);
                bg.setCornerRadius(dp(16));
            }
            view.setBackground(bg);
        }

        void step(int delta) {
            if (control == null) return;
            int n = control.count();
            int next = control instanceof TvControls.Level
                    ? Math.max(0, Math.min(n - 1, pos + delta))   // levels stop at the ends
                    : (pos + delta + n) % n;                        // choices wrap around
            if (next == pos) return;
            pos = next;
            render();
            main.removeCallbacks(apply);
            // Levels follow the remote live; choices wait for the scrolling to settle.
            if (control instanceof TvControls.Level) apply.run();
            else main.postDelayed(apply, APPLY_DELAY_MS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (openTclBar(this)) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // A soft fade from the bottom edge keeps the bar readable over any picture.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230), Gravity.BOTTOM));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        barLp.leftMargin = dp(52);
        barLp.rightMargin = dp(72);
        barLp.bottomMargin = dp(36);
        root.addView(bar, barLp);

        for (TvControls.Control c : TvControls.load(this)) tiles.add(new Tile(c, null, null));
        tiles.add(new Tile(null, "Wi-Fi", new Intent(Settings.ACTION_WIFI_SETTINGS)));
        // Remotes & accessories, where the remote's TV / speaker volume buttons are set up.
        Intent remotes = remotesIntent();
        if (remotes != null) tiles.add(new Tile(null, "Remotes", remotes));
        tiles.add(new Tile(null, "All settings", new Intent(Settings.ACTION_SETTINGS)));
        for (Tile t : tiles) {
            // Settings need room for "120 min left"; shortcuts only say "Open". Narrow shortcut
            // tiles leave the clock its full width.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(t.control != null ? 210 : 160),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            bar.addView(t.view, lp);
        }

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView clock = text(30, 0xFFFFFFFF, 500, 32);
        clock.setFontFeatureSettings("tnum");
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", new Date()));
        clock.setPadding(0, 0, 0, dp(14));
        bar.addView(clock);

        setContentView(root);
        MainActivity.hideSystemBars(getWindow());
        renderAll();
        main.postDelayed(idle, IDLE_MS);
    }

    /**
     * The Remotes & Accessories page, or null when this TV has none that Marquee may open. On Google
     * TV it is a settings slice that needs WRITE_SECURE_SETTINGS, so there the tile is left out.
     */
    private Intent remotesIntent() {
        Intent slice = new Intent("android.settings.SLICE_SETTINGS").putExtra("slice_uri",
                "content://com.google.android.tv.btservices.settings.sliceprovider/general");
        if (canOpen(slice)) return slice;
        Intent bluetooth = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        return canOpen(bluetooth) ? bluetooth : null;
    }

    private boolean canOpen(Intent intent) {
        android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(intent, 0);
        if (r == null || r.activityInfo == null) return false;
        String needed = r.activityInfo.permission;
        return needed == null || checkSelfPermission(needed) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Starts TCL's quick-settings bar, as its Settings app does. False if the TV refused. */
    static boolean openTclBar(android.content.Context context) {
        Intent show = new Intent("com.tcl.settings.SHOW_WINDOW")
                .setComponent(TCL_QUICK_SETTINGS)
                .putExtra("Type", "quicksettings");
        if (context.getPackageManager().resolveService(show, 0) == null) return false;
        try {
            try {
                return context.startService(show) != null;
            } catch (IllegalStateException backgroundLimit) {
                // TV Settings counts as a background app; this path is allowed for it.
                return context.startForegroundService(show) != null;
            }
        } catch (RuntimeException e) {
            // Missing or disabled TV Settings, or background-start limits: use Marquee's bar.
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Config button pressed again closes the bar.
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Settle any pending change before the bar goes away.
        for (Tile t : tiles) {
            if (t.control != null && main.hasCallbacks(t.apply)) {
                main.removeCallbacks(t.apply);
                t.apply.run();
            }
        }
        main.removeCallbacks(idle);
        if (!isFinishing()) finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        main.removeCallbacks(idle);
        main.postDelayed(idle, IDLE_MS);
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            // Handled here, before the views: the clickable backdrop would otherwise take OK as a
            // click and close the bar.
            if (event.getAction() == KeyEvent.ACTION_UP) onKeyUp(code, event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                tiles.get(focus).step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                tiles.get(focus).step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return true; // acted on at key-up
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                Tile t = tiles.get(focus);
                if (t.shortcut != null) open(t.shortcut);
                else t.step(1);
                return true;
            case KeyEvent.KEYCODE_NOTIFICATION:
            case KeyEvent.KEYCODE_SETTINGS:
                finish();
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private void move(int delta) {
        int next = Math.max(0, Math.min(tiles.size() - 1, focus + delta));
        if (next == focus) return;
        focus = next;
        renderAll();
    }

    private void renderAll() {
        for (Tile t : tiles) t.render();
    }

    private void open(Intent target) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            // A shortcut must never take Marquee down; fall back to the main settings page.
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
            }
        }
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, tiles.isEmpty() ? 0 : android.R.anim.fade_out);
    }

    private TextView text(float sp, int color, int weight, int opticalSize) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.inter(this, weight, opticalSize));
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout.LayoutParams topMargin(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
