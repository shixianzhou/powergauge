package com.powergauge.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 极简设置存储：电流单位、电芯数量、充电功率补偿因数。 */
public final class Prefs {

    public static final int UNIT_UA = 0;
    public static final int UNIT_MA = 1;

    private static final String FILE = "powergauge_settings";
    private static Prefs sInstance;

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context context) {
        if (sInstance == null) {
            sInstance = new Prefs(context);
        }
        return sInstance;
    }

    public int unit() {
        return sp.getInt("unit", UNIT_UA);
    }

    public void setUnit(int unit) {
        sp.edit().putInt("unit", unit).apply();
    }

    public int cells() {
        int v = sp.getInt("cells", 1);
        return Math.max(1, Math.min(3, v));
    }

    public void setCells(int cells) {
        sp.edit().putInt("cells", Math.max(1, Math.min(3, cells))).apply();
    }

    public float compFactor() {
        float v = sp.getFloat("comp", 1.0f);
        return Math.max(0.5f, Math.min(2.0f, v));
    }

    public void setCompFactor(float factor) {
        sp.edit().putFloat("comp", Math.max(0.5f, Math.min(2.0f, factor))).apply();
    }

    /** 界面/曲线/通知的刷新间隔，单位毫秒，只允许 1s / 3s / 5s。 */
    public int refreshMs() {
        int v = sp.getInt("refresh_ms", 1000);
        return v == 3000 || v == 5000 ? v : 1000;
    }

    public void setRefreshMs(int ms) {
        int v = ms == 3000 || ms == 5000 ? ms : 1000;
        sp.edit().putInt("refresh_ms", v).apply();
    }

    public String refreshLabel() {
        return (refreshMs() / 1000) + " 秒";
    }

    /** 主题模式：见 {@link Theming}，默认深色。 */
    public int themeMode() {
        int v = sp.getInt("theme_mode", Theming.DARK);
        return v >= 0 && v <= 3 ? v : Theming.DARK;
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt("theme_mode", Math.max(0, Math.min(3, mode))).apply();
    }

    /** 悬浮窗开关。真正能不能显示还要看系统有没有授予「在其他应用上层显示」。 */
    public boolean overlayEnabled() {
        return sp.getBoolean("overlay", false);
    }

    public void setOverlayEnabled(boolean enabled) {
        sp.edit().putBoolean("overlay", enabled).apply();
    }
}
