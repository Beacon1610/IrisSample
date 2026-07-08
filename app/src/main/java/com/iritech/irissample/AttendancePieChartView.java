package com.iritech.irissample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Biểu đồ tròn nhỏ cho thống kê điểm danh, không phụ thuộc thư viện chart ngoài.
 * Dữ liệu được cung cấp qua {@link #setData(int, int)}.
 */
public class AttendancePieChartView extends View {

    private static final float START_ANGLE = -90f;

    private final Paint presentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint absentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chartBounds = new RectF();

    private int presentCount;
    private int absentCount;
    private float ringWidth;

    public AttendancePieChartView(Context context) {
        super(context);
        init();
    }

    public AttendancePieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AttendancePieChartView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringWidth = dpToPx(18f);

        configureArcPaint(
                presentPaint,
                ContextCompat.getColor(getContext(), R.color.colorSuccess)
        );
        configureArcPaint(
                absentPaint,
                ContextCompat.getColor(getContext(), R.color.colorError)
        );
        configureArcPaint(
                emptyPaint,
                ContextCompat.getColor(getContext(), R.color.colorDivider)
        );

        percentagePaint.setColor(
                ContextCompat.getColor(getContext(), R.color.colorTextPrimary)
        );
        percentagePaint.setTextAlign(Paint.Align.CENTER);
        percentagePaint.setTextSize(spToPx(21f));
        percentagePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        captionPaint.setColor(
                ContextCompat.getColor(getContext(), R.color.colorTextSecondary)
        );
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(spToPx(10f));

        setData(0, 0);
    }

    private void configureArcPaint(Paint paint, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(ringWidth);
    }

    /**
     * Cập nhật số sinh viên có mặt và vắng mặt.
     * Giá trị âm được chuẩn hóa về 0 để view luôn vẽ an toàn.
     */
    public void setData(int presentCount, int absentCount) {
        this.presentCount = Math.max(0, presentCount);
        this.absentCount = Math.max(0, absentCount);

        long total = (long) this.presentCount + this.absentCount;
        int percentage = total == 0
                ? 0
                : Math.round(this.presentCount * 100f / total);

        if (total == 0) {
            setContentDescription("Chưa có dữ liệu điểm danh");
        } else {
            setContentDescription(String.format(
                    Locale.getDefault(),
                    "Tỷ lệ cả lớp %d phần trăm, có mặt %d, vắng %d",
                    percentage,
                    this.presentCount,
                    this.absentCount
            ));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float diameter = Math.min(contentWidth, contentHeight) - ringWidth;

        if (diameter <= 0f) {
            return;
        }

        float centerX = getPaddingLeft() + contentWidth / 2f;
        float centerY = getPaddingTop() + contentHeight / 2f;
        float radius = diameter / 2f;
        chartBounds.set(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
        );

        long total = (long) presentCount + absentCount;
        int percentage = total == 0
                ? 0
                : Math.round(presentCount * 100f / total);

        if (total == 0) {
            canvas.drawArc(chartBounds, 0f, 360f, false, emptyPaint);
        } else {
            float presentSweep = presentCount * 360f / total;
            canvas.drawArc(
                    chartBounds,
                    START_ANGLE,
                    presentSweep,
                    false,
                    presentPaint
            );
            canvas.drawArc(
                    chartBounds,
                    START_ANGLE + presentSweep,
                    360f - presentSweep,
                    false,
                    absentPaint
            );
        }

        Paint.FontMetrics percentageMetrics = percentagePaint.getFontMetrics();
        Paint.FontMetrics captionMetrics = captionPaint.getFontMetrics();
        float percentageBaseline = centerY
                - percentageMetrics.descent / 2f
                - dpToPx(3f);
        float captionBaseline = centerY
                - captionMetrics.ascent
                + dpToPx(12f);

        canvas.drawText(
                String.format(Locale.getDefault(), "%d%%", percentage),
                centerX,
                percentageBaseline,
                percentagePaint
        );
        canvas.drawText(
                total == 0 ? "Chưa có dữ liệu" : "Cả lớp",
                centerX,
                captionBaseline,
                captionPaint
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredSize = Math.round(dpToPx(150f));
        int measuredWidth = resolveSize(desiredSize, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredSize, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
