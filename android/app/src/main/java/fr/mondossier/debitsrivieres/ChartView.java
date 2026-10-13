package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class ChartView extends View {

    private static final int[] PALETTE = {
            Color.parseColor("#1565C0"), Color.parseColor("#2E7D32"),
            Color.parseColor("#C62828"), Color.parseColor("#6A1B9A"),
            Color.parseColor("#EF6C00")
    };

    private Map<String, List<HubEauClient.Observation>> series = new TreeMap<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float padL = 110f, padR = 20f, padT = 20f, padB = 90f;
    private long tMin = 0, tMax = 1;
    private float yMax = 1f;
    private boolean hasData = false;
    private float touchX = -1f;
    private String touchLabel = null;

    public interface TouchListener {
        void onTouch(ObservationAt obs);
    }

    public static final class ObservationAt {
        public final long timestampMs;
        public final float debit;
        public final String when;

        ObservationAt(long t, float d, String w) {
            timestampMs = t;
            debit = d;
            when = w;
        }
    }

    private TouchListener touchListener;

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
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(2f);
        cursorPaint.setColor(Color.parseColor("#C62828"));
        cursorTextPaint.setColor(Color.WHITE);
        cursorTextPaint.setTextSize(26f);
    }

    public void setSeries(Map<String, List<HubEauClient.Observation>> s) {
        this.series = s != null ? s : Collections.emptyMap();
        touchX = -1f;
        touchLabel = null;
        hasData = false;
        invalidate();
    }

    public void setTouchListener(TouchListener l) {
        this.touchListener = l;
    }

    public void clearCursor() {
        touchX = -1f;
        touchLabel = null;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasData) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touchX = event.getX();
                updateCursor();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateCursor();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateCursor() {
        if (touchX < padL) {
            touchLabel = null;
            if (touchListener != null) touchListener.onTouch(null);
            return;
        }
        List<HubEauClient.Observation> obs = firstSeries();
        if (obs == null || obs.isEmpty()) {
            touchLabel = null;
            return;
        }
        float chartW = getWidth() - padL - padR;
        float frac = Math.max(0f, Math.min(1f, (touchX - padL) / chartW));
        long t = tMin + (long) ((tMax - tMin) * frac);
        HubEauClient.Observation best = null;
        int lo = 0, hi = obs.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            HubEauClient.Observation o = obs.get(mid);
            if (o.timestampMs < t) lo = mid + 1;
            else hi = mid;
        }
        if (hi >= 0) best = obs.get(hi);
        if (lo < obs.size()) {
            HubEauClient.Observation o = obs.get(lo);
            if (best == null || Math.abs(o.timestampMs - t) < Math.abs(best.timestampMs - t)) best = o;
        }
        if (best == null) {
            touchLabel = null;
            if (touchListener != null) touchListener.onTouch(null);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
        fmt.setTimeZone(java.util.TimeZone.getDefault());
        touchLabel = String.format(Locale.FRANCE, "%.1f m³/s", best.debit);
        if (touchListener != null) {
            touchListener.onTouch(new ObservationAt(best.timestampMs, best.debit, fmt.format(new Date(best.timestampMs))));
        }
    }

    private List<HubEauClient.Observation> firstSeries() {
        for (List<HubEauClient.Observation> l : series.values()) {
            if (!l.isEmpty()) return l;
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        padL = 110f * Math.max(0.7f, density * 0.4f);
        padB = 90f * Math.max(0.7f, density * 0.4f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hasData = false;
        for (List<HubEauClient.Observation> obs : series.values()) {
            if (!obs.isEmpty()) { hasData = true; break; }
        }
        if (!hasData) return;

        float w = getWidth(), h = getHeight();
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;

        tMin = Long.MAX_VALUE;
        tMax = Long.MIN_VALUE;
        yMax = 0f;
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
        fmt.setTimeZone(java.util.TimeZone.getDefault());
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
        for (Map.Entry<String, List<HubEauClient.Observation>> e : series.entrySet()) {
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
            float lastX = padL + chartW * (obs.get(obs.size() - 1).timestampMs - tMin) / (tMax - tMin);
            fill.lineTo(lastX, padT + chartH);
            fill.close();
            canvas.drawPath(fill, fillPaint);
            fillPaint.setAlpha(255);
            canvas.drawPath(line, linePaint);

            canvas.drawRect(legendX, h - 34f, legendX + 16f, h - 18f, fillPaint);
            canvas.drawText(truncate(e.getKey(), 24), legendX + 24f, h - 18f, legendPaint);
            legendX += 24f + legendPaint.measureText(truncate(e.getKey(), 24)) + 28f;
        }

        if (touchX >= padL && touchLabel != null) {
            List<HubEauClient.Observation> obs = firstSeries();
            float frac = Math.max(0f, Math.min(1f, (touchX - padL) / chartW));
            long t = tMin + (long) ((tMax - tMin) * frac);
            HubEauClient.Observation best = null;
            int lo = 0, hi = obs.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                HubEauClient.Observation o = obs.get(mid);
                if (o.timestampMs < t) lo = mid + 1;
                else hi = mid;
            }
            if (hi >= 0) best = obs.get(hi);
            if (lo < obs.size()) {
                HubEauClient.Observation o = obs.get(lo);
                if (best == null || Math.abs(o.timestampMs - t) < Math.abs(best.timestampMs - t)) best = o;
            }
            if (best != null) {
                float cx = padL + chartW * (best.timestampMs - tMin) / (tMax - tMin);
                float cy = padT + chartH * (1f - best.debit / yMax);
                cursorPaint.setColor(Color.parseColor("#C62828"));
                canvas.drawLine(cx, padT, cx, padT + chartH, cursorPaint);
                canvas.drawCircle(cx, cy, 8f, cursorPaint);
                cursorPaint.setStyle(Paint.Style.FILL);
                cursorPaint.setAlpha(60);
                canvas.drawCircle(cx, cy, 14f, cursorPaint);
                cursorPaint.setAlpha(255);
                cursorPaint.setStyle(Paint.Style.STROKE);

                String txt = touchLabel + "\n" + when(best.timestampMs);
                Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                boxPaint.setColor(Color.parseColor("#C62828"));
                float textW = Math.max(cursorTextPaint.measureText(touchLabel),
                        cursorTextPaint.measureText(when(best.timestampMs)));
                float boxW = textW + 24f;
                float boxH = 68f;
                float bx = Math.min(Math.max(cx - boxW / 2f, padR), w - boxW - padR);
                float by = Math.max(cy - boxH - 16f, padT);
                canvas.drawRoundRect(bx, by, bx + boxW, by + boxH, 10f, 10f, boxPaint);
                canvas.drawText(touchLabel, bx + 12f, by + 30f, cursorTextPaint);
                canvas.drawText(when(best.timestampMs), bx + 12f, by + 60f, cursorTextPaint);
            }
        }
    }

    private String when(long t) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        fmt.setTimeZone(java.util.TimeZone.getDefault());
        return fmt.format(new Date(t));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
