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
 * 画的是功率绝对值（充电/放电分开由颜色体现），带渐变填充。
 */
public class ChartView extends View {

    private static final int CAPACITY = 180;

    private final float[] samples = new float[CAPACITY];
    private int head;
    private int count;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        linePaint.setColor(accent);
        gridPaint.setColor(Theming.grid(getContext()));
        hintPaint.setColor(Theming.dim(getContext()));

        float pad = Math.max(6f, h * 0.08f);
        float top = pad;
        float bottom = h - pad;
        float usable = bottom - top;

        canvas.drawLine(pad, top, w - pad, top, gridPaint);
        canvas.drawLine(pad, top + usable / 2f, w - pad, top + usable / 2f, gridPaint);
        canvas.drawLine(pad, bottom, w - pad, bottom, gridPaint);

        if (count < 8) {
            // 刚打开时只有几个点、贴在右边像 bug，先给一句提示
            hintPaint.setTextSize(getResources().getDisplayMetrics().density * 11f);
            canvas.drawText("正在采集数据…", w / 2f, top + usable / 2f, hintPaint);
        }
        if (count < 2) {
            return;
        }

        float max = 5f;
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
        float lastY = bottom;
        for (int i = 0; i < count; i++) {
            float v = Math.abs(samples[(head - count + i + CAPACITY) % CAPACITY]);
            float x = offsetX + stepX * i;
            float y = bottom - Math.min(1f, v / max) * usable;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, bottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        fillPath.lineTo(lastX, bottom);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{(accent & 0x00FFFFFF) | 0x55000000,
                        (accent & 0x00FFFFFF)}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        if (count > 0) {
            Paint dot = linePaint;
            canvas.drawCircle(lastX, lastY, 4f, dot);
        }
    }
}
