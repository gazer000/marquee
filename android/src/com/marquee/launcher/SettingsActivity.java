package com.marquee.launcher;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Choose which apps the home screen shows and in what order. Each row has a toggle (OK shows or
 * hides the app) and up/down buttons to its right that move a shown app within the list.
 */
public class SettingsActivity extends Activity {

    private static final int ID_TOGGLE = 1, ID_UP = 2, ID_DOWN = 3;

    private AppStore store;
    private List<AppStore.App> installed;
    private Map<String, AppStore.App> byPackage;
    private List<String> order;
    private LinearLayout list;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        installed = store.loadInstalled();
        byPackage = AppStore.index(installed);
        order = new ArrayList<>();
        for (String pkg : store.loadOrder(installed)) if (byPackage.containsKey(pkg)) order.add(pkg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101218);
        root.setPadding(dp(96), dp(44), dp(96), 0);

        TextView title = text(34, 0xFFFFFFFF, Typeface.BOLD);
        title.setText("Customize apps");
        root.addView(title);
        TextView sub = text(16, 0x99FFFFFF, Typeface.NORMAL);
        sub.setText("OK shows or hides an app.  Move right to the arrows to reorder shown apps.");
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(20);
        root.addView(sub, subLp);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(48));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        MainActivity.hideSystemBars(getWindow());
        rebuild(order.isEmpty() ? null : order.get(0), ID_TOGGLE);
    }

    /** Redraws the list, then puts focus back on the given app's button. */
    private void rebuild(String focusPackage, int focusPart) {
        list.removeAllViews();
        list.addView(header("On home screen  ·  " + order.size()));
        View toFocus = null;
        for (int i = 0; i < order.size(); i++) {
            View row = row(byPackage.get(order.get(i)), true, i == 0, i == order.size() - 1);
            list.addView(row);
            if (order.get(i).equals(focusPackage)) toFocus = row.findViewById(focusPart);
        }
        list.addView(header("Other apps"));
        for (AppStore.App app : installed) {
            if (order.contains(app.packageName)) continue;
            View row = row(app, false, true, true);
            list.addView(row);
            if (app.packageName.equals(focusPackage)) toFocus = row.findViewById(ID_TOGGLE);
        }
        list.addView(resetRow());
        if (toFocus == null) toFocus = list.findViewById(ID_TOGGLE);
        if (toFocus != null) {
            final View target = toFocus;
            target.requestFocus();
            target.post(new Runnable() {
                @Override
                public void run() {
                    target.requestFocus();
                }
            });
        }
    }

    private View header(String label) {
        TextView t = text(14, 0x80FFFFFF, Typeface.BOLD);
        t.setText(label.toUpperCase());
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(12), dp(18), 0, dp(8));
        return t;
    }

    private View row(final AppStore.App app, final boolean shown, boolean first, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout toggle = new LinearLayout(this);
        toggle.setId(ID_TOGGLE);
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(dp(12), dp(8), dp(16), dp(8));
        toggle.setFocusable(true);
        toggle.setBackground(focusBackground());

        TextView check = text(20, shown ? 0xFF7CC4FF : 0x66FFFFFF, Typeface.BOLD);
        check.setText(shown ? "✓" : "+");
        check.setGravity(Gravity.CENTER);
        toggle.addView(check, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable art = app.banner != null ? app.banner : app.icon;
        icon.setImageDrawable(art);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(app.banner != null ? 64 : 36), dp(36));
        iconLp.leftMargin = dp(8);
        iconLp.rightMargin = dp(app.banner != null ? 14 : 28 + 14);
        toggle.addView(icon, iconLp);

        TextView name = text(20, shown ? 0xFFFFFFFF : 0xB3FFFFFF, Typeface.NORMAL);
        name.setText(app.label);
        name.setSingleLine(true);
        toggle.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (shown) order.remove(app.packageName);
                else order.add(app.packageName);
                store.saveOrder(order);
                rebuild(app.packageName, ID_TOGGLE);
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (shown) {
            row.addView(moveButton(app, "▲", -1, ID_UP, !first));
            row.addView(moveButton(app, "▼", 1, ID_DOWN, !last));
        } else {
            // Keep names aligned with the shown rows.
            row.addView(new View(this), new LinearLayout.LayoutParams(dp(56) * 2 + dp(16), 1));
        }
        return row;
    }

    private View moveButton(final AppStore.App app, String glyph, final int delta, final int id, boolean enabled) {
        TextView b = text(18, enabled ? 0xFFFFFFFF : 0x33FFFFFF, Typeface.NORMAL);
        b.setId(id);
        b.setText(glyph);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(enabled);
        b.setBackground(focusBackground());
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = order.indexOf(app.packageName);
                int j = i + delta;
                if (i < 0 || j < 0 || j >= order.size()) return;
                order.set(i, order.get(j));
                order.set(j, app.packageName);
                store.saveOrder(order);
                // Stay on the same arrow so repeated presses keep moving the app; switch arrows at the ends.
                int part = id;
                if (j == 0) part = ID_DOWN;
                if (j == order.size() - 1) part = ID_UP;
                if (order.size() == 1) part = ID_TOGGLE;
                rebuild(app.packageName, part);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(52));
        lp.leftMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private View resetRow() {
        TextView reset = text(18, 0xB3FFFFFF, Typeface.NORMAL);
        reset.setText("Reset to defaults");
        reset.setPadding(dp(12), dp(12), dp(12), dp(12));
        reset.setFocusable(true);
        reset.setBackground(focusBackground());
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.resetOrder();
                order = new ArrayList<>();
                for (String pkg : store.loadOrder(installed)) if (byPackage.containsKey(pkg)) order.add(pkg);
                rebuild(order.isEmpty() ? null : order.get(0), ID_TOGGLE);
                scroll.smoothScrollTo(0, 0);
            }
        });
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(24);
        reset.setLayoutParams(lp);
        return reset;
    }

    private Drawable focusBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x26FFFFFF);
        focused.setStroke(dp(2), 0xCCFFFFFF);
        focused.setCornerRadius(dp(12));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, new ColorDrawable(0));
        return states;
    }

    private TextView text(float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.inter(this, style == Typeface.BOLD ? 650 : 450, sp >= 24 ? 32 : 14));
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
