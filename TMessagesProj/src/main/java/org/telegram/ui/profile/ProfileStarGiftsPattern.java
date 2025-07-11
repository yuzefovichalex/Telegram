package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;
import static java.lang.Math.toRadians;

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
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.List;

public class ProfileStarGiftsPattern extends Drawable {

    @NonNull
    private final PointF tmpPointF = new PointF();

    @NonNull
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;

    private int defaultAvatarSize;

    private float commonAlpha = 1f;

    private int backgroundColor = Color.TRANSPARENT;

    @NonNull
    private final RectF avatarBounds = new RectF();

    private final int patternSize = dp(24f);
    private final int patternHalfSize = patternSize / 2;

    private boolean hasEmoji;
    private boolean isEmojiLoaded;

    @NonNull
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float expandCollapseProgress;

    @NonNull
    private final double[][] circles = new double[][] {
        { 0, toRadians(90), toRadians(270) },
        { dp(4), toRadians(35), toRadians(145), toRadians(215), toRadians(325) },
        { dp(32), toRadians(0), toRadians(180) },
        { dp(40), toRadians(60), toRadians(120), toRadians(240), toRadians(300) },
        { dp(64), toRadians(30), toRadians(150), toRadians(210), toRadians(330) },
        { dp(80), toRadians(0), toRadians(180) }
    };

    @NonNull
    private final float[][] velocities = new float[][] {
        { 0.85f, 0.9f },
        { 0.83f, 0.79f, 0.85f, 0.75f },
        { 0.67f, 0.6f },
        { 0.23f, 0.59f, 0.2f, 0.4f },
        { 0.45f, 0.51f, 0.6f, 0.29f },
        { 0.4f, 0.5f }
    };

    // distance, angle, velocity
    private final float[][] giftsParams = new float[][] {
        { dp(8), (float) toRadians(35), .82f },
        { dp(8), (float) toRadians(215), .78f },
        { dp(8), (float) toRadians(145), .58f },
        { dp(8), (float) toRadians(325), .62f },
        { dp(32), (float) toRadians(0), .38f },
        { dp(32), (float) toRadians(180), .42f },
        { dp(64), (float) toRadians(343), .2f },
        { dp(64), (float) toRadians(197), .1f },
        { dp(4), (float) toRadians(270), .9f }
    };

    @Nullable
    private List<ProfileGiftsController.Gift> gifts;

    @Nullable
    private ProfileGiftsController.Gift pressedGift;

    @Nullable
    private Callback callback;


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
        if (parentView.isAttachedToWindow()) {
            pattern.attach();
        }
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

    public void setExpandCollapseProgress(float progress) {
        expandCollapseProgress = progress;
    }

    public void release() {
        pattern.detach();
    }

    private boolean isEmojiLoaded() {
        if (isEmojiLoaded) {
            return true;
        }
        if (pattern.getDrawable() instanceof AnimatedEmojiDrawable) {
            AnimatedEmojiDrawable drawable = (AnimatedEmojiDrawable) pattern.getDrawable();
            if (drawable.getImageReceiver() != null && drawable.getImageReceiver().hasImageLoaded()) {
                return isEmojiLoaded = true;
            }
        }
        return false;
    }

    public void setGifts(@Nullable List<ProfileGiftsController.Gift> gifts) {
        this.gifts = gifts;
        invalidateSelf();
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (commonAlpha == 0f || expandCollapseProgress >= 2f) {
            return;
        }

        float avatarWidth = avatarBounds.width();
        float avatarHeight = avatarBounds.height();
        float avatarCx = avatarBounds.centerX();
        float avatarCy = avatarBounds.centerY();
        float avatarRadius =
            (float) Math.sqrt(avatarWidth * avatarWidth + avatarHeight * avatarHeight) / 2f;

        if (hasEmoji) {
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
        }

        if (isEmojiLoaded()) {
            pattern.setBounds(0, 0, patternSize, patternSize);

            for (int i = 0; i < circles.length; i++) {
                double[] circle = circles[i];
                float r = avatarRadius + (float) circle[0];
                float distanceFactor = (float) i / circles.length;
                for (int j = 1; j < circle.length; j++) {
                    float velocity = velocities[i][j - 1];
                    float localProgress = clamp(
                        (expandCollapseProgress - velocity),
                        1f,
                        0f
                    ) / (1f - velocity);

                    int alpha = (int) (lerp(80, 25, distanceFactor) * localProgress * commonAlpha);
                    if (alpha == 0) {
                        continue;
                    }

                    double angle = circle[j];
                    float scale = lerp(1f, .64f, distanceFactor) * lerp(.8f, 1f, localProgress);

                    float patternX = avatarCx + r * (float) Math.cos(angle);
                    float patternY = avatarCy + r * (float) Math.sin(angle);

                    PointF currentPatternPos =
                        qerp(avatarCx, avatarCy, patternX, patternY, r, localProgress);

                    canvas.save();
                    canvas.translate(
                        currentPatternPos.x - patternHalfSize,
                        currentPatternPos.y - patternHalfSize
                    );
                    canvas.scale(scale, scale, patternHalfSize, patternHalfSize);
                    pattern.setAlpha(alpha);
                    pattern.draw(canvas);
                    canvas.restore();
                }
            }
        }

        if (gifts != null && !gifts.isEmpty()) {
            int count = Math.min(9, gifts.size());
            for (int i = 0; i < count; ++i) {
                ProfileGiftsController.Gift gift = gifts.get(i);
                float[] params = giftsParams[i];
                float r = avatarRadius + params[0];
                float angle = params[1];
                float velocity = params[2];

                float localProgress = clamp(
                    (expandCollapseProgress - velocity),
                    1f,
                    0f
                ) / (1f - velocity);

                float visibilityAlpha = gift.animatedFloat.set(1f);
                float visibilityScale = lerp(.5f, 1f, visibilityAlpha);
                float alpha = visibilityAlpha * localProgress;
                float scale = visibilityScale * lerp(.8f, 1f, localProgress);
                float x = avatarCx + r * (float) Math.cos(angle);
                float y = avatarCy + r * (float) Math.sin(angle);

                PointF currentPatternPos =
                    qerp(avatarCx, avatarCy, x, y, r, localProgress);

                gift.draw(
                    canvas,
                    currentPatternPos.x, currentPatternPos.y,
                    scale,
                    0f,
                    alpha,
                    lerp(0f, .9f, Math.min(expandCollapseProgress, 1f))
                );
            }
        }
    }

    @NonNull
    private PointF qerp(
        float x1, float y1,
        float x2, float y2,
        float r,
        float p
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float nx = -dy;
        float ny = dx;
        float length = (float) Math.sqrt(nx * nx + ny * ny);
        nx /= length;
        ny /= length;
        float side = Math.signum(dx);
        float curvature = r * .4f;
        float controlX = (x1 + x2) / 2 + nx * curvature * side;
        float controlY = (y1 + y2) / 2 + ny * curvature * side;

        return quadraticBezier(
            x1, y1,
            controlX, controlY,
            x2, y2,
            p
        );
    }

    @NonNull
    private PointF quadraticBezier(
        float p1x, float p1y,
        float cpx, float cpy,
        float p2x, float p2y,
        float t
    ) {
        float u = 1 - t;
        float x = u * u * p1x + 2 * u * t * cpx + t * t * p2x;
        float y = u * u * p1y + 2 * u * t * cpy + t * t * p2y;
        tmpPointF.set(x, y);
        return tmpPointF;
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

    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final ProfileGiftsController.Gift hit = getGiftUnder(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedGift = hit;
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedGift != hit && pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedGift != null) {
                if (callback != null) {
                    callback.onGiftClick(pressedGift);
                }
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        }
        return pressedGift != null;
    }

    public ProfileGiftsController.Gift getGiftUnder(float x, float y) {
        if (gifts == null) {
            return null;
        }

        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        return null;
    }


    public interface Callback {
        void onGiftClick(@NonNull ProfileGiftsController.Gift gift);
    }

}
