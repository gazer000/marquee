package com.marquee.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Marquee's own sleep timer. The deadline lives in prefs; HomeRedirectService (an accessibility
 * service, so always running) waits for it and puts the TV to sleep.
 */
final class SleepTimer {

    static final int[] MINUTES = {0, 10, 20, 30, 45, 60, 90, 120, 180};
    static final String ACTION_CHANGED = "com.marquee.launcher.SLEEP_TIMER_CHANGED";
    private static final String KEY_DEADLINE = "sleep_deadline";
    private static final String KEY_MINUTES = "sleep_minutes";

    private SleepTimer() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("marquee", Context.MODE_PRIVATE);
    }

    /** Wall-clock time the TV should sleep, or 0 when the timer is off. */
    static long deadline(Context context) {
        long d = prefs(context).getLong(KEY_DEADLINE, 0);
        return d > System.currentTimeMillis() ? d : 0;
    }

    static void set(Context context, int minutes) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (minutes <= 0) e.remove(KEY_DEADLINE).remove(KEY_MINUTES);
        else e.putLong(KEY_DEADLINE, System.currentTimeMillis() + minutes * 60000L).putInt(KEY_MINUTES, minutes);
        e.commit();
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }

    static TvControls.Control control(final Context context) {
        final Context app = context.getApplicationContext();
        return new TvControls.Control("Sleep timer") {
            @Override
            int count() {
                return MINUTES.length;
            }

            @Override
            String valueAt(int i) {
                if (MINUTES[i] == 0) return "Off";
                // The running timer shows what is left rather than what was picked.
                if (i == current()) {
                    long left = deadline(app) - System.currentTimeMillis();
                    if (left > 0) return ((left + 59999) / 60000) + " min left";
                }
                int m = MINUTES[i];
                if (m < 60) return m + " min";
                return m % 60 == 0 ? m / 60 + "h" : m / 60 + "h" + m % 60;   // 90 -> "1h30"
            }

            @Override
            int current() {
                if (deadline(app) == 0) return 0;
                int picked = prefs(app).getInt(KEY_MINUTES, 0);
                for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == picked) return i;
                return 0;
            }

            @Override
            boolean apply(int i) {
                set(app, MINUTES[i]);
                return true;
            }
        };
    }
}
