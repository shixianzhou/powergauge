package com.powergauge.app;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 主题与配色：深色 / 浅色 / 跟随系统 / AMOLED 黑。
 * 颜色一律从这里取，界面里不再出现写死的色值，切换主题后 recreate() 即可整体变色。
 */
public final class Theming {

    public static final int DARK = 0;
    public static final int LIGHT = 1;
    public static final int SYSTEM = 2;
    public static final int AMOLED = 3;

    public static final String[] MODE_NAMES = {"深色", "浅色", "跟随系统", "AMOLED 黑"};

    private Theming() {
    }

    public static boolean isDark(Context context) {
        switch (Prefs.get(context).themeMode()) {
            case LIGHT:
                return false;
            case DARK:
            case AMOLED:
                return true;
            default:
                int mode = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                return mode == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    public static boolean isAmoled(Context context) {
        return Prefs.get(context).themeMode() == AMOLED;
    }

    public static int bg(Context context) {
        if (isDark(context)) {
            return isAmoled(context) ? 0xFF000000 : 0xFF0B0E13;
        }
        return 0xFFF4F6FA;
    }

    public static int card(Context context) {
        if (isDark(context)) {
            return isAmoled(context) ? 0xFF070708 : 0xFF11161F;
        }
        return 0xFFFFFFFF;
    }

    /** 卡片里再低一层的底色：选项 chip、说明块。 */
    public static int soft(Context context) {
        if (isDark(context)) {
            return isAmoled(context) ? 0xFF121214 : 0xFF1A2230;
        }
        return 0xFFEDF1F7;
    }

    public static int text(Context context) {
        return isDark(context) ? 0xFFF2F5F9 : 0xFF11161F;
    }

    public static int dim(Context context) {
        return isDark(context) ? 0xFF7C8AA0 : 0xFF6B7787;
    }

    public static int accent(Context context) {
        return 0xFF12E39A;
    }

    /** 强调色上的文字（选中的 chip）。 */
    public static int onAccent(Context context) {
        return 0xFF07110C;
    }

    public static int warning(Context context) {
        return 0xFFFF8A3D;
    }

    public static int track(Context context) {
        if (isDark(context)) {
            return isAmoled(context) ? 0xFF17171A : 0xFF1A2230;
        }
        return 0xFFE1E7F0;
    }

    public static int grid(Context context) {
        if (isDark(context)) {
            return isAmoled(context) ? 0xFF17171A : 0xFF1A2230;
        }
        return 0xFFE6EBF3;
    }

    public static int styleRes(Context context) {
        if (!isDark(context)) {
            return R.style.Theme_PowerGauge_Light;
        }
        return isAmoled(context) ? R.style.Theme_PowerGauge_Amoled : R.style.Theme_PowerGauge_Dark;
    }
}
