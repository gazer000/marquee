package com.marquee.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Paints an app's backdrop once into a small bitmap: a deep base in its color, a soft glow of that
 * color on the right, a glow of its accent at the bottom left, and the dark scrims that keep text
 * readable. The image has no detail, so the GPU can stretch it to full screen without visible
 * pixels, and the home screen only has to cross-fade two textures instead of redrawing gradients.
 */
final class Backdrop {
    static final int WIDTH = 480, HEIGHT = 270;

    private Backdrop() {
    }

    static Bitmap render(int primary, int accent) {
        Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float w = WIDTH, h = HEIGHT;

        c.drawColor(scale(primary, 0.30f));
        glow(c, p, w * 0.82f, h * 0.28f, w * 0.80f, primary, 0.95f);
        glow(c, p, w * 0.08f, h * 1.08f, w * 0.62f, accent, 0.70f);

        // Darken the left side, where the names sit, and the top, where the clock sits.
        p.setShader(new LinearGradient(0, 0, w * 0.75f, 0,
                new int[]{0xB8000000, 0x55000000, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.35f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * 0.75f, 0, h, 0x00000000, 0x59000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return bmp;
    }

    private static void glow(Canvas c, Paint p, float cx, float cy, float r, int color, float strength) {
        p.setShader(new RadialGradient(cx, cy, r,
                new int[]{withAlpha(color, strength), withAlpha(color, strength * 0.45f), withAlpha(color, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, WIDTH, HEIGHT, p);
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int scale(int color, float f) {
        return Color.rgb(Math.round(Color.red(color) * f), Math.round(Color.green(color) * f),
                Math.round(Color.blue(color) * f));
    }
}
