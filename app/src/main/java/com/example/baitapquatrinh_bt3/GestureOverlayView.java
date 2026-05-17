package com.example.baitapquatrinh_bt3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay trong suốt đặt chồng lên PreviewView.
 * Vẽ đường trail ngón tay với hiệu ứng gradient fade.
 */
public class GestureOverlayView extends View {

    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  trailPath  = new Path();

    private List<PointF> trail = new ArrayList<>();

    // Màu trail: tím → vàng (gradient theo thời gian)
    private static final int COLOR_OLD  = Color.argb(80,  180, 100, 255);
    private static final int COLOR_NEW  = Color.argb(255, 255, 220,  50);
    private static final int COLOR_DOT  = Color.argb(255, 255, 255, 255);

    public GestureOverlayView(Context context) {
        super(context);
        init();
    }

    public GestureOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(8f);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(COLOR_DOT);
    }

    /** Gọi từ AirGestureActivity mỗi khi có điểm mới */
    public void setTrail(List<PointF> points) {
        this.trail = points;
        invalidate(); // trigger onDraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trail == null || trail.size() < 2) return;

        int n = trail.size();

        // Vẽ từng đoạn với alpha tăng dần (cũ mờ → mới đậm)
        for (int i = 1; i < n; i++) {
            float progress = (float) i / n;          // 0 → 1
            int alpha = (int) (80 + 175 * progress); // 80 → 255

            // Interpolate màu tím → vàng
            int r = lerp(180, 255, progress);
            int g = lerp(100, 220, progress);
            int b = lerp(255,  50, progress);

            trailPaint.setColor(Color.argb(alpha, r, g, b));
            trailPaint.setStrokeWidth(4f + 6f * progress); // dày dần

            PointF p1 = trail.get(i - 1);
            PointF p2 = trail.get(i);
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint);
        }

        // Vẽ chấm trắng ở đầu ngón tay (điểm mới nhất)
        PointF tip = trail.get(n - 1);

        // Glow effect: vẽ 2 vòng tròn
        dotPaint.setColor(Color.argb(60, 255, 255, 255));
        canvas.drawCircle(tip.x, tip.y, 22f, dotPaint);

        dotPaint.setColor(Color.argb(130, 255, 255, 255));
        canvas.drawCircle(tip.x, tip.y, 14f, dotPaint);

        dotPaint.setColor(Color.WHITE);
        canvas.drawCircle(tip.x, tip.y, 7f, dotPaint);
    }

    private int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }
}
