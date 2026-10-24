package com.marquee.launcher;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings the Config bar changes in place. Each one is a list of values with a current
 * position; the bar steps through them and calls apply() on every step.
 */
final class TvControls {

    abstract static class Control {
        final String label;
        Control(String label) {
            this.label = label;
        }
        abstract int count();
        abstract String valueAt(int i);
        abstract int current();
        /** Applies value i right away. Returns false if the TV refused it. */
        abstract boolean apply(int i);
    }

    /** A control backed by a fixed list of names. */
    abstract static class Choice extends Control {
        final String[] values;
        Choice(String label, String... values) {
            super(label);
            this.values = values;
        }
        @Override int count() { return values.length; }
        @Override String valueAt(int i) { return values[i]; }
    }

    /** 0..100 in steps, shown as a number. */
    abstract static class Level extends Control {
        final int step;
        Level(String label, int step) {
            super(label);
            this.step = step;
        }
        @Override int count() { return 100 / step + 1; }
        @Override String valueAt(int i) { return String.valueOf(i * step); }
    }

    static List<Control> load(Context context) {
        List<Control> list = new ArrayList<>();
        list.add(SleepTimer.control(context));
        return list;
    }
}
