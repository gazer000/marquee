package com.marquee.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * TCL's Google TV sends the Home button to the stock launcher even when Marquee is the default home
 * app. This service takes Home back: it catches the Home key where the system lets it, and as a
 * fallback reopens Marquee whenever the stock home screen comes to the front. Because the system keeps
 * it running, it also opens the quick-settings bar for the Config button and a held Home, opens apps
 * for the Chromecast remote's YouTube and Netflix buttons, and carries out the fallback bar's sleep
 * timer.
 */
public class HomeRedirectService extends AccessibilityService {

    private static final long DEBOUNCE_MS = 600;
    private static final long LONG_PRESS_MS = 500;
    private static final int GOOGLE_VENDOR_ID = 0x18d1;
    private long lastLaunch;
    private boolean homeHeld;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable longPressHome = new Runnable() {
        @Override
        public void run() {
            homeHeld = true;
            openQuickSettings();
        }
    };

    private final Runnable sleep = new Runnable() {
        @Override
        public void run() {
            long left = SleepTimer.deadline(HomeRedirectService.this) - System.currentTimeMillis();
            if (left > 1000) {
                // Woken early (clock change or a new deadline): wait the rest.
                main.postDelayed(this, left);
                return;
            }
            SleepTimer.set(HomeRedirectService.this, 0);
            // On TV the lock-screen action puts the set into standby, like the power button.
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    };

    private final BroadcastReceiver timerChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Turning the TV off by hand ends the timer, as TCL's own one did.
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) SleepTimer.set(context, 0);
            else scheduleSleep();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter f = new IntentFilter(SleepTimer.ACTION_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(timerChanged, f, Context.RECEIVER_NOT_EXPORTED);
        scheduleSleep();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(sleep);
        main.removeCallbacks(longPressHome);
        try {
            unregisterReceiver(timerChanged);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void scheduleSleep() {
        main.removeCallbacks(sleep);
        long deadline = SleepTimer.deadline(this);
        if (deadline != 0) main.postDelayed(sleep, Math.max(0, deadline - System.currentTimeMillis()));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The service config limits events to the stock launcher packages.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) openMarquee();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_HOME:
                // Short press opens Marquee; holding it opens the quick-settings bar while still held,
                // as Google TV's dashboard did. Key repeats never reach this filter, so a timer
                // counted from the key's own down time tells them apart.
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        homeHeld = false;
                        main.removeCallbacks(longPressHome);
                        main.postAtTime(longPressHome, event.getDownTime() + LONG_PRESS_MS);
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    main.removeCallbacks(longPressHome);
                    if (homeHeld) return true;
                    // A release that arrived late still counts by its timestamps.
                    if (event.getEventTime() - event.getDownTime() >= LONG_PRESS_MS) openQuickSettings();
                    else openMarquee();
                }
                return true;
            case KeyEvent.KEYCODE_NOTIFICATION:
                // The Config button: SystemUI can't route it to Marquee, so open the bar from here.
                if (event.getAction() == KeyEvent.ACTION_UP) openQuickSettings();
                return true;
            case KeyEvent.KEYCODE_BUTTON_3:
            case KeyEvent.KEYCODE_BUTTON_4:
                // The Chromecast remote's YouTube and Netflix buttons. Google TV's launcher used to
                // open them, but Android blocks that once Marquee is home. Gamepads can send the same
                // codes, so only the Google remote's presses are taken.
                if (!fromGoogleRemote(event)) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    openApp(event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_3
                            ? "com.google.android.youtube.tv" : "com.netflix.ninja");
                }
                return true;
            default:
                return false;
        }
    }

    private static boolean fromGoogleRemote(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device != null && device.getVendorId() == GOOGLE_VENDOR_ID && !device.isVirtual()
                && (device.getSources() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD;
    }

    private void openApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        // Disabled or not installed: do nothing rather than open a store page.
        if (launch == null) return;
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException ignored) {
        }
    }

    private void openQuickSettings() {
        if (QuickPanelActivity.openTclBar(this)) return;
        startActivity(new Intent(this, QuickPanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onInterrupt() {
    }

    private void openMarquee() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        Intent home = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }
}
