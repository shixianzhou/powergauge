package com.powergauge.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * 主仪表盘：一圈 240° 的圆弧 + 中间的大号功率数字。
 * 充电为绿色，放电为橙色，弧长按 0~120W 线性映射。
 */
public class GaugeView extends View {

    private static final float MAX_WATTS = 120f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mahPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Sampler.Reading reading;
    private float shownWatts;

    public GaugeView(Context context) {
        this(context, null);
    }

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        valuePaint.setStyle(Paint.Style.STROKE);
        valuePaint.setStrokeCap(Paint.Cap.ROUND);
        bigPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        bigPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextAlign(Paint.Align.CENTER);
        peakPaint.setTextAlign(Paint.Align.CENTER);
        mahPaint.setTextAlign(Paint.Align.RIGHT);
        mahPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public void bind(Sampler.Reading r) {
        this.reading = r;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.54f;
        float radius = Math.min(w * 0.40f, h * 0.36f);
        float stroke = Math.max(12f, radius * 0.13f);

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // 主题色每次重画时取，切换深色/浅色/AMOLED 后立即生效
        trackPaint.setColor(Theming.track(getContext()));
        bigPaint.setColor(Theming.text(getContext()));
        unitPaint.setColor(Theming.dim(getContext()));
        subPaint.setColor(Theming.dim(getContext()));
        trackPaint.setStrokeWidth(stroke);
        valuePaint.setStrokeWidth(stroke);

        canvas.drawArc(rect, 150f, 240f, false, trackPaint);

        boolean valid = reading != null && reading.valid;
        double watts = valid ? reading.watts : 0;
        float target = (float) Math.min(1.0, Math.abs(watts) / MAX_WATTS);
        shownWatts += (target - shownWatts) * 0.25f;
        if (Math.abs(target - shownWatts) < 0.002f) {
            shownWatts = target;
        }

        valuePaint.setColor(valid && watts < 0
                ? Theming.warning(getContext()) : Theming.accent(getContext()));
        float sweep = 240f * shownWatts;
        // 小功率（电脑 USB 口 0.3~0.5W）按比例几乎画不出来，给一段最小可见弧
        if (valid && Math.abs(watts) > 0.005 && sweep < 2f) {
            sweep = 2f;
        }
        if (sweep > 0.01f) {
            canvas.drawArc(rect, 150f, sweep, false, valuePaint);
        }

        String big = valid ? Fmt.watts(watts) : "--";
        bigPaint.setTextSize(radius * 0.62f);
        // 629.38 这种位数会把数字撑出弧外，按可用宽度等比缩小
        float textW = bigPaint.measureText(big);
        float maxW = radius * 1.62f;
        if (textW > maxW) {
            bigPaint.setTextSize(bigPaint.getTextSize() * maxW / textW);
        }
        float baseY = cy + bigPaint.getTextSize() * 0.28f;
        canvas.drawText(big, cx, baseY, bigPaint);

        // 大数字上方的小字：本次运行期间的最低 / 最高功率（放电为负）
        if (valid && Bus.hasRange()) {
            String peak = Fmt.watts(Bus.minWatts()) + "/" + Fmt.watts(Bus.maxWatts());
            peakPaint.setColor(Theming.dim(getContext()));
            peakPaint.setTextSize(radius * 0.115f);
            float peakW = peakPaint.measureText(peak);
            float peakMax = radius * 1.30f;
            if (peakW > peakMax) {
                peakPaint.setTextSize(peakPaint.getTextSize() * peakMax / peakW);
            }
            canvas.drawText(peak, cx, cy - radius * 0.50f, peakPaint);
        }

        unitPaint.setTextSize(radius * 0.20f);
        canvas.drawText("W", cx, cy + radius * 0.55f, unitPaint);

        // 状态行居中，右端空位放剩余电量（mAh）。两者挤不下时先缩状态文字，实在不行就不画 mAh
        float statusY = cy + radius * 0.86f;
        float gap = radius * 0.08f;
        String status = statusLine(valid);
        String mah = mahText();
        float mahWidth = 0f;
        if (mah != null) {
            mahPaint.setColor(Theming.dim(getContext()));
            mahPaint.setTextSize(radius * 0.14f);
            mahWidth = mahPaint.measureText(mah);
        }

        subPaint.setTextSize(radius * 0.16f);
        float statusMax = w - 2f * (mahWidth + (mah == null ? 0f : gap));
        float statusWidth = subPaint.measureText(status);
        if (statusWidth > statusMax && statusMax > 0) {
            float scaled = subPaint.getTextSize() * statusMax / statusWidth;
            if (scaled >= radius * 0.10f) {
                subPaint.setTextSize(scaled);
            } else {
                mah = null;
            }
        }
        canvas.drawText(status, cx, statusY, subPaint);
        if (mah != null) {
            canvas.drawText(mah, w - 2f, statusY, mahPaint);
        }
    }

    /** 剩余电量文本；设备不报 CHARGE_COUNTER 时返回 null，界面保持原样。 */
    private String mahText() {
        if (reading == null || Double.isNaN(reading.chargeMah) || reading.chargeMah <= 0) {
            return null;
        }
        return Fmt.mah(reading.chargeMah);
    }

    private String statusLine(boolean valid) {
        if (reading == null) {
            return "等待数据…";
        }
        if (!valid) {
            return reading.hint == null ? "不支持该设备" : reading.hint;
        }
        if (reading.full()) {
            return "已充满 · " + plugName(reading.plugged);
        }
        if (reading.charging()) {
            return "充电中 · " + plugName(reading.plugged);
        }
        return "放电中 · 整机功耗";
    }

    static String plugName(int plugged) {
        switch (plugged) {
            case 1:
                return "有线";
            case 2:
                return "USB";
            case 4:
                return "无线";
            default:
                return "未插电";
        }
    }
}
