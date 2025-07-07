package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

public class ProfileStarGiftsPattern extends Drawable {

    @NonNull
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;

    private int defaultAvatarSize;

    private float commonAlpha = 1f;

    private int backgroundColor = Color.TRANSPARENT;

    @NonNull
    private final RectF avatarBounds = new RectF();

    private final int patternSize = dp(24f);

    private boolean hasEmoji;

    @NonNull
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[][] circles = new int[][] {
        { 0, 90, 270 },
        { dp(4), 35, 145, 215, 325 },
        { dp(32), 0, 180 },
        { dp(40), 60, 120, 240, 300 },
        { dp(64), 30, 150, 210, 330 },
        { dp(80), 0, 180 }
    };

    private final float[][] delays = new float[][] {
        { 0.85f, 0.9f },
        { 0.83f, 0.79f, 0.85f, 0.75f },
        { 0.67f, 0.6f },
        { 0.1f, 0.59f, 0.1f, 0.3f },
        { 0.25f, 0.33f, 0.6f, 0.25f },
        { 0.4f, 0.5f }
    };


    public ProfileStarGiftsPattern(@NonNull View parentView) {
        pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(
            parentView,
            false,
            patternSize,
            AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC
        );

        setCallback(parentView);
        parentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                pattern.attach();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                pattern.detach();
            }
        });
    }


    public boolean hasEmoji() {
        return hasEmoji;
    }

    public void setEmoji(long emojiId, boolean animated) {
        hasEmoji = emojiId != 0 && emojiId != -1;
        pattern.set(emojiId, animated);
    }

    public void setEmojiColor(@ColorInt int color) {
        pattern.setColor(color);
        invalidateSelf();
    }

    public void setBackgroundColor(@ColorInt int color) {
        if (backgroundColor == color) {
            return;
        }

        backgroundColor = color;
        invalidateGlow();
    }

    public void setDefaultAvatarSize(int size) {
        if (defaultAvatarSize == size) {
            return;
        }

        defaultAvatarSize = size;
        invalidateGlow();
    }

    private void invalidateGlow() {
        if (defaultAvatarSize <= 0) {
            return;
        }

        float radius = defaultAvatarSize * 2.5f;
        RadialGradient glowGradient = new RadialGradient(
            radius, radius,
            radius,
            ColorUtils.blendARGB(0x15FFFFFF, backgroundColor, .5f), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        );
        glowPaint.setShader(glowGradient);
        invalidateSelf();
    }

    public void setAvatarBounds(@NonNull RectF rect) {
        avatarBounds.set(rect);
    }

    float expandCollapseProgress;

    public void setExpandCollapseProgress(float progress) {
        expandCollapseProgress = progress;
    }

    public void release() {
        pattern.detach();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float avatarWidth = avatarBounds.width();
        float avatarHeight = avatarBounds.height();
        float avatarCx = avatarBounds.centerX();
        float avatarCy = avatarBounds.centerY();

        float glowSize = defaultAvatarSize * 5f;
        float glowHalfSize = glowSize / 2f;
        float glowScaleX = avatarWidth / defaultAvatarSize;
        float glowScaleY = avatarHeight / defaultAvatarSize;
        glowPaint.setAlpha((int) (255 * expandCollapseProgress * expandCollapseProgress * commonAlpha));
        canvas.save();
        canvas.translate(avatarCx - glowHalfSize, avatarCy - glowHalfSize);
        canvas.scale(glowScaleX, glowScaleY, glowHalfSize, glowHalfSize);
        canvas.drawRect(0, 0, glowSize, glowSize, glowPaint);
        canvas.restore();

        pattern.setBounds(0, 0, patternSize, patternSize);

        float innerRadius =
            (float) Math.sqrt(avatarWidth * avatarWidth + avatarHeight * avatarHeight) / 2f;

        for (int i = 0; i < circles.length; i++) {
            int[] circle = circles[i];
            float r = innerRadius + circle[0];
            for (int j = 1; j < circle.length; j++) {
                float patternIndex = i * circle.length + (j - 1);
                float totalPatterns = circles.length * (circle.length - 1);
                float delayFactor = 1f - (patternIndex / totalPatterns);
                float localProgress = Utilities.clamp((expandCollapseProgress - delays[i][j - 1]), 1f, 0f)  / (1f - delays[i][j - 1]);
                double angle = Math.toRadians(circle[j]);
                float scale = lerp(
                    1f,
                    lerp(.32f, .64f, localProgress * localProgress),
                    (float) i / circles.length
                );
                int alpha = (int) (lerp(80, 25, (float) i / circles.length) * commonAlpha);

//                float x = lerp(
//                    avatarCx,
//                    avatarCx + r * (float) Math.cos(angle),
//                    localProgress
//                ) - patternSize / 2f;
//                float y = lerp(
//                    avatarCy,
//                    avatarCy + r * (float) Math.sin(angle),
//                    localProgress
//                ) - patternSize / 2f;

                PointF start = new PointF(avatarCx, avatarCy);
                PointF end = new PointF(
                    avatarCx + r * (float) Math.cos(angle),
                    avatarCy + r * (float) Math.sin(angle)
                );

                float dx = end.x - start.x;
                float dy = end.y - start.y;

                float nx = -dy;
                float ny = dx;

                float length = (float) Math.sqrt(nx * nx + ny * ny);
                nx /= length;
                ny /= length;

                float side = Math.signum(dx);

                float curvature = r * .4f;
                PointF control = new PointF(
                    (start.x + end.x) / 2 + nx * curvature * side,
                    (start.y + end.y) / 2 + ny * curvature * side
                );

                PointF p = quadraticBezier(start, control, end, localProgress);

                float x = p.x - patternSize / 2f;
                float y = p.y - patternSize / 2f;

                canvas.save();
                canvas.translate(x, y);
                canvas.scale(scale, scale, patternSize / 2f, patternSize / 2f);
                pattern.setAlpha(alpha);
                pattern.draw(canvas);
                canvas.restore();
            }
        }
    }

    PointF quadraticBezier(PointF p0, PointF p1, PointF p2, float t) {
        float u = 1 - t;
        float x = u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x;
        float y = u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y;
        return new PointF(x, y);
    }

    @Override
    public void setAlpha(int alpha) {
        setAlpha(alpha / 255f);
    }

    public void setAlpha(float alpha) {
        if (commonAlpha != alpha) {
            commonAlpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // No-op
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

}
