package org.telegram.ui.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class ShadingView extends View {

    public static final int TOP_BOTTOM = 0;
    public static final int BOTTOM_TOP = 1;

    @ColorInt
    private int color = 0x42000000;
    private int solidHeight = 0;
    private int gradientHeight = 0;

    private int orientation = TOP_BOTTOM;

    @NonNull
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @NonNull
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);


    public ShadingView(@NonNull Context context) {
        super(context);

        solidPaint.setColor(color);
    }


    public void setColor(@ColorInt int color) {
        if (this.color != color) {
            this.color = color;
            solidPaint.setColor(color);
            recreateGradient();
            invalidate();
        }
    }

    public void setSolidHeight(int height) {
        if (solidHeight != height) {
            solidHeight = height;
            if (!isInLayout()) {
                requestLayout();
            }
        }
    }

    public void setGradientHeight(int height) {
        if (gradientHeight != height) {
            gradientHeight = height;
            recreateGradient();
            if (!isInLayout()) {
                requestLayout();
            }
        }
    }

    public void setOrientation(int orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            recreateGradient();
            invalidate();
        }
    }

    private void recreateGradient() {
        LinearGradient gradient = new LinearGradient(
            0, orientation == TOP_BOTTOM ? 0 : gradientHeight,
            0, orientation == TOP_BOTTOM ? gradientHeight : 0,
            new int[] { color, Color.TRANSPARENT },
            null,
            Shader.TileMode.CLAMP
        );
        gradientPaint.setShader(gradient);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        int desiredHeight = solidHeight + gradientHeight;
        int finalHeight = Math.min(availableHeight, desiredHeight);
        int finalHeightSpec = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, finalHeightSpec);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (orientation == TOP_BOTTOM) {
            canvas.drawRect(0f, 0f, getMeasuredWidth(), solidHeight, solidPaint);
            canvas.save();
            canvas.translate(0f, solidHeight);
            canvas.drawRect(0f, 0f, getMeasuredWidth(), gradientHeight, gradientPaint);
            canvas.restore();
        } else {
            canvas.drawRect(0f, 0f, getMeasuredWidth(), gradientHeight, gradientPaint);
            canvas.drawRect(0f, gradientHeight, getMeasuredWidth(), solidHeight + gradientHeight, solidPaint);
        }
    }

}
