package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class MediaRecorder extends FrameLayout {

    private static final long OPEN_ANIMATION_DURATION = 350L;
    private static final long CLOSE_ANIMATION_DURATION = 220L;


    @NonNull
    private final ImageView placeholder;

    @Nullable
    private DualCameraView cameraView;

    @NonNull
    private final ImageView cameraIcon;


    @NonNull
    private final ValueAnimator openCloseAnimator = new ValueAnimator();
    private float openCloseProgress;


    @NonNull
    private final RectF previewRect = new RectF();
    private float previewClientX, previewClientY, previewAbsoluteX, previewAbsoluteY, previewSize;

    @NonNull
    private final RectF previewClipRect = new RectF();

    @NonNull
    private final float[] previewRadius = new float[4];

    @NonNull
    private final RectF fullSizeRect = new RectF();

    @NonNull
    private final RectF currentRect = new RectF();

    private final float[] currentRadii = new float[8];

    @NonNull
    private final Path clipPath = new Path();


    private boolean isOpenOrOpening;


    public MediaRecorder(@NonNull Context context) {
        super(context);

        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openCloseAnimator.addUpdateListener(animation -> {
            openCloseProgress = (float) animation.getAnimatedValue();
            invalidateInternal();
        });
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                int visibility = isOpenOrOpening
                    ? View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                    : View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                setSystemUiVisibility(visibility);
            }
        });

        placeholder = new ImageView(context);
        placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(placeholder, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        cameraIcon = new ImageView(context);
        cameraIcon.setImageResource(R.drawable.instant_camera);
        addView(cameraIcon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setConvexPath(clipPath);
                }
            });
            setClipToOutline(true);
        }
    }


    public boolean isOpenOrOpening() {
        return isOpenOrOpening;
    }

    public void setPreviewSize(float previewSize) {
        if (this.previewSize == previewSize) {
            return;
        }

        this.previewSize = previewSize;
        if (isLaidOut() && !isLayoutRequested()) {
            calculatePreviewRect();
            invalidateInternal();
        }
    }

    public void setPreviewPosition(float x, float y) {
        this.previewClientX = x;
        this.previewClientY = y;
        calculatePreviewAbsolutePosition();
        invalidateInternal();
    }

    private void calculatePreviewAbsolutePosition() {
        this.previewAbsoluteX = (-getMeasuredWidth() + previewSize) / 2f + previewClientX;
        this.previewAbsoluteY = (-getMeasuredHeight() + previewSize) / 2f + previewClientY;
    }

    public void setPreviewRadius(
        float topLeft,
        float topRight,
        float bottomRight,
        float bottomLeft
    ) {
        previewRadius[0] = topLeft;
        previewRadius[1] = topRight;
        previewRadius[2] = bottomRight;
        previewRadius[3] = bottomLeft;
        invalidateInternal();
    }

    public void setPreviewClip(
        float clipLeft,
        float clipTop,
        float clipRight,
        float clipBottom
    ) {
        previewClipRect.set(clipLeft, clipTop, clipRight, clipBottom);
        if (isLaidOut() && !isLayoutRequested()) {
            calculatePreviewRect();
            invalidateInternal();
        }
    }

    public void setPlaceholder(@Nullable Bitmap bitmap) {
        placeholder.setImageBitmap(bitmap);
    }

    public void setPlaceholder(@DrawableRes int resId) {
        placeholder.setImageResource(resId);
    }

    private void invalidateInternal() {
        lerp(previewRect, fullSizeRect, openCloseProgress, currentRect);
        currentRadii[0] = currentRadii[1] = lerp(previewRadius[0], 0f, openCloseProgress);
        currentRadii[2] = currentRadii[3] = lerp(previewRadius[1], 0f, openCloseProgress);
        currentRadii[4] = currentRadii[5] = lerp(previewRadius[2], 0f, openCloseProgress);
        currentRadii[6] = currentRadii[7] = lerp(previewRadius[3], 0f, openCloseProgress);

        clipPath.reset();
        clipPath.addRoundRect(currentRect, currentRadii, Path.Direction.CW);

        setTranslationX(lerp(previewAbsoluteX, 0f, openCloseProgress));
        setTranslationY(lerp(previewAbsoluteY, 0f, openCloseProgress));

        if (getMeasuredWidth() != 0) {
            float scale = lerp(previewSize / getMeasuredWidth(), 1f, openCloseProgress);
            setPlaceholderScale(scale);
            setTextureViewScale(scale);
        }

        float invertedProgress = 1f - openCloseProgress;
        cameraIcon.setAlpha(invertedProgress * invertedProgress);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        } else {
            invalidate();
        }
    }

    private void setPlaceholderScale(float scale) {
        placeholder.setScaleX(scale);
        placeholder.setScaleY(scale);
    }

    private void setTextureViewScale(float scale) {
        if (cameraView != null) {
            cameraView.getTextureView().setScaleX(scale);
            cameraView.getTextureView().setScaleY(scale);
        }
    }

    public void startCamera() {
        if (cameraView != null) {
            return;
        }

        placeholder.setVisibility(View.VISIBLE);

        cameraView = new DualCameraView(getContext(), false, false);
        cameraView.setAlpha(0f);
        cameraView.setDelegate(() -> {
            cameraView.animate()
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .alpha(1f)
                .withEndAction(() -> {
                    if (cameraView != null) {
                        placeholder.setVisibility(View.GONE);
                    }
                });
        });
        cameraView.setContentDescription(LocaleController.getString(R.string.AccDescrInstantCamera));
        addView(cameraView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void open() {
        if (fullSizeRect.isEmpty() || isOpenOrOpening) {
            return;
        }

        openCloseAnimator.cancel();
        openCloseAnimator.setFloatValues(openCloseProgress, 1f);
        openCloseAnimator.setDuration(OPEN_ANIMATION_DURATION);
        openCloseAnimator.start();
        isOpenOrOpening = true;
    }

    public void close() {
        if (!isOpenOrOpening) {
            return;
        }

        openCloseAnimator.cancel();
        openCloseAnimator.setFloatValues(openCloseProgress, 0f);
        openCloseAnimator.setDuration(CLOSE_ANIMATION_DURATION);
        openCloseAnimator.start();
        isOpenOrOpening = false;
    }

    public void destroyCamera(boolean async) {
        if (cameraView != null) {
            cameraView.setDelegate(null);
            cameraView.destroy(async, null);
            removeView(cameraView);
            cameraView = null;
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    @Nullable
    public Bitmap getCameraBitmap() {
        if (cameraView != null) {
            return cameraView.getTextureView().getBitmap();
        }
        return null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        fullSizeRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        calculatePreviewRect();
        invalidateInternal();
    }

    private void calculatePreviewRect() {
        float left = (getMeasuredWidth() - previewSize) / 2f;
        float top = (getMeasuredHeight() - previewSize) / 2f;
        float right = left + previewSize;
        float bottom = top + previewSize;

        float clippedTop = clamp(top + previewClipRect.top, bottom, top);
        float clippedBottom = clamp(bottom - previewClipRect.bottom, bottom, clippedTop);

        previewRect.set(
            left + previewClipRect.left,
            clippedTop,
            right - previewClipRect.right,
            clippedBottom
        );
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isOpenOrOpening) {
            return super.dispatchTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            canvas.save();
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            drawInternal(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
            drawInternal(canvas);
        }
    }

    private void drawInternal(@NonNull Canvas canvas) {

    }

}
