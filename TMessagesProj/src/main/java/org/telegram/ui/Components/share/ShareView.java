package org.telegram.ui.Components.share;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;
import static java.lang.Math.round;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

public class ShareView extends View {

    private static final int DEFAULT_ITEM_SIZE = 115;
    private static final int DEFAULT_ITEM_COUNT = 5;
    private static final int DEFAULT_CONTAINER_PADDING = 16;
    private static final int DEFAULT_CONTAINER_OFFSET = 25;
    private static final int DEFAULT_SIDE_OFFSET = 15;
    private static final int DEFAULT_SHADOW_COLOR = 0xCCCCCC;
    private static final int SHOW_ANIMATION_DURATION = 525;
    private static final int DEFAULT_HIDE_ANIMATION_DURATION = 250;

    @NonNull
    private final Paint commonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Paint opacityPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    @NonNull
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Paint chatBgPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground);

    @NonNull
    private final ValueAnimator showAnimator = ValueAnimator.ofFloat(0f, 1f);

    private float showProgress;
    private boolean isFullyShown;

    @NonNull
    private final OpenGLBitmapProcessor morphGLBitmapProcessor;
    @NonNull
    private final OpenGLBitmapProcessor blurGLBitmapProcessor;

    private Bitmap blurringBitmap;
    private Canvas blurringBitmapCanvas;
    private Bitmap blurredBitmap;
    private final Paint blurredBitmapPaint;

    private Bitmap morphBitmap;
    @NonNull
    private final Rect srcRect = new Rect();
    @NonNull
    private final RectF dstRect = new RectF();

    @NonNull
    private final PorterDuffXfermode srcInMode = new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY);
    @NonNull
    private final PorterDuffXfermode clearMode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    private int itemSize = DEFAULT_ITEM_SIZE;
    private float itemRadius = itemSize / 2f;
    private int itemCount = DEFAULT_ITEM_COUNT;
    private int containerPadding = DEFAULT_CONTAINER_PADDING;
    // Offset between anchor and container or container and label
    private int elementsOffset = DEFAULT_CONTAINER_OFFSET;

    private int containerWidth;
    private int containerHeight;
    private int bounceOffset;
    @NonNull
    private final RectF containerBounds = new RectF();
    @NonNull
    private final RectF tempRect = new RectF();

    private float anchorX = -1;
    private float anchorY = -1;
    private int anchorSize = 0;

    @Nullable
    private Controller controller;

    private boolean needsItemCountEnsure;

    private final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
    private int selectedIdx = -1;

    @NonNull
    private final Path hideAnimationPath = new Path();
    @NonNull
    private final PathMeasure hideAnimationPathMeasure = new PathMeasure();
    @NonNull
    private final float[] hideAnimatedPos = new float[2];

    @NonNull
    private final ValueAnimator hideAnimator = ValueAnimator.ofFloat(0f, 1f);
    private float hideProgress = 0f;
    private boolean isHideRunning;

    @Nullable
    private OnHideListener onHideListener;

    private int color = 0xFFFFFF;

    private boolean isLongPressed;

    @NonNull
    private Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private Paint labelBgBlurPaint;
    @NonNull
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private StaticLayout labelLayout;
    private int labelHorPadding = dp(12);
    private int labelVertPadding = dp(4);
    private int labelBlurredBackgroundWidth;
    private int labelBlurredBackgroundHeight;
    @Nullable
    private BitmapShader labelBlurBitmapShader;
    @Nullable
    private Matrix labelBlurBitmapMatrix;

    @Nullable
    private Runnable pendingLongPressRunnable;


    public ShareView(@NonNull Context context) {
        this(context, null);
    }

    public ShareView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        shadowPaint.setColor(DEFAULT_SHADOW_COLOR);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(1f);
        shadowPaint.setMaskFilter(new BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL));

        morphGLBitmapProcessor = new OpenGLBitmapProcessor(context);
        morphGLBitmapProcessor.onAttach();
        blurGLBitmapProcessor = new OpenGLBitmapProcessor(context);
        blurGLBitmapProcessor.onAttach();

        blurredBitmapPaint = new Paint();
        blurredBitmapPaint.setAntiAlias(true);
        blurredBitmapPaint.setDither(true);

        labelBgPaint.setColor(Color.BLACK);
        labelBgPaint.setAlpha(127);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(14));
        labelPaint.setTypeface(AndroidUtilities.bold());

        showAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        showAnimator.setDuration(SHOW_ANIMATION_DURATION);
        showAnimator.addUpdateListener((animation -> {
            isFullyShown = false;
            showProgress = (float) animation.getAnimatedValue();
            invalidate();
        }));
        showAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFullyShown = true;
                prepareLabelBlur();
            }
        });

        hideAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        hideAnimator.setDuration(DEFAULT_HIDE_ANIMATION_DURATION);
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
                isLongPressed = false;

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

    public void setContainerPadding(int containerPadding) {
        this.containerPadding = containerPadding;
        invalidateParams();
    }

    public void setElementsOffset(int elementsOffset) {
        this.elementsOffset = elementsOffset;
        invalidateParams();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setShadowColor(int shadowColor) {
        shadowPaint.setColor(shadowColor);
        invalidate();
    }

    public void setLabelPadding(int horizontal, int vertical) {
        labelHorPadding = horizontal;
        labelVertPadding = vertical;
        invalidate();
    }

    private void invalidateParams() {
        if (getMeasuredWidth() == 0) {
            needsItemCountEnsure = true;
            return;
        }

        int fullWidth;
        while (true) {
            containerWidth = itemSize * itemCount + containerPadding * (itemCount + 1);
            fullWidth = containerWidth + DEFAULT_SIDE_OFFSET * 2;
            if (fullWidth <= getMeasuredWidth()) {
                break;
            } else {
                itemCount--;
            }
        }

        containerHeight = itemSize + containerPadding * 2;
        int fullHeight = containerHeight + elementsOffset + anchorSize + DEFAULT_SIDE_OFFSET;
        itemRadius = itemSize / 2f;
        bounceOffset = containerHeight / 2;

        if (morphBitmap != null) {
            morphBitmap.recycle();
        }
        morphBitmap = Bitmap.createBitmap(
            fullWidth + bounceOffset * 2,
            fullHeight + bounceOffset,
            Bitmap.Config.ARGB_8888
        );

        needsItemCountEnsure = false;

        morphGLBitmapProcessor.initSurface(fullWidth + bounceOffset * 2, fullHeight + bounceOffset);
    }

    public void setAnchor(float x, float y, int size) {
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
        blurredBitmapPaint.setColor(Color.BLACK);
        blurredBitmapPaint.setShader(
            new BitmapShader(blurredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
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

    private void prepareLabelBlur() {
        labelBlurredBackgroundWidth = 0;
        labelBlurredBackgroundHeight = 0;
        labelBlurBitmapShader = null;
        labelBlurBitmapMatrix = null;

        if (labelBgBlurPaint != null) {
            labelBgBlurPaint.setShader(null);
            labelBgBlurPaint.setColorFilter(null);
        }

        labelBgPaint = chatBgPaint;
        if (LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
                labelBlurredBackgroundWidth = bitmap.getWidth();
                labelBlurredBackgroundHeight = bitmap.getHeight();
                labelBlurBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                labelBlurBitmapMatrix = new Matrix();

                ColorMatrix colorMatrix = new ColorMatrix();
                colorMatrix.setSaturation(1.5f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? +.12f : -.08f);
                if (labelBgBlurPaint == null) {
                    labelBgBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                labelBgBlurPaint.setShader(labelBlurBitmapShader);
                labelBgBlurPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                labelBgPaint = labelBgBlurPaint;
            }, 12f);
        }
    }

    public void hide(float x, float y) {
        float startX = containerBounds.left + containerPadding * (selectedIdx + 1) + itemSize * selectedIdx;
        float startY = containerBounds.top + containerPadding;
        hideAnimationPath.reset();
        hideAnimationPath.moveTo(startX, startY);
        hideAnimationPath.quadTo(x, startY - containerHeight * 2, x, y);
        hideAnimationPathMeasure.setPath(hideAnimationPath, false);

        hide();
    }

    public void hide() {
        if (hideAnimator.isRunning()) {
            return;
        }

        showAnimator.cancel();
        hideAnimator.cancel();

        float speed = 2000f / 1000f; // 2000px per 1000ms
        long duration = (long) (hideAnimationPathMeasure.getLength() / speed);
        if (duration == 0) {
            duration = DEFAULT_HIDE_ANIMATION_DURATION;
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

        float halfContainerWidthWithSideOffset = containerWidth / 2f + DEFAULT_SIDE_OFFSET;
        float anchorRadius = anchorSize / 2f;
        float containerRadius = containerHeight / 2f;
        float outOffset = 0f;

        float animationCenterStartX = anchorX;
        float animationCenterStartY = anchorY;
        float intrinsicCenterEndX = anchorX + anchorRadius + containerRadius - halfContainerWidthWithSideOffset;

        if (intrinsicCenterEndX - halfContainerWidthWithSideOffset < 0) {
            outOffset = intrinsicCenterEndX - halfContainerWidthWithSideOffset;
        } else if (intrinsicCenterEndX + halfContainerWidthWithSideOffset > getMeasuredWidth()) {
            outOffset = intrinsicCenterEndX - (getMeasuredWidth() - halfContainerWidthWithSideOffset);
        }
        float animationCenterEndX = intrinsicCenterEndX - outOffset;
        float animationCenterEndY = anchorY - anchorRadius - elementsOffset - containerHeight / 2f;

        float rectWidth = lerp(anchorSize, containerWidth, showProgress);
        float rectHeight = lerp(anchorSize, containerHeight, showProgress);
        float rectCenterX = lerp(animationCenterStartX, animationCenterEndX, showProgress);
        float rectCenterY = lerp(animationCenterStartY, animationCenterEndY, showProgress);
        float rectRadius = lerp(anchorRadius, containerRadius, showProgress);
        float rectX = rectCenterX - rectWidth / 2f;
        float rectY = rectCenterY - rectHeight / 2f;
        containerBounds.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight);

        int containerAlpha = hideAnimationPath.isEmpty()
            ? toIntAlpha(1f - hideProgress)
            : toIntAlpha(1f - clamp(hideProgress / 0.2f, 1f, 0f));
        int shadowAlpha = isHideRunning
            ? containerAlpha
            : toIntAlpha(clamp((showProgress - 0.8f) / 0.2f, 1f, 0f));

        // Special coef to not increase the morph figure when rect is fully under circle
        float radCoef = 10f * (1f - showProgress);

        tempRect.set(
            containerBounds.left + radCoef, containerBounds.top + radCoef,
            containerBounds.right - radCoef, containerBounds.bottom - radCoef
        );
        shadowPaint.setAlpha(shadowAlpha);
        canvas.drawRoundRect(tempRect, rectRadius, rectRadius, shadowPaint);

        commonPaint.setXfermode(clearMode);
        blurringBitmapCanvas.drawRect(0, 0, anchorRadius * 2f, anchorRadius * 2f, commonPaint);
        commonPaint.setXfermode(null);
        commonPaint.setColor(Color.WHITE);

        blurringBitmapCanvas.drawRect(0, 0, anchorSize, anchorSize * (1f - showProgress), commonPaint);

        if (!isFullyShown) {
            blurGLBitmapProcessor.processBitmap(blurringBitmap, blurredBitmap, lerp(20f, 12f, showProgress));
        }

        float morphBgX = animationCenterEndX - halfContainerWidthWithSideOffset - bounceOffset;
        float morphBgY = animationCenterEndY - containerHeight / 2f - bounceOffset;

        if (!isFullyShown) {
            morphGLBitmapProcessor.drawMorph(
                morphBitmap,
                rectX - morphBgX + radCoef,
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

        opacityPaint.setAlpha(containerAlpha);
        canvas.drawBitmap(morphBitmap, morphBgX, morphBgY, opacityPaint);

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
            canvas.drawBitmap(morphBitmap, srcRect, dstRect, opacityPaint);

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
                d.setAlpha(
                    isSelectedAndAnimated
                        ? toIntAlpha(1f - clamp((hideProgress - 0.8f) / 0.2f, 1f, 0f))
                        : containerAlpha
                );
            } else {
                d.setAlpha(selectedIdx == -1 || selectedIdx == i ? 255 : 127);
            }

            float offset = i - center + 0.5f;
            float cx = rectCenterX + (scaledSize + gap) * offset;
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

        if (isFullyShown && isLongPressed && selectedIdx != -1 && labelLayout != null) {
            int labelWidth = labelLayout.getWidth() + labelHorPadding * 2;
            int labelHalfWidth = labelWidth / 2;
            int labelHeight = labelLayout.getHeight() + labelVertPadding * 2;
            int labelHalfHeight = labelHeight / 2;

            float offset = selectedIdx - center + 0.5f;
            float intrinsicLabelCenterX = rectCenterX + (itemSize + containerPadding) * offset;
            float labelOffset = 0f;
            if (intrinsicLabelCenterX - labelHalfWidth < rectX) {
                labelOffset = labelHalfWidth - (intrinsicLabelCenterX - rectX);
            } else if (rectX + rectWidth - intrinsicLabelCenterX < labelHalfWidth) {
                labelOffset = (rectX + rectWidth - intrinsicLabelCenterX) - labelHalfWidth;
            }

            float labelStartX = intrinsicLabelCenterX + labelOffset - labelHalfWidth;
            float labelStartY = rectY - this.elementsOffset - labelHalfHeight - labelHalfHeight;

            tempRect.set(
                labelStartX, labelStartY,
                labelStartX + labelWidth, labelStartY + labelHeight
            );

            updateLabelBlurPositionIfNeeded(tempRect);

            int oldAlpha = chatBgPaint.getAlpha();
            labelBgPaint.setAlpha(containerAlpha);
            canvas.drawRoundRect(tempRect, labelHalfHeight, labelHalfHeight, labelBgPaint);
            if (labelBgPaint == chatBgPaint) {
                chatBgPaint.setAlpha(oldAlpha);
            }

            labelPaint.setAlpha(containerAlpha);
            canvas.drawText(
                labelLayout.getText().toString(),
                labelStartX + labelHorPadding,
                labelStartY + labelHeight - labelVertPadding * 2,
                labelPaint
            );
        }

        if (!isFullyShown && controller != null) {
            canvas.save();
            canvas.translate(
                animationCenterStartX - anchorRadius,
                animationCenterStartY - anchorRadius
            );
            controller.drawAnchorOverlay(canvas);
            canvas.restore();
        }
    }

    private void updateLabelBlurPositionIfNeeded(@NonNull RectF labelBounds) {
        if (labelBlurBitmapShader == null || labelBlurBitmapMatrix == null) {
            return;
        }

        labelBlurBitmapMatrix.reset();
        labelBlurBitmapMatrix.postScale(
            AndroidUtilities.displaySize.x / (float) labelBlurredBackgroundWidth,
            (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) / (float) labelBlurredBackgroundHeight
        );
        labelBlurBitmapMatrix.postTranslate(0, labelBounds.height());
        labelBlurBitmapShader.setLocalMatrix(labelBlurBitmapMatrix);
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
                int targetIdx = getItemUnder(ex, ey);
                if (targetIdx != -1) {
                    startPendingLongPressRunnable(targetIdx);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - event.getDownTime() > longPressTimeout) {
                    cancelPendingLongPressRunnable();
                    int selectedIdx = getItemUnder(ex, ey);
                    selectItemOnLongPress(selectedIdx);
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelPendingLongPressRunnable();
                selectedIdx = getItemUnder(ex, ey);
                if (selectedIdx != -1 && controller != null) {
                    controller.onItemClick(this, selectedIdx);
                    invalidate();
                } else {
                    hide();
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int getItemUnder(float x, float y) {
        if (!containerBounds.contains(x, y)) {
            return -1;
        }

        float dw = containerBounds.width() / itemCount;
        for (int i = 0; i < itemCount; i++) {
            float l = containerBounds.left + dw * i;
            float r = l + dw;
            if (x >= l && x <= r) {
                return i;
            }
        }

        return -1;
    }

    private int toIntAlpha(float floatAlpha) {
        return (int) (255 * clamp(floatAlpha, 1f, 0f));
    }

    private void makeLabelLayout(@NonNull String text) {
        CharSequence labelText = TextUtils.ellipsize(text, labelPaint, containerWidth * 0.7f, TextUtils.TruncateAt.END);
        float textWidth = labelPaint.measureText(labelText.toString());
        labelLayout = new StaticLayout(
            labelText,
            labelPaint,
            (int) textWidth,
            Layout.Alignment.ALIGN_CENTER,
            1f,
            0f,
            false
        );
    }

    private void startPendingLongPressRunnable(int idx) {
        cancelPendingLongPressRunnable();

        pendingLongPressRunnable = () -> {
            selectItemOnLongPress(idx);
            pendingLongPressRunnable = null;
        };
        AndroidUtilities.runOnUIThread(pendingLongPressRunnable, longPressTimeout);
    }

    private void cancelPendingLongPressRunnable() {
        if (pendingLongPressRunnable == null) {
            return;
        }

        AndroidUtilities.cancelRunOnUIThread(pendingLongPressRunnable);
        pendingLongPressRunnable = null;
    }

    private void selectItemOnLongPress(int selectedIdx) {
        boolean invalidate = selectedIdx != this.selectedIdx;
        if (controller != null && selectedIdx != this.selectedIdx && selectedIdx != -1) {
            controller.onItemSelect(selectedIdx);
            performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            );
            String label = controller.getItemLabel(selectedIdx);
            makeLabelLayout(label);
        }
        this.selectedIdx = selectedIdx;
        isLongPressed = true;

        if (invalidate) {
            invalidate();
        }
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
        void onAttachedToWindow(@NonNull ShareView shareView);
        void drawAnchorOverlay(@NonNull Canvas canvas);
        @NonNull
        String getItemLabel(int idx);
        @NonNull
        Drawable getItemDrawable(int idx);
        void onItemSelect(int idx);
        void onItemClick(@NonNull ShareView shareView, int idx);
        void onDetachFromWindow(@NonNull ShareView shareView);
    }

    interface OnHideListener {
        void onHide();
    }

}
