package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class PointerDrawable extends Drawable {

    private static final int DEFAULT_ANGLE = 39;
    private static final int DEFAULT_GAP = 12;
    private static final int DEFAULT_STROKE_WIDTH = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);

    private final Path arrowPath1 = new Path();
    private final Path arrowPath2 = new Path();

    private float angle;
    private float side;
    private float gap = DEFAULT_GAP;
    private float strokeWidth = DEFAULT_STROKE_WIDTH;

    private float progress;


    public PointerDrawable() {
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        animator.setDuration(1000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(animator::start, 2000);
            }
        });

        setAngle(DEFAULT_ANGLE);

        animator.start();
    }


    public void setAngle(float degrees) {
        angle = (float) Math.toRadians(degrees);
        side = (float) (getIntrinsicWidth() / (2 * Math.cos(angle)));
        invalidateSelf();
    }

    public void setGap(float gap) {
        this.gap = gap;
        invalidateSelf();
    }

    public void setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int w = getIntrinsicWidth();
        int cx = getBounds().left;
        int cy = getBounds().top + getIntrinsicHeight() / 2;
        float sy1 = cy - gap / 2 - strokeWidth / 2;

        // 1 arrow
        fillArrow(arrowPath1, cx, sy1 + gap * .75f * progress, w);
        canvas.drawPath(arrowPath1, paint);

        // 2 arrow
        fillArrow(arrowPath2, cx, sy1 + gap + gap * .5f * progress, w);
        canvas.drawPath(arrowPath2, paint);
    }

    private void fillArrow(@NonNull Path path, float sx, float sy, float w) {
        path.rewind();
        path.moveTo(sx, sy);
        path.lineTo(
            sx + side * (float) Math.cos(angle),
            sy + side * (float) Math.sin(angle)
        );
        path.lineTo(sx + w, sy);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(10);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(10);
    }

}
