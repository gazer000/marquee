package com.marquee.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A small Wi-Fi fan drawn with arcs. Level 0..4 fills that many segments; -1 draws it crossed out. */
final class WifiIcon extends View {
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int level = 4;
    private boolean ethernet;

    WifiIcon(Context context) {
        super(context);
        on.setColor(0xFFFFFFFF);
        off.setColor(0x40FFFFFF);
        on.setStyle(Paint.Style.STROKE);
        off.setStyle(Paint.Style.STROKE);
        on.setStrokeCap(Paint.Cap.ROUND);
        off.setStrokeCap(Paint.Cap.ROUND);
    }

    void setLevel(int level, boolean ethernet) {
        this.level = level;
        this.ethernet = ethernet;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float stroke = h / 9f;
        on.setStrokeWidth(stroke);
        off.setStrokeWidth(stroke);
        if (ethernet) {
            // Three boxes joined by lines: a simple wired-network glyph.
            on.setStyle(Paint.Style.STROKE);
            float s = h / 3.2f;
            canvas.drawRect(w / 2 - s / 2, stroke, w / 2 + s / 2, stroke + s, on);
            canvas.drawLine(w / 2, stroke + s, w / 2, h / 2 + stroke, on);
            canvas.drawLine(w * 0.2f, h / 2 + stroke, w * 0.8f, h / 2 + stroke, on);
            canvas.drawLine(w * 0.2f, h / 2 + stroke, w * 0.2f, h - s - stroke, on);
            canvas.drawLine(w * 0.8f, h / 2 + stroke, w * 0.8f, h - s - stroke, on);
            canvas.drawRect(w * 0.2f - s / 2, h - s - stroke, w * 0.2f + s / 2, h - stroke, on);
            canvas.drawRect(w * 0.8f - s / 2, h - s - stroke, w * 0.8f + s / 2, h - stroke, on);
            return;
        }
        float cx = w / 2f, cy = h - stroke;
        // Dot at the bottom, then three arcs.
        Paint dot = level >= 1 ? on : off;
        dot.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy - stroke * 0.3f, stroke * 0.9f, dot);
        dot.setStyle(Paint.Style.STROKE);
        float maxR = h - stroke * 1.5f;
        for (int i = 1; i <= 3; i++) {
            float r = maxR * i / 3f;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 225, 90, false, level > i ? on : off);
        }
        if (level < 0) {
            canvas.drawLine(w * 0.15f, h * 0.1f, w * 0.85f, h * 0.9f, on);
        }
    }
}
