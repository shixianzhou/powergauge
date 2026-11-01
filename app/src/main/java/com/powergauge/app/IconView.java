package com.powergauge.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 22dp 的小图标按钮：齿轮（设置）与重置箭头（清除曲线）。
 * 全部用 Canvas 画，省掉 import 矢量图和额外资源。
 */
public class IconView extends View {

    public static final int MODE_GEAR = 0;
    public static final int MODE_RESET = 1;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();

    private int mode;
    private int color = 0xFF8A97AB;

    public IconView(Context context, int mode) {
        super(context);
        this.mode = mode;
        setClickable(true);
        setFocusable(true);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    public void setIconColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(22 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f * 0.86f;

        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(Math.max(2f, r * 0.18f));
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);

        if (mode == MODE_GEAR) {
            drawGear(canvas, cx, cy, r);
        } else {
            drawReset(canvas, cx, cy, r);
        }
    }

    private void drawGear(Canvas canvas, float cx, float cy, float r) {
        // 齿：8 根短粗放射线
        stroke.setStrokeWidth(Math.max(2.5f, r * 0.30f));
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f, cx, cy);
            canvas.drawLine(cx, cy - r * 0.66f, cx, cy - r * 0.97f, stroke);
            canvas.restore();
        }
        // 轮体：粗描边圆环，中间自然留出轴孔
        stroke.setStrokeWidth(Math.max(3f, r * 0.34f));
        canvas.drawCircle(cx, cy, r * 0.56f, stroke);
    }

    private void drawReset(Canvas canvas, float cx, float cy, float r) {
        float rr = r * 0.78f;
        arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
        canvas.drawArc(arc, 305f, 250f, false, stroke);

        // 弧的起点（305°）处放一个箭头，指向切线方向
        double a = Math.toRadians(305);
        float x = cx + rr * (float) Math.cos(a);
        float y = cy + rr * (float) Math.sin(a);
        float d = r * 0.42f;
        path.reset();
        path.moveTo(x + d * 0.55f, y - d * 0.75f);
        path.lineTo(x + d * 0.75f, y + d * 0.20f);
        path.lineTo(x - d * 0.45f, y + d * 0.05f);
        path.close();
        canvas.drawPath(path, fill);
    }
}
