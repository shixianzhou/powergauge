package com.powergauge.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 电池信息页：当前容量、设计容量、健康度、损耗、实时读数和充放电统计。
 *
 * 系统没有公开「设计容量 / 健康度」接口，所以：
 *  - 设计容量让用户按机型参数手动填；
 *  - 满电容量用电流积分（库仑计数）估算，再和设计容量比较得到健康度。
 */
public class BatteryInfoActivity extends Activity implements Bus.Listener {

    private int BG;
    private int CARD;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int SOFT;
    private int WARN;
    private int appliedTheme;

    private Prefs prefs;
    private BatteryStats stats;

    private TextView heroMah;
    private TextView heroStatus;
    private TextView tvDesign;
    private TextView tvFull;
    private TextView tvHealth;
    private TextView tvWear;
    private TextView tvCycles;
    private TextView tvHealthState;
    private TextView tvVolt;
    private TextView tvAmp;
    private TextView tvWatt;
    private TextView tvTemp;
    private TextView tvLevel;
    private TextView tvTech;
    private TextView tvDuration;
    private TextView tvCharged;
    private TextView tvDischarged;
    private TextView tvNet;
    private TextView tvLevels;
    private TextView tvSegFull;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        stats = BatteryStats.get(this);

        appliedTheme = Theming.styleRes(this);
        setTheme(appliedTheme);
        BG = Theming.bg(this);
        CARD = Theming.card(this);
        TEXT = Theming.text(this);
        DIM = Theming.dim(this);
        ACCENT = Theming.accent(this);
        SOFT = Theming.soft(this);
        WARN = Theming.warning(this);

        setContentView(buildUi());
        applySystemBars();
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bus.subscribe(this);
        refreshNow();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Theming.styleRes(this) != appliedTheme) {
            recreate();
            return;
        }
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Bus.unsubscribe(this);
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onReading(Sampler.Reading reading) {
        refresh();
    }

    // ---------------------------------------------------------------- 界面

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(root);

        root.addView(buildTopBar(), rowParams(0));
        root.addView(buildHeroCard(), rowParams(dp(14)));

        root.addView(sectionTitle("容量与健康"), rowParams(dp(18)));
        root.addView(buildCapacityCard(), rowParams(0));

        root.addView(sectionTitle("实时读数"), rowParams(dp(18)));
        root.addView(buildLiveCard(), rowParams(0));

        root.addView(sectionTitle("充放电统计"), rowParams(dp(18)));
        root.addView(buildStatsCard(), rowParams(0));

        TextView footer = new TextView(this);
        footer.setText("系统没有公开设计容量和健康度接口：设计容量需要按机型参数手动填写；"
                + "满电容量由电流对时间积分估算，充/放电不足 10% 时可能还没有结果。\n\n"
                + "优先使用系统 charge_counter；读不到（或为负数）时，当前容量按「满电容量 × 电量」估算，"
                + "并在前面标 ≈。");
        footer.setTextColor(DIM);
        footer.setTextSize(11);
        footer.setLineSpacing(dp(3), 1f);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer);

        return scroll;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(TEXT);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));

        TextView title = new TextView(this);
        title.setText("电池信息");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(6);
        row.addView(title, titleLp);
        return row;
    }

    private View buildHeroCard() {
        LinearLayout box = card();

        TextView caption = new TextView(this);
        caption.setText("当前容量");
        caption.setTextColor(DIM);
        caption.setTextSize(11);
        box.addView(caption);

        heroMah = new TextView(this);
        heroMah.setText("—");
        heroMah.setTextColor(ACCENT);
        heroMah.setTextSize(32);
        heroMah.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams mahLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mahLp.topMargin = dp(2);
        box.addView(heroMah, mahLp);

        heroStatus = new TextView(this);
        heroStatus.setText("等待电池数据…");
        heroStatus.setTextColor(DIM);
        heroStatus.setTextSize(12);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(4);
        box.addView(heroStatus, statusLp);
        return box;
    }

    private View buildCapacityCard() {
        LinearLayout box = card();

        tvDesign = value("未设置 · 点击填写");
        tvDesign.setTextColor(ACCENT);
        LinearLayout designRow = row("设计容量", tvDesign);
        designRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDesignDialog();
            }
        });
        tvDesign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDesignDialog();
            }
        });
        box.addView(designRow);

        tvFull = value("—");
        box.addView(row("估算满电容量", tvFull));

        tvHealth = value("—");
        box.addView(row("容量健康度", tvHealth));

        tvWear = value("—");
        box.addView(row("电池损耗", tvWear));

        tvCycles = value("—");
        box.addView(row("循环次数", tvCycles));

        tvHealthState = value("—");
        box.addView(row("电池健康状态", tvHealthState));

        TextView hint = new TextView(this);
        hint.setText("健康度 = 估算满电容量 ÷ 设计容量；损耗 = 设计容量 − 满电容量。"
                + "设计容量可在手机参数页或官网查到。");
        hint.setTextColor(DIM);
        hint.setTextSize(10);
        hint.setLineSpacing(dp(2), 1f);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        return box;
    }

    private View buildLiveCard() {
        LinearLayout box = card();
        tvVolt = value("—");
        tvAmp = value("—");
        tvWatt = value("—");
        tvTemp = value("—");
        tvLevel = value("—");
        tvTech = value("—");
        box.addView(row("电压", tvVolt));
        box.addView(row("电流（充电为正 / 放电为负）", tvAmp));
        box.addView(row("功率", tvWatt));
        box.addView(row("温度", tvTemp));
        box.addView(row("电量", tvLevel));
        box.addView(row("电池技术", tvTech));
        return box;
    }

    private View buildStatsCard() {
        LinearLayout box = card();
        tvDuration = value("—");
        tvCharged = value("—");
        tvDischarged = value("—");
        tvNet = value("—");
        tvLevels = value("—");
        tvSegFull = value("—");

        box.addView(row("统计时长", tvDuration));
        box.addView(row("累计充入", tvCharged));
        box.addView(row("累计放出", tvDischarged));
        box.addView(row("净充入", tvNet));
        box.addView(row("起止电量", tvLevels));
        box.addView(row("本次估算满电容量", tvSegFull));

        TextView note = new TextView(this);
        note.setText("统计从服务启动或上次重置开始；进程重启后如果电量已经变化，会重新开一个统计窗口。");
        note.setTextColor(DIM);
        note.setTextSize(10);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);

        TextView reset = actionButton("重置统计");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stats.reset();
                refresh();
                Toast.makeText(BatteryInfoActivity.this, "统计已重置", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.addView(reset, lp);
        return box;
    }

    // ---------------------------------------------------------------- 数据

    private void refresh() {
        Sampler.Reading r = Bus.last();
        double mah = stats.currentMah(r);
        boolean estimated = stats.currentMahEstimated(r);
        heroMah.setText(mah > 0
                ? (estimated ? "≈" : "") + Fmt.mah(mah)
                : "—");
        heroStatus.setText(statusLine(r));

        if (r != null) {
            tvVolt.setText(r.volts > 0 ? Fmt.volts(r.volts) + " V" : "—");
            tvAmp.setText(r.valid ? Fmt.amps(r.amps) + " A" : "—");
            tvWatt.setText(r.valid ? Fmt.watts(r.watts) + " W" : "—");
            tvTemp.setText(Double.isNaN(r.celsius) ? "—" : Fmt.temp(r.celsius) + " °C");
            tvLevel.setText(r.percent >= 0 ? r.percent + "%" : "—");
            tvTech.setText(r.technology == null || r.technology.isEmpty() ? "—" : r.technology);
            tvCycles.setText(r.cycleCount >= 0 ? String.valueOf(r.cycleCount) : "系统未提供");
            tvHealthState.setText(healthName(r.health));
        } else {
            tvVolt.setText("—");
            tvAmp.setText("—");
            tvWatt.setText("—");
            tvTemp.setText("—");
            tvLevel.setText("—");
            tvTech.setText("—");
            tvCycles.setText("系统未提供");
            tvHealthState.setText("—");
        }

        int design = prefs.designMah();
        tvDesign.setText(design > 0 ? design + " mAh" : "未设置 · 点击填写");

        double full = stats.fullMah();
        tvFull.setText(full > 0
                ? (stats.fullExact() ? "" : "≈") + Fmt.mah(full)
                : "—");

        double health = stats.healthPercent();
        if (Double.isNaN(health)) {
            tvHealth.setText("—");
            tvHealth.setTextColor(DIM);
        } else {
            tvHealth.setText(String.format(Locale.US, "%.1f%%", health));
            tvHealth.setTextColor(health < 80 ? WARN : TEXT);
        }

        double wear = stats.wearPercent();
        double wearMah = stats.wearMah();
        if (Double.isNaN(wear)) {
            tvWear.setText("—");
        } else {
            String text = String.format(Locale.US, "%.1f%%", wear);
            if (!Double.isNaN(wearMah) && wearMah > 0) {
                text += " · " + Fmt.mah(wearMah);
            }
            tvWear.setText(text);
        }

        long duration = stats.durationMs();
        tvDuration.setText(duration > 0 ? formatDuration(duration) : "—");
        tvCharged.setText(Fmt.mah(stats.chargedMah())
                + " · " + String.format(Locale.US, "%.2f Wh", stats.chargedWh()));
        tvDischarged.setText(Fmt.mah(stats.dischargedMah())
                + " · " + String.format(Locale.US, "%.2f Wh", stats.dischargedWh()));
        tvNet.setText(signedMah(stats.netMah())
                + " · " + String.format(Locale.US, "%+.2f Wh", stats.netWh()));
        tvLevels.setText(stats.startLevel() >= 0 && stats.lastLevel() >= 0
                ? stats.startLevel() + "% → " + stats.lastLevel() + "%"
                : "—");
        tvSegFull.setText(full > 0
                ? (stats.fullExact() ? "" : "≈") + Fmt.mah(full)
                : "—（继续充/放电 10% 后自动估算）");
    }

    private String statusLine(Sampler.Reading r) {
        if (r == null) {
            return "等待电池数据…";
        }
        if (!r.valid) {
            return r.hint == null ? "不支持该设备" : r.hint;
        }
        StringBuilder sb = new StringBuilder();
        if (r.full()) {
            sb.append("已充满");
        } else if (r.charging()) {
            sb.append("充电中");
        } else {
            sb.append("放电中");
        }
        if ((r.charging() || r.full()) && r.plugged != 0) {
            sb.append(" · ").append(GaugeView.plugName(r.plugged));
        }
        if (r.percent >= 0) {
            sb.append(" · 电量 ").append(r.percent).append("%");
        }
        return sb.toString();
    }

    private String signedMah(double mah) {
        return (mah >= 0 ? "+" : "") + Fmt.mah(mah);
    }

    private String healthName(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "已损坏 / 需更换";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "过压";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "故障";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "过冷";
            default:
                return "系统未提供";
        }
    }

    private String formatDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return h + " 小时 " + m + " 分";
        }
        if (m > 0) {
            return m + " 分 " + s + " 秒";
        }
        return s + " 秒";
    }

    // ---------------------------------------------------------------- 弹窗

    private void showDesignDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        int current = prefs.designMah();
        if (current > 0) {
            input.setText(String.valueOf(current));
            input.setSelection(input.getText().length());
        }
        input.setHint("例如 6000");
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("电池设计容量")
                .setMessage("填写电池标称容量（mAh），用来计算健康度和损耗。\n"
                        + "可在手机参数页或官网查到；例如 6000。留空表示不填。")
                .setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        int mah = 0;
                        if (!text.isEmpty()) {
                            try {
                                mah = Integer.parseInt(text);
                            } catch (NumberFormatException e) {
                                Toast.makeText(BatteryInfoActivity.this,
                                        "请输入数字，例如 6000", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        if (mah != 0 && (mah < 100 || mah > 30000)) {
                            Toast.makeText(BatteryInfoActivity.this,
                                    "容量应在 100–30000 mAh 之间", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        prefs.setDesignMah(mah);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        Theming.styleDialog(this, dialog);
        dialog.show();
    }

    // ---------------------------------------------------------------- 工具

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(roundRect(CARD, 16f));
        return box;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(DIM);
        view.setTextSize(11);
        return view;
    }

    private TextView value(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(13);
        view.setGravity(Gravity.END);
        view.setMaxLines(2);
        return view;
    }

    private LinearLayout row(String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(DIM);
        name.setTextSize(12);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView actionButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SOFT);
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

    private void refreshNow() {
        Intent intent = new Intent(this, PowerService.class);
        intent.setAction(PowerService.ACTION_REFRESH);
        try {
            startService(intent);
        } catch (RuntimeException ignored) {
            // 后台限制等情况下忽略，等下一次电池广播即可
        }
    }
}
