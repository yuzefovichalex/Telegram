package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
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
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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

    @NonNull
    private final ContentView contentView;

    @NonNull
    private final GestureDetector gestureDetector;

    @NonNull
    private final ScaleGestureDetector scaleGestureDetector;


    public MediaRecorder(@NonNull Context context) {
        super(context);

        contentView = new ContentView(context);
        addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                return contentView.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                return contentView.onDoubleTap(e);
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                return contentView.onScroll(e1, e2, distanceX, distanceY);
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                return contentView.onFling(e1, e2, velocityX, velocityY);
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                return false;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                return false;
            }
        });
    }


    public boolean isOpenOrOpening() {
        return contentView.isOpenOrOpening;
    }

    public void setPreviewSize(float previewSize) {
        contentView.setPreviewSize(previewSize);
    }

    public void setPreviewPosition(float x, float y) {
        contentView.setPreviewPosition(x, y);
    }

    public void setPreviewRadius(
        float topLeft,
        float topRight,
        float bottomRight,
        float bottomLeft
    ) {
        contentView.setPreviewRadius(topLeft, topRight, bottomRight, bottomLeft);
    }

    public void setPreviewClip(
        float clipLeft,
        float clipTop,
        float clipRight,
        float clipBottom
    ) {
        contentView.setPreviewClip(clipLeft, clipTop, clipRight, clipBottom);
    }

    public void setPlaceholder(@Nullable Bitmap bitmap) {
        contentView.setPlaceholder(bitmap);
    }

    public void setPlaceholder(@DrawableRes int resId) {
        contentView.setPlaceholder(resId);
    }

    public void startCamera() {
        contentView.startCamera();
    }

    public void openCamera() {
        contentView.openCamera();
    }

    public void closeCamera() {
        contentView.closeCamera();
    }

    public void destroyCamera(boolean async) {
        contentView.destroyCamera(async);
    }

    @Nullable
    public Bitmap getCameraBitmap() {
        return contentView.getCameraBitmap();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (contentView.isOpenOrOpening && !contentView.isOpenCloseAnimationRunning) {
            if (!contentView.isAtDual(ev)) {
                scaleGestureDetector.onTouchEvent(ev);
                gestureDetector.onTouchEvent(ev);
            }
            return super.dispatchTouchEvent(ev);
        } else {
            return false;
        }
    }


    private static class ContentView extends FrameLayout {

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


        private final int maxDragRadius = dp(32f);
        private float dragProgress;

        @NonNull
        private final ValueAnimator resetDragAnimator = new ValueAnimator();


        private ContentView(@NonNull Context context) {
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


        private float getDragRadius() {
            return lerp(0f, maxDragRadius, dragProgress);
        }

        private float getDragScale() {
            return lerp(1f, .8f, dragProgress);
        }

        private float getDragTranslationY() {
            return dragProgress * getMeasuredHeight();
        }

        private void setPreviewSize(float previewSize) {
            if (this.previewSize == previewSize) {
                return;
            }

            this.previewSize = previewSize;
            if (isLaidOut() && !isLayoutRequested()) {
                calculatePreviewRect();
                invalidateInternal();
            }
        }

        private void setPreviewPosition(float x, float y) {
            this.previewClientX = x;
            this.previewClientY = y;
            calculatePreviewAbsolutePosition();
            invalidateInternal();
        }

        private void calculatePreviewAbsolutePosition() {
            this.previewAbsoluteX = (-getMeasuredWidth() + previewSize) / 2f + previewClientX;
            this.previewAbsoluteY = (-getMeasuredHeight() + previewSize) / 2f + previewClientY;
        }

        private void setPreviewRadius(
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

        private void setPreviewClip(
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

        private void setPlaceholder(@Nullable Bitmap bitmap) {
            placeholder.setImageBitmap(bitmap);
        }

        private void setPlaceholder(@DrawableRes int resId) {
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

            this.dragProgress = clamp(dragProgress, 1f, 0f);

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

        private void startCamera() {
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

        private void openCamera() {
            if (fullSizeRect.isEmpty() || isOpenOrOpening) {
                return;
            }

            openCloseAnimator.cancel();
            openCloseAnimator.setFloatValues(openCloseProgress, 1f);
            openCloseAnimator.setDuration(OPEN_ANIMATION_DURATION);
            openCloseAnimator.start();
            isOpenOrOpening = true;
        }

        private void closeCamera() {
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

        private void destroyCamera(boolean async) {
            if (cameraView != null) {
                cameraView.setDelegate(null);
                cameraView.destroy(async, null);
                removeView(cameraView);
                cameraView = null;
                placeholder.setVisibility(View.VISIBLE);
            }
        }

        @Nullable
        private Bitmap getCameraBitmap() {
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

        private boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (cameraView != null && !isAtDual(e)) {
                cameraView.focusToPoint((int) e.getRawX(), (int) e.getRawY());
                return true;
            }
            return false;
        }

        private boolean onDoubleTap(@NonNull MotionEvent e) {
            if (cameraView != null && !isAtDual(e)) {
                cameraView.switchCamera();
                return true;
            }
            return false;
        }

        private boolean onScroll(
            @Nullable MotionEvent e1,
            @NonNull MotionEvent e2,
            float distanceX,
            float distanceY
        ) {
            if (distanceY != 0) {
                float updatedDragProgress = dragProgress - distanceY / getMeasuredHeight();
                setDragProgress(updatedDragProgress);
                return true;
            }

            return false;
        }

        private boolean onFling(
            @Nullable MotionEvent e1,
            @NonNull MotionEvent e2,
            float velocityX,
            float velocityY
        ) {
            if (dragProgress > CLOSE_ON_DRAG_ANCHOR_PERCENTAGE || velocityY > MIN_FLING_VELOCITY) {
                closeCamera();
                return true;
            }
            return false;
        }

        private boolean isAtDual(@NonNull MotionEvent event) {
            return cameraView != null && cameraView.isAtDual(event.getX(), event.getY());
        }

    }

}
