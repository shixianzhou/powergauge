package com.powergauge.app;

import java.util.Locale;

/** 统一的数字格式：屏幕和通知里显示的写法保持一致。 */
public final class Fmt {

    private Fmt() {
    }

    public static String watts(double w) {
        return String.format(Locale.US, "%.2f", w);
    }

    public static String wattsShort(double w) {
        return String.format(Locale.US, "%.1f", w);
    }

    public static String volts(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    public static String amps(double a) {
        return String.format(Locale.US, "%.2f", a);
    }

    public static String temp(double c) {
        return Double.isNaN(c) ? "--" : String.format(Locale.US, "%.1f", c);
    }

    public static String percent(int p) {
        return p < 0 ? "--" : String.valueOf(p);
    }

    /** 剩余电量：整数 mAh，例如 "2130 mAh"。 */
    public static String mah(double mah) {
        return String.format(Locale.US, "%.0f mAh", mah);
    }

    public static String time() {
        return new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new java.util.Date());
    }
}
