package com.powergauge.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 单页主界面：仪表盘 + 四项读数 + 功率曲线 + 底部设置。
 * 界面全部用代码搭，不依赖 AppCompat / Material。
 */
public class MainActivity extends Activity implements Bus.Listener {

    // 配色随主题变化，在 onCreate 里由 Theming 填充
    private int BG;
    private int CARD;
    private int TEXT;
    private int DIM;
    private int appliedTheme;

    private Prefs prefs;
    private GaugeView gauge;
    private ChartView chart;
    private TextView statVolt;
    private TextView statAmp;
    private TextView statLevel;
    private TextView statTemp;
    private TextView chipUnit;
    private TextView chipCells;
    private TextView chipComp;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            Sampler.Reading r = Bus.last();
            if (r != null && r.valid) {
                chart.push(r.watts);
            }
            handler.postDelayed(this, prefs.refreshMs());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        appliedTheme = Theming.styleRes(this);
        setTheme(appliedTheme);
        BG = Theming.bg(this);
        CARD = Theming.card(this);
        TEXT = Theming.text(this);
        DIM = Theming.dim(this);
        setContentView(buildUi());
        applySystemBars();
        askNotificationPermission();
    }

    /** 状态栏 / 导航栏跟随主题，浅色底下用深色图标。 */
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

    @Override
    protected void onStart() {
        super.onStart();
        Bus.subscribe(this);
        // 每次回到界面都确保前台服务在跑：用户可能从通知栏点过「停止」
        startSampling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置页可能改过主题：换了就把界面整个重建一遍
        if (Theming.styleRes(this) != appliedTheme) {
            recreate();
            return;
        }
        // 设置页可能改过刷新间隔，回到界面时按新间隔重启计时器
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Bus.unsubscribe(this);
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onReading(Sampler.Reading reading) {
        if (reading == null) {
            return;
        }
        gauge.bind(reading);
        if (reading.valid) {
            statVolt.setText(Fmt.volts(reading.volts));
            statAmp.setText(Fmt.amps(Math.abs(reading.amps)));
            statLevel.setText(Fmt.percent(reading.percent));
            statTemp.setText(Fmt.temp(reading.celsius));
        } else {
            statVolt.setText("--");
            statAmp.setText("--");
            statLevel.setText("--");
            statTemp.setText("--");
        }
    }

    // ---------------------------------------------------------------- 界面

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(18), dp(20), dp(14));
        root.setFitsSystemWindows(true);

        root.addView(buildHeader(), rowParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, 0));

        gauge = new GaugeView(this);
        root.addView(gauge, rowParams(ViewGroup.LayoutParams.MATCH_PARENT, 0,
                1f, 0, dp(8), 0, dp(8)));

        root.addView(buildStats(), rowParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, dp(14)));

        root.addView(buildChartCard(), rowParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, dp(14)));

        root.addView(buildChips(), rowParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, 0));

        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("PowerGauge");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView sub = new TextView(this);
        sub.setText("v1.5.0");
        sub.setTextColor(DIM);
        sub.setTextSize(11);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.leftMargin = dp(8);

        row.addView(title);
        row.addView(sub, subLp);

        // 右侧留白撑开，齿轮贴右上角
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        IconView gear = new IconView(this, IconView.MODE_GEAR);
        gear.setIconColor(DIM);
        gear.setContentDescription("设置");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        row.addView(gear, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    private View buildStats() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        statVolt = new TextView(this);
        statAmp = new TextView(this);
        statLevel = new TextView(this);
        statTemp = new TextView(this);

        row.addView(statCell(statVolt, getString(R.string.label_voltage), "V"), weightParams(0));
        row.addView(statCell(statAmp, getString(R.string.label_current), "A"), weightParams(1));
        row.addView(statCell(statLevel, getString(R.string.label_level), "%"), weightParams(2));
        row.addView(statCell(statTemp, getString(R.string.label_temp), "°C"), weightParams(3));
        return row;
    }

    private View statCell(TextView value, String label, String unit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(12), dp(6), dp(12));
        box.setBackground(card(16f, CARD));

        value.setText("--");
        value.setTextColor(TEXT);
        value.setGravity(Gravity.CENTER);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // 「154.87」这种长数字不能折行，用自动缩小字号兜住
        value.setMaxLines(1);
        value.setAutoSizeTextTypeUniformWithConfiguration(10, 18, 1, TypedValue.COMPLEX_UNIT_SP);

        TextView caption = new TextView(this);
        caption.setText(label + " · " + unit);
        caption.setTextColor(DIM);
        caption.setTextSize(10);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);

        box.addView(value);
        box.addView(caption, lp);
        return box;
    }

    private View buildChartCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(10));
        box.setBackground(card(16f, CARD));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.label_chart));
        title.setTextColor(DIM);
        title.setTextSize(11);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = new TextView(this);
        reset.setText("清除");
        reset.setTextSize(11);
        reset.setTextColor(DIM);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(12), dp(5), dp(12), dp(5));
        reset.setBackground(card(11f, Theming.soft(this)));
        reset.setContentDescription("清除曲线");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearChart();
            }
        });
        header.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(header);

        chart = new ChartView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        lp.topMargin = dp(6);
        box.addView(chart, lp);
        return box;
    }

    /** 清空曲线并从头开始统计。 */
    private void clearChart() {
        chart.clear();
        Toast.makeText(this, "已清除，重新统计", Toast.LENGTH_SHORT).show();
    }

    private View buildChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        chipUnit = chip("");
        chipCells = chip("");
        chipComp = chip("");

        TextView help = chip(getString(R.string.chip_help));
        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp();
            }
        });

        chipUnit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUnitDialog();
            }
        });
        chipCells.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCellsDialog();
            }
        });
        chipComp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCompDialog();
            }
        });

        row.addView(chipUnit, weightParams(0));
        row.addView(chipCells, weightParams(1));
        row.addView(chipComp, weightParams(2));
        row.addView(help, weightParams(3));

        refreshChips();
        return row;
    }

    private TextView chip(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFB9C5D6);
        view.setGravity(Gravity.CENTER);
        // 一行放下：窄屏/大字号机型（如 1260px 宽 + 系统放大字体）不会把「补偿 ×1.00」折成两行
        view.setMaxLines(1);
        view.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        view.setPadding(dp(3), dp(9), dp(3), dp(9));
        view.setBackground(card(12f, CARD));
        return view;
    }

    private void refreshChips() {
        chipUnit.setText(getString(R.string.chip_unit) + " "
                + (prefs.unit() == Prefs.UNIT_MA ? "mA" : "µA"));
        chipCells.setText(getString(R.string.chip_cells) + " ×" + prefs.cells());
        chipComp.setText(getString(R.string.chip_comp) + " ×"
                + String.format(Locale.US, "%.2f", prefs.compFactor()));
    }

    // ---------------------------------------------------------------- 弹窗

    private void showUnitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dlg_unit_title))
                .setSingleChoiceItems(new String[]{
                        getString(R.string.unit_ua), getString(R.string.unit_ma)}, prefs.unit(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.setUnit(which);
                                refreshChips();
                                refreshNow();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCellsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dlg_cells_title))
                .setSingleChoiceItems(
                        new String[]{"单电芯", "双电芯", "三电芯"}, prefs.cells() - 1,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.setCells(which + 1);
                                refreshChips();
                                refreshNow();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCompDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", prefs.compFactor()));
        input.setSelection(input.getText().length());
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dlg_comp_title))
                .setMessage(getString(R.string.dlg_comp_msg))
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            float v = Float.parseFloat(input.getText().toString().trim());
                            prefs.setCompFactor(v);
                        } catch (NumberFormatException ignored) {
                            // 输入不合法就保持原值
                        }
                        refreshChips();
                        refreshNow();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dlg_help_title))
                .setMessage(getString(R.string.help_text))
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------------------------------------------------------- 采样

    private void startSampling() {
        Intent intent = new Intent(this, PowerService.class);
        try {
            startForegroundService(intent);
        } catch (IllegalStateException e) {
            startService(intent);
        }
        refreshNow();
    }

    /**
     * 请前台服务立刻重读一次电池广播。
     * 界面不再自己算读数：通知栏和界面必须来自同一份数据，否则放电时会差一截。
     */
    private void refreshNow() {
        Intent intent = new Intent(this, PowerService.class);
        intent.setAction(PowerService.ACTION_REFRESH);
        try {
            startService(intent);
        } catch (RuntimeException ignored) {
            // 被后台限制时忽略，等下一次电池广播即可
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    // ---------------------------------------------------------------- 工具

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable card(float radiusDp, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams weightParams(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int gap = dp(5);
        lp.leftMargin = index == 0 ? 0 : gap;
        lp.rightMargin = index == 3 ? 0 : gap;
        return lp;
    }

    private LinearLayout.LayoutParams rowParams(int width, int height, float weight,
                                                int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height, weight);
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.rightMargin = right;
        lp.bottomMargin = bottom;
        return lp;
    }
}
