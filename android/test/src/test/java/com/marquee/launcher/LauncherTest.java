package com.marquee.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class LauncherTest {

    private static final String OUT = System.getProperty("shots", "/tmp/shots");

    @Before
    public void installApps() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        add("com.google.android.youtube.tv", "YouTube", 0xFFFF0033, Color.WHITE);
        add("com.stremio.one", "Stremio", 0xFF7B5BF5, 0xFF1B1340);
        add("com.limelight", "Moonlight", 0xFF2C8FD8, 0xFF0B2236);
        add("com.netflix.ninja", "Netflix", 0xFF111111, 0xFFE50914);
        add("com.spotify.tv.android", "Spotify", 0xFF1DB954, 0xFF121212);
        add("com.plexapp.android", "Plex", 0xFF1F1F1F, 0xFFE5A00D);
    }

    private void add(String pkg, String label, int bg, int fg) {
        PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
        ShadowPackageManager spm = shadowOf(pm);
        ComponentName cn = new ComponentName(pkg, pkg + ".Main");
        ActivityInfo ai = spm.addActivityIfNotPresent(cn);
        ai.nonLocalizedLabel = label;
        ai.applicationInfo.nonLocalizedLabel = label;
        ai.icon = 0x7f010001;
        ai.applicationInfo.icon = 0x7f010001;
        spm.addDrawableResolution(pkg, 0x7f010001, icon(bg, fg));
        IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
        f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try {
            spm.addIntentFilterForActivity(cn, f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BitmapDrawable icon(int bg, int fg) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fg);
        c.drawCircle(48, 48, 24, p);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        }
    }

    private static void shot(View root, String name) throws Exception {
        new File(OUT).mkdirs();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static List<String> visibleNames(View root) {
        List<String> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(View v, List<String> out) {
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
        } else if (v instanceof TextView && v.getVisibility() == View.VISIBLE) {
            out.add(((TextView) v).getText() + " a=" + v.getAlpha());
        }
    }

    private static void press(android.app.Activity a, int key) {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    @Test
    public void homeScreenCyclesAndLaunches() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        settle();
        System.out.println("initial: " + visibleNames(root));
        shot(root, "home-youtube");

        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        settle();
        System.out.println("after down: " + visibleNames(root));
        shot(root, "home-stremio");

        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        settle();
        shot(root, "home-moonlight");

        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_UP); // wraps to the last entry
        settle();
        System.out.println("after wrap: " + visibleNames(root));
        shot(root, "home-customize");

        press(a, KeyEvent.KEYCODE_DPAD_DOWN); // back to YouTube
        settle();
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started = shadowOf(a).getNextStartedActivity();
        assertNotNull(started);
        assertEquals("com.google.android.youtube.tv", started.getComponent().getPackageName());
        System.out.println("launched: " + started);

        // Back does nothing on home.
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(false, a.isFinishing());

        c.pause().stop().destroy();
    }

    @Test
    public void customizeScreenTogglesAndReorders() throws Exception {
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity a = c.get();
        View root = a.getWindow().getDecorView();
        settle();
        shot(root, "customize");
        System.out.println("settings: " + visibleNames(root));

        AppStore store = new AppStore(a);
        System.out.println("order before: " + store.loadOrder(store.loadInstalled()));

        // Focused: YouTube toggle. Move right to its down arrow and press it: Stremio becomes first.
        System.out.println("touch mode: " + root.isInTouchMode());
        View focused = a.getCurrentFocus();
        System.out.println("focused: " + focused);
        // Robolectric does not run focus navigation for injected arrows, so click YouTube's down arrow directly.
        root.findViewById(3).performClick();
        settle();
        List<String> order = store.loadOrder(store.loadInstalled());
        System.out.println("order after move: " + order);
        assertEquals("com.stremio.one", order.get(0));
        assertEquals("com.google.android.youtube.tv", order.get(1));

        // Hide Netflix (shown by default) via its toggle.
        View netflixToggle = null;
        for (View v : root.getFocusables(View.FOCUS_DOWN)) {
            if (v.getId() == 1 && v.toString() != null) {
                List<String> t = new ArrayList<>();
                collect(v, t);
                if (!t.isEmpty() && t.toString().contains("Netflix")) netflixToggle = v;
            }
        }
        assertNotNull(netflixToggle);
        netflixToggle.performClick();
        settle();
        order = store.loadOrder(store.loadInstalled());
        System.out.println("order after hiding Netflix: " + order);
        assertEquals(false, order.contains("com.netflix.ninja"));
        shot(root, "customize-after");
        c.pause().stop().destroy();
    }

    @Test
    public void quickPanelOpensAndCloses() throws Exception {
        ActivityController<QuickPanelActivity> c = Robolectric.buildActivity(QuickPanelActivity.class,
                new Intent("android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL")).setup();
        QuickPanelActivity a = c.get();
        View root = a.getWindow().getDecorView();
        settle();
        System.out.println("panel: " + visibleNames(root));
        shot(root, "quick-panel");
        // Pressing the Config button again closes it.
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_NOTIFICATION));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_NOTIFICATION));
        assertEquals(true, a.isFinishing());
    }
}
