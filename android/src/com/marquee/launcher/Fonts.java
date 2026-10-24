package com.marquee.launcher;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/** Inter (variable font, bundled in assets) at the weights the UI uses, built once and cached. */
final class Fonts {
    private static final Map<String, Typeface> cache = new HashMap<>();

    private Fonts() {
    }

    /** weight 100..900; opticalSize 14 (text) .. 32 (display). */
    static synchronized Typeface inter(Context context, int weight, int opticalSize) {
        String key = weight + "/" + opticalSize;
        Typeface tf = cache.get(key);
        if (tf == null) {
            try {
                tf = new Typeface.Builder(context.getAssets(), "fonts/Inter.ttf")
                        .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + opticalSize)
                        .build();
            } catch (RuntimeException e) {
                tf = null;
            }
            if (tf == null) tf = Typeface.create(Typeface.SANS_SERIF, weight, false);
            cache.put(key, tf);
        }
        return tf;
    }
}
