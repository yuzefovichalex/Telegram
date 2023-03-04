package org.telegram.ui.Components.voip;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class LoadingDrawable extends Drawable {

    private static final int DOT_COUNT = 3;

    private final int dotSize = AndroidUtilities.dp(5);

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] progressPerDot = new float[DOT_COUNT];


    public LoadingDrawable() {
        dotPaint.setColor(Color.WHITE);
    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        float cy = getBounds().exactCenterY();
        for (int i = 0; i < DOT_COUNT; i++) {
            if (progressPerDot[i] > 1f) {
                progressPerDot[i] = 0;
                rightRotateByOne();
            }
            if (i != 0) {
                // Something like start delay
                if (progressPerDot[i - 1] - progressPerDot[i] < 1f / DOT_COUNT) {
                    continue;
                }
            }
            float progress = progressPerDot[i];
            canvas.save();
            canvas.translate(getBounds().width() * progress, 0f);
            float scale = progress < 0.5f ? progress : 1f - progress;
            canvas.drawCircle(dotSize / 2f, cy, dotSize * scale, dotPaint);
            canvas.restore();
            progressPerDot[i] += 0.015;
        }
        invalidateSelf();
    }

    private void rightRotateByOne() {
        int i;
        float temp = progressPerDot[0];
        for (i = 0; i < DOT_COUNT - 1; i++) {
            progressPerDot[i] = progressPerDot[i + 1];
        }
        progressPerDot[i] = temp;
    }

    @Override
    public void setAlpha(int alpha) {
        dotPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        dotPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return dotSize * (2 * DOT_COUNT - 1);
    }

    @Override
    public int getIntrinsicHeight() {
        return dotSize;
    }
}
