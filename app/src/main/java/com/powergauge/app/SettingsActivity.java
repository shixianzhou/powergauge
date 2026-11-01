package com.powergauge.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：刷新间隔、通知权限、电池优化白名单。
 * 和主页一样纯代码搭界面，不依赖 AppCompat / Material / PreferenceFragment。
 */
public class SettingsActivity extends Activity {

    private int BG;
    private int CARD;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int appliedTheme;

    private final int[] choices = {1000, 3000, 5000};
    private final TextView[] chops = new TextView[3];
    private final TextView[] themeChips = new TextView[4];

    private TextView notifStatus;
    private TextView batteryStatus;
    private TextView overlayStatus;
    private Switch overlaySwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedTheme = Theming.styleRes(this);
        setTheme(appliedTheme);
        BG = Theming.bg(this);
        CARD = Theming.card(this);
        TEXT = Theming.text(this);
        DIM = Theming.dim(this);
        ACCENT = Theming.accent(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        // 顶栏不能被状态栏盖住
        scroll.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(root);

        root.addView(buildTopBar(), rowParams(0));

        root.addView(sectionTitle("主题模式"), rowParams(dp(16)));
        root.addView(buildThemeCard(), rowParams(0));

        root.addView(sectionTitle("刷新间隔"), rowParams(dp(16)));
        root.addView(buildRefreshCard(), rowParams(0));

        root.addView(sectionTitle("悬浮窗"), rowParams(dp(16)));
        root.addView(buildOverlayCard(), rowParams(0));

        root.addView(sectionTitle("常驻通知"), rowParams(dp(16)));
        root.addView(buildNotificationCard(), rowParams(0));

        root.addView(sectionTitle("后台保活"), rowParams(dp(16)));
        root.addView(buildBatteryCard(), rowParams(0));

        TextView footer = new TextView(this);
        footer.setText("本工具只读取系统 battery 服务上报的电压、电流与温度，不联网、不写外部存储。"
                + "读数偏小或为 0 时，回主页切换电流单位；OPPO/一加/realme 部分机型不开放电压，属系统限制。");
        footer.setTextColor(DIM);
        footer.setTextSize(11);
        footer.setLineSpacing(dp(3), 1f);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer);

        setContentView(scroll);
        applySystemBars();
        refreshStates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Theming.styleRes(this) != appliedTheme) {
            recreate();
            return;
        }
        refreshStates();
    }

    private void applySystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Theming.isDark(this)) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }


    // ---------------------------------------------------------------- 顶栏

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(TEXT);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(back);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(title);
        return row;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(DIM);
        view.setTextSize(11);
        return view;
    }

    // ---------------------------------------------------------------- 主题

    private View buildThemeCard() {
        LinearLayout box = card();

        TextView hint = new TextView(this);
        hint.setText("深色 / 浅色 / 跟随系统 / AMOLED 黑。AMOLED 用纯黑底，OLED 屏上最省电。");
        hint.setTextColor(DIM);
        hint.setTextSize(11);
        hint.setLineSpacing(dp(3), 1f);
        box.addView(hint);

        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineLp.topMargin = dp(row == 0 ? 12 : 8);
            for (int col = 0; col < 2; col++) {
                final int mode = row * 2 + col;
                TextView chip = new TextView(this);
                chip.setText(Theming.MODE_NAMES[mode]);
                chip.setTextSize(13);
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1,
                        android.util.TypedValue.COMPLEX_UNIT_SP);
                chip.setPadding(0, dp(10), 0, dp(10));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.get(SettingsActivity.this).setThemeMode(mode);
                        recreate();
                    }
                });
                themeChips[mode] = chip;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) {
                    lp.leftMargin = dp(8);
                }
                line.addView(chip, lp);
            }
            box.addView(line, lineLp);
        }
        return box;
    }

    // ---------------------------------------------------------------- 刷新间隔

    private View buildRefreshCard() {
        LinearLayout box = card();

        TextView hint = new TextView(this);
        hint.setText("界面数字、功率曲线采样和通知栏的刷新频率。间隔越短越实时，也越费电。");
        hint.setTextColor(DIM);
        hint.setTextSize(11);
        hint.setLineSpacing(dp(3), 1f);
        box.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(12);

        for (int i = 0; i < choices.length; i++) {
            final int ms = choices[i];
            TextView chop = new TextView(this);
            chop.setText((ms / 1000) + " 秒");
            chop.setTextSize(13);
            chop.setGravity(Gravity.CENTER);
            chop.setPadding(0, dp(10), 0, dp(10));
            chop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.get(SettingsActivity.this).setRefreshMs(ms);
                    refreshStates();
                    Toast.makeText(SettingsActivity.this,
                            "刷新间隔：" + (ms / 1000) + " 秒", Toast.LENGTH_SHORT).show();
                }
            });
            chops[i] = chop;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(8);
            }
            row.addView(chop, lp);
        }
        box.addView(row, rowLp);
        return box;
    }

    // ---------------------------------------------------------------- 悬浮窗

    private View buildOverlayCard() {
        LinearLayout box = card();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        overlayStatus = new TextView(this);
        overlayStatus.setTextSize(12);
        overlayStatus.setLineSpacing(dp(3), 1f);
        top.addView(overlayStatus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        overlaySwitch = new Switch(this);
        overlaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                Prefs.get(SettingsActivity.this).setOverlayEnabled(checked);
                if (checked && !OverlayWindow.canShow(SettingsActivity.this)) {
                    openOverlayPermission();
                }
                syncService();
                refreshStates();
            }
        });
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchLp.leftMargin = dp(10);
        top.addView(overlaySwitch, switchLp);

        box.addView(top);

        TextView button = actionButton("打开悬浮窗权限");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlayPermission();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.addView(button, lp);
        return box;
    }

    private void openOverlayPermission() {
        if (OverlayWindow.canShow(this)) {
            Toast.makeText(this, "已经有悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tryStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName())))) {
            Toast.makeText(this, "在系统页面里允许「显示在其他应用上层」", Toast.LENGTH_LONG).show();
        } else if (tryStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
            Toast.makeText(this, "找到「PowerGauge」，允许「显示在其他应用上层」", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "这台设备打不开悬浮窗权限页，请到「设置 → 应用 → 特殊权限」里开启",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 让前台服务立刻按新开关重刷一次。
     * 这里只能用 startService：服务本来就在跑的话，再发一次 startForegroundService
     * 会要求 5 秒内再调一次 startForeground，否则系统直接判 crash。
     */
    private void syncService() {
        Intent intent = new Intent(this, PowerService.class).setAction(PowerService.ACTION_REFRESH);
        try {
            startService(intent);
        } catch (IllegalStateException e) {
            try {
                startForegroundService(intent);
            } catch (RuntimeException ignored) {
                // 后台被限制时等前台服务自己刷新
            }
        } catch (RuntimeException ignored) {
            // 后台被限制时等前台服务自己刷新
        }
    }

    // ---------------------------------------------------------------- 通知

    private View buildNotificationCard() {
        LinearLayout box = card();

        notifStatus = new TextView(this);
        notifStatus.setTextSize(12);
        notifStatus.setLineSpacing(dp(3), 1f);
        box.addView(notifStatus);

        TextView button = actionButton("打开通知设置");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationSettings();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.addView(button, lp);
        return box;
    }

    // ---------------------------------------------------------------- 电池

    private View buildBatteryCard() {
        LinearLayout box = card();

        batteryStatus = new TextView(this);
        batteryStatus.setTextSize(12);
        batteryStatus.setLineSpacing(dp(3), 1f);
        box.addView(batteryStatus);

        TextView button = actionButton("去设置");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.addView(button, lp);
        return box;
    }

    // ---------------------------------------------------------------- 状态

    private void refreshStates() {
        Prefs prefs = Prefs.get(this);
        for (int i = 0; i < chops.length; i++) {
            boolean active = choices[i] == prefs.refreshMs();
            chips(chops[i], active);
        }

        for (int i = 0; i < themeChips.length; i++) {
            if (themeChips[i] != null) {
                chips(themeChips[i], i == prefs.themeMode());
            }
        }

        if (overlaySwitch != null) {
            boolean on = prefs.overlayEnabled();
            boolean granted = OverlayWindow.canShow(this);
            overlaySwitch.setOnCheckedChangeListener(null);
            overlaySwitch.setChecked(on);
            overlaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                    Prefs.get(SettingsActivity.this).setOverlayEnabled(checked);
                    if (checked && !OverlayWindow.canShow(SettingsActivity.this)) {
                        openOverlayPermission();
                    }
                    syncService();
                    refreshStates();
                }
            });
            if (on && granted) {
                overlayStatus.setText("已开启：屏幕上方常驻一块小窗，显示功率 / 电压 / 电流 / 温度，可拖动。");
                overlayStatus.setTextColor(TEXT);
            } else if (on) {
                overlayStatus.setText("已开启但没授权：需要允许「显示在其他应用上层」才会显示。");
                overlayStatus.setTextColor(0xFFFF8A3D);
            } else {
                overlayStatus.setText("未开启：打开右侧开关后，会在屏幕上方显示一块透明小窗。");
                overlayStatus.setTextColor(DIM);
            }
        }

        boolean notif = notificationsEnabled();
        notifStatus.setText(notif
                ? "已开启：锁屏/后台时会在通知栏常驻显示实时功率。"
                : "未开启：前台服务仍在跑，但通知栏看不到读数，部分系统还可能限制后台。");
        notifStatus.setTextColor(notif ? TEXT : 0xFFFF8A3D);

        boolean ignore = ignoringBatteryOptimizations();
        batteryStatus.setText(ignore
                ? "已加入电池优化白名单，锁屏后不容易被系统回收。"
                : "未加入白名单：vivo / 小米等系统可能在锁屏几分钟后杀掉前台服务。"
                + "点下面的按钮，在系统页面里选「允许」/「不限制」。");
        batteryStatus.setTextColor(ignore ? TEXT : 0xFFFF8A3D);
    }

    private boolean notificationsEnabled() {
        if (Build.VERSION.SDK_INT < 24) {
            return true;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    private boolean ignoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "打不开系统设置，请手动到「设置 → 通知」里开启", Toast.LENGTH_LONG).show();
        }
    }

    private void openBatterySettings() {
        String pkg = getPackageName();
        // 1) 还没进白名单：直接弹系统的「允许后台运行」请求
        if (Build.VERSION.SDK_INT >= 23 && !ignoringBatteryOptimizations()) {
            if (tryStart(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + pkg)))) {
                return;
            }
        }
        // 2) 退到「电池优化」列表页（设置里的电池界面），并提示要点哪里
        if (Build.VERSION.SDK_INT >= 23
                && tryStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            Toast.makeText(this, "在列表里找到「PowerGauge」，选「不限制」/「允许后台运行」",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // 3) 再退应用详情
        if (tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + pkg)))) {
            return;
        }
        // 4) 最后一档：系统设置首页
        if (tryStart(new Intent(Settings.ACTION_SETTINGS))) {
            Toast.makeText(this, "请到「电池 → 后台耗电管理」里允许本应用后台运行",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "这台设备打不开系统设置，请手动到「设置 → 电池」里放行",
                Toast.LENGTH_LONG).show();
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 样式

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(roundRect(CARD, 16f));
        return box;
    }

    private void chips(TextView view, boolean active) {
        view.setTextColor(active ? Theming.onAccent(this)
                : (Theming.isDark(this) ? 0xFFB9C5D6 : 0xFF3E4A59));
        view.setTypeface(Typeface.create("sans-serif-medium",
                active ? Typeface.BOLD : Typeface.NORMAL));
        view.setBackground(roundRect(active ? ACCENT : Theming.soft(this), 12f));
    }

    private TextView actionButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theming.soft(this));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), ACCENT);
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams rowParams(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
