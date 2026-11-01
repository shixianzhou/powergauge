package com.powergauge.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 悬浮窗：竖排四行「文字 + 数值(单位)」，透黑圆角底，可拖动。
 * 由前台服务持有，界面退到后台后仍然更新。
 */
final class OverlayWindow {

    private static final int LABEL_COLOR = 0xFFA8B3C2;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0xCC000000; // 80% 黑，透出一点底色

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;

    private LinearLayout root;
    private TextView powerValue;
    private TextView voltValue;
    private TextView ampValue;
    private TextView tempValue;
    private boolean shown;

    OverlayWindow(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(14);
        params.y = dp(120);
    }

    static boolean canShow(Context context) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context);
    }

    void show() {
        if (shown || windowManager == null) {
            return;
        }
        build();
        try {
            windowManager.addView(root, params);
            shown = true;
            // 默认贴右边：宽度要等布局出来才知道，所以 post 一次再定位
            root.post(new Runnable() {
                @Override
                public void run() {
                    if (!shown || root == null) {
                        return;
                    }
                    int screenW = context.getResources().getDisplayMetrics().widthPixels;
                    params.x = Math.max(dp(8), screenW - root.getWidth() - dp(14));
                    params.y = dp(120);
                    try {
                        windowManager.updateViewLayout(root, params);
                    } catch (RuntimeException ignored) {
                        // 窗口已经没了
                    }
                }
            });
        } catch (RuntimeException ignored) {
            // 权限被系统收回等情况，交给下一次刷新重试
        }
    }

    void hide() {
        if (!shown || root == null) {
            return;
        }
        try {
            windowManager.removeView(root);
        } catch (RuntimeException ignored) {
            // 已经被移除
        }
        shown = false;
    }

    void update(Sampler.Reading reading) {
        if (!shown) {
            return;
        }
        boolean valid = reading != null && reading.valid;
        powerValue.setText(valid ? Fmt.watts(reading.watts) + " W" : "--");
        voltValue.setText(valid ? Fmt.volts(reading.volts) + " V" : "--");
        ampValue.setText(valid ? Fmt.amps(Math.abs(reading.amps)) + " A" : "--");
        tempValue.setText(valid ? Fmt.temp(reading.celsius) + " ℃" : "--");
    }

    private void build() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(9), dp(14), dp(9));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG_COLOR);
        bg.setCornerRadius(dp(14));
        root.setBackground(bg);

        powerValue = new TextView(context);
        voltValue = new TextView(context);
        ampValue = new TextView(context);
        tempValue = new TextView(context);
        root.addView(row("功率", powerValue));
        root.addView(row("电压", voltValue));
        root.addView(row("电流", ampValue));
        root.addView(row("温度", tempValue));

        root.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (event.getRawX() - downX);
                        params.y = startY + (int) (event.getRawY() - downY);
                        try {
                            windowManager.updateViewLayout(root, params);
                        } catch (RuntimeException ignored) {
                            // 窗口已经没了
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private View row(String label, TextView value) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(2), 0, dp(2));

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(LABEL_COLOR);
        name.setTextSize(11);
        name.setWidth(dp(34));

        value.setTextColor(VALUE_COLOR);
        value.setTextSize(13);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setPadding(dp(8), 0, 0, 0);
        value.setMinWidth(dp(74));

        line.addView(name);
        line.addView(value);
        return line;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
