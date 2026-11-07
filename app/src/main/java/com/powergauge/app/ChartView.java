package com.powergauge.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 近 3 分钟的功率曲线：每秒一个采样点，环形缓冲。
 * 中间是 0W 基准线：充电为正，画在基准线上方；放电为负，画在基准线下方。
 */
public class ChartView extends View {

    private static final int CAPACITY = 180;

    private final float[] samples = new float[CAPACITY];
    private int head;
    private int count;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public ChartView(Context context) {
        this(context, null);
    }

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        zeroPaint.setStyle(Paint.Style.STROKE);
        zeroPaint.setStrokeWidth(1.5f);

        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void push(double watts) {
        samples[head] = (float) watts;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
        invalidate();
    }

    public void clear() {
        count = 0;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int accent = Theming.accent(getContext());
        int warning = Theming.warning(getContext());
        gridPaint.setColor(Theming.grid(getContext()));
        hintPaint.setColor(Theming.dim(getContext()));
        zeroPaint.setColor(Theming.dim(getContext()));
        zeroPaint.setAlpha(0x88);

        float pad = Math.max(6f, h * 0.08f);
        float top = pad;
        float bottom = h - pad;
        float usable = bottom - top;
        float centerY = top + usable / 2f;
        float half = usable / 2f;

        // 上下两条边 + 25%/75% 辅助线；正中间的 0W 线单独用更亮的颜色。
        canvas.drawLine(pad, top, w - pad, top, gridPaint);
        canvas.drawLine(pad, top + usable * 0.25f, w - pad, top + usable * 0.25f, gridPaint);
        canvas.drawLine(pad, top + usable * 0.75f, w - pad, top + usable * 0.75f, gridPaint);
        canvas.drawLine(pad, bottom, w - pad, bottom, gridPaint);
        canvas.drawLine(pad, centerY, w - pad, centerY, zeroPaint);

        hintPaint.setTextSize(getResources().getDisplayMetrics().density * 11f);
        if (count < 2) {
            if (count < 8) {
                canvas.drawText("正在采集数据…", w / 2f, centerY, hintPaint);
            }
            return;
        }

        // 以 0W 为中心做对称缩放：正功率向上，负功率向下。
        float max = 1f;
        for (int i = 0; i < count; i++) {
            float v = Math.abs(samples[(head - count + i + CAPACITY) % CAPACITY]);
            if (v > max) {
                max = v;
            }
        }
        max *= 1.15f;

        float stepX = (w - pad * 2f) / (CAPACITY - 1);
        float offsetX = w - pad - stepX * (count - 1);

        linePath.reset();
        fillPath.reset();
        float lastX = 0;
        float lastY = centerY;
        for (int i = 0; i < count; i++) {
            float v = samples[(head - count + i + CAPACITY) % CAPACITY];
            float x = offsetX + stepX * i;
            float y = centerY - (v / max) * half;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, centerY);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        fillPath.lineTo(lastX, centerY);
        fillPath.close();

        // 用裁剪把正负半区分开上色：0W 以上绿色，以下橙色，切换点正好在基准线上。
        fillPaint.setShader(new LinearGradient(0, top, 0, centerY,
                new int[]{(accent & 0x00FFFFFF) | 0x11000000,
                        (accent & 0x00FFFFFF) | 0x55000000},
                null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(0, 0, w, centerY);
        canvas.drawPath(fillPath, fillPaint);
        canvas.restore();

        fillPaint.setShader(new LinearGradient(0, centerY, 0, bottom,
                new int[]{(warning & 0x00FFFFFF) | 0x55000000,
                        (warning & 0x00FFFFFF) | 0x11000000},
                null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(0, centerY, w, h);
        canvas.drawPath(fillPath, fillPaint);
        canvas.restore();

        // 填充是半透明的，但这里再画一次基准线，保证它始终清楚。
        canvas.drawLine(pad, centerY, w - pad, centerY, zeroPaint);

        linePaint.setColor(accent);
        canvas.save();
        canvas.clipRect(0, 0, w, centerY);
        canvas.drawPath(linePath, linePaint);
        canvas.restore();

        linePaint.setColor(warning);
        canvas.save();
        canvas.clipRect(0, centerY, w, h);
        canvas.drawPath(linePath, linePaint);
        canvas.restore();

        if (count > 0) {
            linePaint.setColor(lastY <= centerY ? accent : warning);
            canvas.drawCircle(lastX, lastY, 4f, linePaint);
        }
        if (count < 8) {
            // 刚打开时只有几个点、贴在右边像 bug，先给一句提示
            canvas.drawText("正在采集数据…", w / 2f, centerY, hintPaint);
        }
    }
}
