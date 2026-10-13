package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ChartView extends View {

    private static final int[] PALETTE = {
            Color.parseColor("#1565C0"), Color.parseColor("#2E7D32"),
            Color.parseColor("#C62828"), Color.parseColor("#6A1B9A"),
            Color.parseColor("#EF6C00")
    };

    private java.util.Map<String, List<HubEauClient.Observation>> series = new java.util.LinkedHashMap<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChartView(Context context) {
        this(context, null);
    }

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        fillPaint.setStyle(Paint.Style.FILL);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.LTGRAY);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(26f);
        legendPaint.setColor(Color.DKGRAY);
        legendPaint.setTextSize(24f);
    }

    public void setSeries(java.util.Map<String, List<HubEauClient.Observation>> s) {
        this.series = s != null ? s : Collections.emptyMap();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (series.isEmpty()) return;

        float padL = 110f, padR = 20f, padT = 20f, padB = 70f;
        float w = getWidth(), h = getHeight();
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;

        long tMin = Long.MAX_VALUE, tMax = Long.MIN_VALUE;
        float yMax = 0f;
        for (List<HubEauClient.Observation> obs : series.values()) {
            for (HubEauClient.Observation o : obs) {
                if (o.timestampMs < tMin) tMin = o.timestampMs;
                if (o.timestampMs > tMax) tMax = o.timestampMs;
                if (o.debit > yMax) yMax = o.debit;
            }
        }
        if (tMax <= tMin) tMax = tMin + 1;
        if (yMax <= 0) yMax = 1f;
        yMax *= 1.15f;

        for (int i = 0; i <= 4; i++) {
            float y = padT + chartH * i / 4f;
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
            String label = String.format(Locale.FRANCE, "%.0f", yMax * (4 - i) / 4f);
            canvas.drawText(label, padL - textPaint.measureText(label) - 8f, y + 8f, textPaint);
        }
        canvas.drawLine(padL, padT, padL, padT + chartH, axisPaint);
        canvas.drawLine(padL, padT + chartH, w - padR, padT + chartH, axisPaint);

        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        fmt.setTimeZone(TimeZone.getDefault());
        for (int i = 0; i <= 3; i++) {
            long t = tMin + (tMax - tMin) * i / 3;
            float x = padL + chartW * i / 3f;
            String label = fmt.format(new Date(t));
            canvas.save();
            canvas.rotate(-30f, x, padT + chartH + 10f);
            canvas.drawText(label, x, padT + chartH + 34f, textPaint);
            canvas.restore();
        }

        int colorIdx = 0;
        float legendX = padL;
        for (java.util.Map.Entry<String, List<HubEauClient.Observation>> e : series.entrySet()) {
            List<HubEauClient.Observation> obs = e.getValue();
            if (obs.isEmpty()) continue;
            int color = PALETTE[colorIdx % PALETTE.length];
            colorIdx++;
            linePaint.setColor(color);
            fillPaint.setColor(color);
            fillPaint.setAlpha(40);

            Path line = new Path();
            Path fill = new Path();
            boolean first = true;
            for (HubEauClient.Observation o : obs) {
                float x = padL + chartW * (o.timestampMs - tMin) / (tMax - tMin);
                float y = padT + chartH * (1f - o.debit / yMax);
                if (first) {
                    line.moveTo(x, y);
                    fill.moveTo(x, padT + chartH);
                    fill.lineTo(x, y);
                    first = false;
                } else {
                    line.lineTo(x, y);
                    fill.lineTo(x, y);
                }
            }
            fill.lineTo(padL + chartW * (obs.get(obs.size() - 1).timestampMs - tMin) / (tMax - tMin), padT + chartH);
            fill.close();
            canvas.drawPath(fill, fillPaint);
            fillPaint.setAlpha(255);
            canvas.drawPath(line, linePaint);

            fillPaint.setAlpha(255);
            canvas.drawRect(legendX, h - 34f, legendX + 16f, h - 18f, fillPaint);
            canvas.drawText(truncate(e.getKey(), 24), legendX + 24f, h - 18f, legendPaint);
            legendX += 24f + legendPaint.measureText(truncate(e.getKey(), 24)) + 28f;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }
}
