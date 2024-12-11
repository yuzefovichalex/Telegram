package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
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
    private static final int MIN_FLING_VELOCITY = 2000;
    private static final float CLOSE_ON_DRAG_ANCHOR_PERCENTAGE = 0.15f;
    private static final long MIN_DRAG_RESET_ANIMATION_DURATION = 50L;


    @NonNull
    private final ImageView placeholder;

    @Nullable
    private DualCameraView cameraView;

    @NonNull
    private final ImageView cameraIcon;


    @NonNull
    private final ValueAnimator openCloseAnimator = new ValueAnimator();
    private boolean isOpenCloseAnimationRunning;
    private float openCloseProgress;
    private boolean isOpenOrOpening;


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


    private final int touchSlop;
    private final int dragTouchSlop;
    private final int maxDragRadius = dp(32f);
    private float dragStartY;
    private float dragProgress;
    private int lastDragPointerId;
    private boolean maybeStartDrag;

    @Nullable
    private VelocityTracker velocityTracker;

    @NonNull
    private final ValueAnimator resetDragAnimator = new ValueAnimator();


    public MediaRecorder(@NonNull Context context) {
        super(context);

        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openCloseAnimator.addUpdateListener(animation -> {
            openCloseProgress = (float) animation.getAnimatedValue();
            invalidateInternal();
        });
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                isOpenCloseAnimationRunning = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isOpenCloseAnimationRunning = false;
                dragProgress = 0f;
                int visibility = isOpenOrOpening
                    ? View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                    : View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                setSystemUiVisibility(visibility);
            }
        });

        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        touchSlop = viewConfiguration.getScaledTouchSlop();
        dragTouchSlop = viewConfiguration.getScaledPagingTouchSlop();

        resetDragAnimator.addUpdateListener(animation -> setDragProgress((float) animation.getAnimatedValue()));

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

    private float getDragRadius() {
        return lerp(0f, maxDragRadius, dragProgress);
    }

    private float getDragScale() {
        return lerp(1f, .8f, dragProgress);
    }

    private float getDragTranslationY() {
        return dragProgress * getMeasuredHeight();
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
        float outerScale = lerp(1f, getDragScale(), openCloseProgress);
        setScaleX(outerScale);
        setScaleY(outerScale);
        setTranslationX(lerp(previewAbsoluteX, 0f, openCloseProgress));
        setTranslationY(lerp(previewAbsoluteY, getDragTranslationY(), openCloseProgress));

        if (getMeasuredWidth() != 0) {
            float innerScale = lerp(previewSize / getMeasuredWidth(), 1f, openCloseProgress);
            setPlaceholderScale(innerScale);
            setTextureViewScale(innerScale);
        }

        float invertedProgress = 1f - openCloseProgress;
        cameraIcon.setAlpha(invertedProgress * invertedProgress);

        lerp(previewRect, fullSizeRect, openCloseProgress, currentRect);

        float dragRadius = getDragRadius();
        setClipRadius(
            lerp(previewRadius[0], dragRadius, openCloseProgress),
            lerp(previewRadius[1], dragRadius, openCloseProgress),
            lerp(previewRadius[2], dragRadius, openCloseProgress),
            lerp(previewRadius[3], dragRadius, openCloseProgress)
        );
        invalidateClipPath();
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

    private void setDragProgress(float dragProgress) {
        if (isOpenCloseAnimationRunning) {
            return;
        }

        this.dragProgress = dragProgress;

        float scale = getDragScale();
        setScaleX(scale);
        setScaleY(scale);
        setTranslationY(getDragTranslationY());

        float dragRadius = getDragRadius();
        setClipRadius(dragRadius, dragRadius, dragRadius, dragRadius);
        invalidateClipPath();
    }

    private void resetDragProgress() {
        if (isOpenCloseAnimationRunning || dragProgress == 0f) {
            return;
        }

        resetDragAnimator.cancel();

        float remainingHeight = getMeasuredHeight() * (1f - dragProgress);
        long duration = Math.max(
            (long) (200f / getMeasuredWidth() * remainingHeight),
            MIN_DRAG_RESET_ANIMATION_DURATION
        );
        resetDragAnimator.setFloatValues(dragProgress, 0f);
        resetDragAnimator.setDuration(duration);
        resetDragAnimator.start();

        maybeStartDrag = false;
    }

    private void setClipRadius(
        float topLeft,
        float topRight,
        float bottomRight,
        float bottomLeft
    ) {
        currentRadii[0] = currentRadii[1] = topLeft;
        currentRadii[2] = currentRadii[3] = topRight;
        currentRadii[4] = currentRadii[5] = bottomRight;
        currentRadii[6] = currentRadii[7] = bottomLeft;
    }

    private void invalidateClipPath() {
        clipPath.reset();
        clipPath.addRoundRect(currentRect, currentRadii, Path.Direction.CW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        } else {
            invalidate();
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

    public void openCamera() {
        if (fullSizeRect.isEmpty() || isOpenOrOpening) {
            return;
        }

        openCloseAnimator.cancel();
        openCloseAnimator.setFloatValues(openCloseProgress, 1f);
        openCloseAnimator.setDuration(OPEN_ANIMATION_DURATION);
        openCloseAnimator.start();
        isOpenOrOpening = true;
    }

    public void closeCamera() {
        if (!isOpenOrOpening || isOpenCloseAnimationRunning) {
            return;
        }

        resetDragAnimator.cancel();
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
        if (isOpenOrOpening && !isOpenCloseAnimationRunning) {
            return super.dispatchTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        if (resetDragAnimator.isRunning()) {
            return false;
        }

        int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastDragPointerId = ev.getPointerId(0);
                dragStartY = ev.getRawY();
                maybeStartDrag = true;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - dragStartY;
                boolean isDragging = Math.abs(dy) > touchSlop &&
                    lastDragPointerId == ev.getPointerId(0) &&
                    maybeStartDrag;
                if (!isDragging && velocityTracker != null) {
                    velocityTracker.clear();
                }
                return isDragging;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetDragProgress();
        }

        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (resetDragAnimator.isRunning()) {
            return false;
        }

        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerId(0) != lastDragPointerId) {
                    return false;
                }

                float distance = event.getRawY() - dragStartY - dragTouchSlop;
                dragProgress = clamp(
                    distance / getMeasuredHeight(),
                    1f,
                    0f
                );
                setDragProgress(dragProgress);

                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000);

                float velocityY = velocityTracker.getYVelocity();
                if (dragProgress > CLOSE_ON_DRAG_ANCHOR_PERCENTAGE ||
                    velocityY > MIN_FLING_VELOCITY
                ) {
                    closeCamera();
                } else {
                    resetDragProgress();
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                resetDragProgress();
                return true;
        }

        return false;
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
