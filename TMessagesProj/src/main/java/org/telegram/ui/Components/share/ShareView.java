package org.telegram.ui.Components.share;

import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;
import static java.lang.Math.round;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

public class ShareView extends View {

    private static final int DEFAULT_ITEM_SIZE = 115;
    private static final int DEFAULT_ITEM_COUNT = 5;
    private static final int DEFAULT_PADDING = 16;
    private static final int DEFAULT_OFFSET = 25;
    private static final int SIDE_OFFSET = 15;

    @NonNull
    private final Paint commonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint opacityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @NonNull
    private final ValueAnimator showAnimator = ValueAnimator.ofFloat(0f, 1f);

    private float showProgress;
    private boolean isFullyShown;

    private final OpenGLBitmapProcessor morphGLBitmapProcessor;
    private final OpenGLBitmapProcessor blurGLBitmapProcessor;

    private Bitmap blurringBitmap;
    private Canvas blurringBitmapCanvas;
    private Bitmap blurredBitmap;
    private final Paint blurredBitmapPaint;

    private Bitmap dst;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private final PorterDuffXfermode srcInMode = new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY);
    private final PorterDuffXfermode clearMode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    private int itemSize = DEFAULT_ITEM_SIZE;
    private float itemRadius = itemSize / 2f;
    private int itemCount = DEFAULT_ITEM_COUNT;
    private int padding = DEFAULT_PADDING;
    private int offset = DEFAULT_OFFSET;

    private int fullWidth;
    private int fullHeight;
    private int containerWidth;
    private int containerHeight;
    private int bounceOffset;
    private final RectF bounds = new RectF();

    private float anchorX = -1;
    private float anchorY = -1;
    private int anchorSize = 0;

    @Nullable
    private Controller controller;

    private boolean needsItemCountEnsure;

    private final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
    private int selectedIdx = -1;

    private final Path hideAnimationPath = new Path();
    private final PathMeasure hideAnimationPathMeasure = new PathMeasure();
    private final float[] hideAnimatedPos = new float[2];

    private final ValueAnimator hideAnimator = ValueAnimator.ofFloat(0f, 1f);
    private float hideProgress = 0f;
    private boolean isHideRunning;

    @Nullable
    private OnHideListener onHideListener;

    private int color = 0xFFFFFF;


    public ShareView(@NonNull Context context) {
        this(context, null);
    }

    public ShareView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        morphGLBitmapProcessor = new OpenGLBitmapProcessor(context);
        morphGLBitmapProcessor.onAttach();
        blurGLBitmapProcessor = new OpenGLBitmapProcessor(context);
        blurGLBitmapProcessor.onAttach();

        blurredBitmapPaint = new Paint();
        blurredBitmapPaint.setAntiAlias(true);
        blurredBitmapPaint.setDither(true);

        showAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        showAnimator.setDuration(525L);
        showAnimator.addUpdateListener((animation -> {
            isFullyShown = false;
            showProgress = (float) animation.getAnimatedValue();
            invalidate();
        }));
        showAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFullyShown = true;
            }
        });

        hideAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        hideAnimator.setDuration(325L);
        hideAnimator.addUpdateListener((animation -> {
            isHideRunning = true;
            hideProgress = (float) animation.getAnimatedValue();
            invalidate();
        }));
        hideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                anchorX = -1;
                anchorY = -1;
                selectedIdx = -1;
                showProgress = 0f;
                isFullyShown = false;
                hideProgress = 0f;
                isHideRunning = false;
                hideAnimationPath.reset();

                if (onHideListener != null) {
                    onHideListener.onHide();
                }
            }
        });

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }


    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
        invalidateParams();
    }

    public void setItemSize(int itemSize) {
        this.itemSize = itemSize;
        invalidateParams();
    }

    public void setPadding(int padding) {
        this.padding = padding;
        invalidateParams();
    }

    public void setOffset(int offset) {
        this.offset = offset;
        invalidateParams();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    private void invalidateParams() {
        if (getMeasuredWidth() == 0) {
            needsItemCountEnsure = true;
            return;
        }

        while (true) {
            containerWidth = itemSize * itemCount + padding * (itemCount + 1);
            fullWidth = containerWidth + SIDE_OFFSET * 2;
            if (fullWidth <= getMeasuredWidth()) {
                break;
            } else {
                itemCount--;
            }
        }

        containerHeight = itemSize + padding * 2;
        fullHeight = containerHeight + offset + anchorSize + SIDE_OFFSET;
        itemRadius = itemSize / 2f;
        bounceOffset = containerHeight / 2;

        if (dst != null) {
            dst.recycle();
        }
        dst = Bitmap.createBitmap(
            fullWidth + bounceOffset * 2,
            fullHeight + bounceOffset,
            Bitmap.Config.ARGB_8888
        );

        needsItemCountEnsure = false;

        morphGLBitmapProcessor.initSurface(fullWidth + bounceOffset * 2, fullHeight + bounceOffset);
    }

    public void setAnchor(float x, float y, int size, Shader shader) {
        anchorX = x;
        anchorY = y;
        anchorSize = size;

        if (blurringBitmap != null) {
            blurringBitmap.recycle();
        }
        blurringBitmap = Bitmap.createBitmap(anchorSize, anchorSize, Bitmap.Config.ARGB_8888);
        blurringBitmapCanvas = new Canvas(blurringBitmap);

        if (blurredBitmap != null) {
            blurredBitmap.recycle();
        }
        blurredBitmap = Bitmap.createBitmap(anchorSize, anchorSize, Bitmap.Config.ARGB_8888);
        blurredBitmapPaint.setShader(
            new ComposeShader(
                new BitmapShader(blurredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                shader,
                PorterDuff.Mode.ADD
            )
        );

        dstRect.set(0, 0, anchorSize, anchorSize);

        blurGLBitmapProcessor.initSurface(anchorSize, anchorSize);

        invalidateParams();
    }

    public void setController(@Nullable Controller controller) {
        this.controller = controller;
    }

    void setOnHideListener(@Nullable OnHideListener onHideListener) {
        this.onHideListener = onHideListener;
    }

    public void setShowProgress(float showProgress) {
        this.showProgress = showProgress;
        invalidate();
    }

    public void show() {
        if (anchorX == -1 || anchorY == -1 || controller == null) {
            return;
        }

        showAnimator.cancel();
        showProgress = 0f;
        showAnimator.start();
    }

    public void hide(float x, float y) {
        float startX = bounds.left + padding * (selectedIdx + 1) + itemSize * selectedIdx;
        float startY = bounds.top + padding;
        hideAnimationPath.reset();
        hideAnimationPath.moveTo(startX, startY);
        hideAnimationPath.quadTo(x, startY - containerHeight * 2, x, y);
        hideAnimationPathMeasure.setPath(hideAnimationPath, false);

        hide();
    }

    public void hide() {
        float speed = 2000f / 1000f; // 2000px per 1000ms
        long duration = (long) (hideAnimationPathMeasure.getLength() / speed);
        if (duration == 0) {
            duration = 525L;
        }
        hideAnimator.setDuration(duration);
        hideAnimator.start();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        if (controller != null) {
            for (int i = 0; i < itemCount; i++) {
                if (controller.getItemDrawable(i) == who) {
                    return true;
                }
            }
        }
        return super.verifyDrawable(who);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (needsItemCountEnsure) {
            invalidateParams();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (anchorX == -1 || anchorY == -1 || controller == null) {
            return;
        }

        super.onDraw(canvas);

        float halfContainerWidthWithSideOffset = containerWidth / 2f + SIDE_OFFSET;
        float anchorRadius = anchorSize / 2f;
        float containerRadius = containerHeight / 2f;
        float outOffset = 0f;

        float animationCenterStartX = anchorX;
        float animationCenterStartY = anchorY;
        float intrinsicCenterEndX = anchorX + anchorRadius + containerRadius - halfContainerWidthWithSideOffset;

        if (intrinsicCenterEndX - halfContainerWidthWithSideOffset < 0) {
            outOffset = (intrinsicCenterEndX - halfContainerWidthWithSideOffset);
        } else if (intrinsicCenterEndX + halfContainerWidthWithSideOffset > getMeasuredWidth()) {
            outOffset = intrinsicCenterEndX - (getMeasuredWidth() - halfContainerWidthWithSideOffset);
        }
        float animationCenterEndX = intrinsicCenterEndX - outOffset;
        float animationCenterEndY = anchorY - anchorRadius - offset - containerHeight / 2f;

        float rectWidth = lerp(anchorSize, containerWidth, showProgress);
        float rectHeight = lerp(anchorSize, containerHeight, showProgress);
        float rectCenterX = lerp(animationCenterStartX, animationCenterEndX, showProgress);
        float rectCenterY = lerp(animationCenterStartY, animationCenterEndY, showProgress);
        float rectRadius = lerp(anchorRadius, containerRadius, showProgress);
        float rectX = rectCenterX - rectWidth / 2f;
        float rectY = rectCenterY - rectHeight / 2f;
        bounds.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight);

        commonPaint.setXfermode(clearMode);
        blurringBitmapCanvas.drawRect(0, 0, anchorRadius * 2f, anchorRadius * 2f, commonPaint);
        commonPaint.setXfermode(null);
        commonPaint.setColor(Color.WHITE);

        blurringBitmapCanvas.drawRect(0, 0, anchorSize, anchorSize * (1f - showProgress), commonPaint);

        if (!isFullyShown) {
            blurGLBitmapProcessor.processBitmap(blurringBitmap, blurredBitmap, lerp(64f, 16f, showProgress));
        }

        float morphBgX = animationCenterEndX - containerWidth / 2f - bounceOffset;
        float morphBgY = animationCenterEndY - containerHeight / 2f - bounceOffset;
        float radCoef = 10f * (1f - showProgress);

        if (!isFullyShown) {
            morphGLBitmapProcessor.drawMorph(
                dst,
                rectX - morphBgX + radCoef + SIDE_OFFSET,
                rectY - morphBgY + radCoef,
                rectWidth - radCoef * 2,
                rectHeight - radCoef * 2,
                rectRadius - radCoef,
                animationCenterStartX - morphBgX,
                animationCenterStartY - morphBgY,
                anchorRadius,
                showProgress,
                color
            );
        }

        int containerAlpha = toIntAlpha(1f - clamp(hideProgress / 0.2f, 1f, 0f));
        opacityPaint.setAlpha(containerAlpha);
        canvas.drawBitmap(dst, morphBgX, morphBgY, opacityPaint);

        canvas.save();
        canvas.translate(animationCenterStartX - anchorRadius, animationCenterStartY - anchorRadius);

        if (isFullyShown) {
            commonPaint.setXfermode(clearMode);
            canvas.drawRect(0, 0, anchorSize, anchorSize, commonPaint);
        } else {
            srcRect.set(
                round(animationCenterStartX - morphBgX - anchorRadius),
                round(animationCenterStartY - morphBgY - anchorRadius),
                round(animationCenterStartX - morphBgX + anchorRadius),
                round(animationCenterStartY - morphBgY + anchorRadius)
            );
            canvas.drawBitmap(dst, srcRect, dstRect, opacityPaint);

            blurredBitmapPaint.setXfermode(srcInMode);
            canvas.drawRect(0, 0, anchorSize, anchorSize, blurredBitmapPaint);
        }

        canvas.restore();

        float center = itemCount / 2f;
        float scaledSize = itemSize * showProgress;
        float gap = (rectWidth - scaledSize * itemCount) / (itemCount + 2);
        for (int i = 0; i < itemCount; i++) {
            boolean isSelectedAndAnimated = selectedIdx == i && !hideAnimationPath.isEmpty();
            Drawable d = controller.getItemDrawable(i);
            d.setBounds(0, 0, itemSize, itemSize);
            if (isHideRunning) {
                d.setAlpha(isSelectedAndAnimated
                    ? toIntAlpha(1f - (float) Math.pow(hideProgress, 3))
                    : containerAlpha
                );
            } else {
                d.setAlpha(selectedIdx == -1 || selectedIdx == i ? 255 : 127);
            }

            float offset = i - center + 0.5f;
            float cx = rectCenterX + SIDE_OFFSET + (scaledSize + gap) * offset;
            float r = itemSize * (float) Math.pow(showProgress, Math.abs(offset) + 1f) / 2f;
            float selectionScale = selectedIdx == i && !isHideRunning
                ? 1.1f
                : isSelectedAndAnimated ? 1f - hideProgress / 2f
                : 1f;
            float scale = r / itemRadius * selectionScale;

            canvas.save();
            if (isHideRunning && isSelectedAndAnimated) {
                hideAnimationPathMeasure.getPosTan(
                    hideAnimationPathMeasure.getLength() * hideProgress,
                    hideAnimatedPos,
                    null
                );
                canvas.scale(selectionScale, selectionScale, hideAnimatedPos[0] + itemRadius, hideAnimatedPos[1] + itemRadius);
                canvas.translate(hideAnimatedPos[0], hideAnimatedPos[1]);
            } else {
                canvas.scale(scale, scale, cx, rectCenterY);
                canvas.translate(cx - itemRadius, rectCenterY - itemRadius);
            }
            d.draw(canvas);
            canvas.restore();
        }

//        Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        btnPaint.setShader(
//            new LinearGradient(
//                0f, 0f, 0f, (float) RECT_HEIGHT,
//                new int[] {
//                    0xff7644cb,
//                    0xff8849b4,
//                    0xffa751a8
//                },
//                null,
//                Shader.TileMode.CLAMP
//            )
//        );
//        btnPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
//        Path btnPath = new Path();
//        btnPath.addCircle(animationCenterStartX, animationCenterStartY, circleRadius, Path.Direction.CW);
//        canvas.drawPath(btnPath, btnPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isHideRunning) {
            return super.onTouchEvent(event);
        }

        float ex = event.getX();
        float ey = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - event.getDownTime() > longPressTimeout) {
                    int selectedIdx = getItemUnder(ex, ey);
                    if (selectedIdx != -1) {
                        if (controller != null) {
                            controller.onItemSelect(selectedIdx);
                            if (selectedIdx != this.selectedIdx) {
                                performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                );
                            }
                        }
                    }
                    this.selectedIdx = selectedIdx;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                selectedIdx = getItemUnder(ex, ey);
                if (selectedIdx != -1 && controller != null) {
                    controller.onItemClick(this, selectedIdx);
                    performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    );
                    invalidate();
                } else {
                    hide();
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int getItemUnder(float x, float y) {
        if (!bounds.contains(x, y)) {
            return -1;
        }

        float dw = bounds.width() / itemCount;
        for (int i = 0; i < itemCount; i++) {
            float l = bounds.left + dw * i;
            float r = l + dw;
            if (x >= l && x <= r) {
                return i;
            }
        }

        return -1;
    }

    private int toIntAlpha(float floatAlpha) {
        return round(255 * floatAlpha);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        morphGLBitmapProcessor.onAttach();
        blurGLBitmapProcessor.onAttach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        morphGLBitmapProcessor.onDetach();
        blurGLBitmapProcessor.onDetach();
    }


    public interface Controller {
        @NonNull
        Drawable getItemDrawable(int idx);
        void onItemSelect(int idx);
        void onItemClick(ShareView shareView, int idx);
    }

    interface OnHideListener {
        void onHide();
    }

}
