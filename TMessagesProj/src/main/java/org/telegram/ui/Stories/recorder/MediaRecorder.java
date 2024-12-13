package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.Utilities.clamp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.hardware.Camera;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class MediaRecorder extends FrameLayout {

    @NonNull
    private final ContentView contentView;

    @NonNull
    private final GestureDetector gestureDetector;

    @NonNull
    private final ScaleGestureDetector scaleGestureDetector;

    private boolean isScaling;
    private boolean isDragging;


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
                if (!isScaling && contentView.onDrag(distanceY)) {
                    isDragging = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                return contentView.onFling(velocityY);
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (!isDragging) {
                    isScaling = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                return contentView.onScale(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                isScaling = false;
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
        if (contentView.isOpen()) {
            if (!isScaling && !isDragging && isNotOnControls(ev)) {
                if (ev.getPointerCount() == 1) {
                    gestureDetector.onTouchEvent(ev);
                } else {
                    scaleGestureDetector.onTouchEvent(ev);
                }
            } else if (isScaling) {
                scaleGestureDetector.onTouchEvent(ev);
            } else if (isDragging) {
                gestureDetector.onTouchEvent(ev);
            }

            if (isDragging &&
                (ev.getActionMasked() == MotionEvent.ACTION_UP ||
                    ev.getActionMasked() == MotionEvent.ACTION_CANCEL)
            ) {
                isDragging = false;
                contentView.resetDragProgress();
            }

            return super.dispatchTouchEvent(ev);
        } else {
            return false;
        }
    }

    private boolean isNotOnControls(@NonNull MotionEvent event) {
        boolean isNotOnStatusBar = event.getY() > AndroidUtilities.statusBarHeight;
        boolean isOnLeftControls = event.getY() < AndroidUtilities.statusBarHeight + dp(56) &&
            event.getX() < dp(56);
        boolean isOnRightControls = event.getY() < AndroidUtilities.statusBarHeight + dp(56) &&
            event.getX() > getMeasuredWidth() - dp(112);
        return contentView.isNotAtDual(event) && isNotOnStatusBar && !isOnLeftControls && !isOnRightControls;
    }


    private static class ContentView extends FrameLayout {

        private static final long OPEN_ANIMATION_DURATION = 350L;
        private static final long CLOSE_ANIMATION_DURATION = 220L;
        private static final int MIN_FLING_VELOCITY = 2000;
        private static final float CLOSE_ON_DRAG_ANCHOR_PERCENTAGE = 0.15f;
        private static final long MIN_DRAG_RESET_ANIMATION_DURATION = 50L;
        private static final int SELECTOR_BACKGROUND_COLOR = 0x20FFFFFF;


        @NonNull
        private final ImageView placeholder;

        @Nullable
        private DualCameraView cameraView;

        @NonNull
        private final ImageView cameraIcon;

        @NonNull
        private final FlashViews.ImageViewInvertable backButton;

        @NonNull
        private final ToggleButton2 flashButton;

        @NonNull
        private final ToggleButton dualButton;


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


        @Nullable
        private Matrix dualCameraMatrix;
        private float cameraZoom;
        private int frontCameraFlashMode = -1;
        private ArrayList<String> frontCameraFlashModes = new ArrayList<>();


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
                    if (isOpenOrOpening && cameraView != null && cameraView.isDual()) {
                        if (dualCameraMatrix == null) {
                            dualCameraMatrix = new Matrix();
                        }
                        dualCameraMatrix.set(cameraView.getDualPosition());
                    }
                    isOpenOrOpening = !isOpenOrOpening;
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

            backButton = new FlashViews.ImageViewInvertable(context);
            backButton.setContentDescription(getString(R.string.AccDescrGoBack));
            backButton.setScaleType(ImageView.ScaleType.CENTER);
            backButton.setImageResource(R.drawable.msg_photo_back);
            backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            backButton.setBackground(Theme.createSelectorDrawable(SELECTOR_BACKGROUND_COLOR));
            backButton.setOnClickListener(v -> {
                if (isOpen()) {
                    closeCamera();
                }
            });
            addView(backButton, LayoutHelper.createFrame(56, 56));

            flashButton = new ToggleButton2(context);
            flashButton.setBackground(Theme.createSelectorDrawable(SELECTOR_BACKGROUND_COLOR));
            flashButton.setOnClickListener(v -> toggleFlashMode());
            addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT));

            dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
            dualButton.setOnClickListener(v -> {
                if (cameraView == null) {
                    return;
                }
                cameraView.toggleDual();
                dualButton.setValue(cameraView.isDual());
            });
            final boolean dualCameraAvailable = DualCameraView.dualAvailableStatic(context);
            dualButton.setVisibility(dualCameraAvailable ? View.VISIBLE : View.GONE);
            dualButton.setAlpha(dualCameraAvailable ? 1f : 0f);
            //flashViews.add(dualButton);
            addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT));

            checkActionButtonsPosition();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                applyWindowInsets(WindowInsetsCompat.CONSUMED);
                ViewCompat.setOnApplyWindowInsetsListener(this, new androidx.core.view.OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(
                        @NonNull View v,
                        @NonNull WindowInsetsCompat insets
                    ) {
                        applyWindowInsets(insets);
                        return insets;
                    }
                });

                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setConvexPath(clipPath);
                    }
                });
                setClipToOutline(true);
            }
        }


        private boolean isOpen() {
            return isOpenOrOpening && !isOpenCloseAnimationRunning;
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

            invalidateDualCameraScale();
        }

        private void invalidateDualCameraScale() {
            if (cameraView != null && cameraView.isDual() && dualCameraMatrix != null) {
                Matrix m = cameraView.getDualPosition();
                m.set(dualCameraMatrix);
                m.postScale(openCloseProgress, openCloseProgress);
                cameraView.updateDualPosition();
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
                initFlashModes();
            });
            cameraView.setContentDescription(LocaleController.getString(R.string.AccDescrInstantCamera));

            dualCameraMatrix = cameraView.getSavedDualMatrix();
            invalidateDualCameraScale();

            addView(cameraView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private void openCamera() {
            if (fullSizeRect.isEmpty() || isOpenOrOpening || isOpenCloseAnimationRunning) {
                return;
            }

            openCloseAnimator.setFloatValues(openCloseProgress, 1f);
            openCloseAnimator.setDuration(OPEN_ANIMATION_DURATION);
            openCloseAnimator.start();
        }

        private void closeCamera() {
            if (!isOpenOrOpening || isOpenCloseAnimationRunning) {
                return;
            }

            resetDragAnimator.cancel();

            openCloseAnimator.setFloatValues(openCloseProgress, 0f);
            openCloseAnimator.setDuration(CLOSE_ANIMATION_DURATION);
            openCloseAnimator.start();
        }

        private void destroyCamera(boolean async) {
            if (cameraView != null) {
                cameraView.setDelegate(null);
                if (dualCameraMatrix != null) {
                    Matrix m = cameraView.getDualPosition();
                    m.set(dualCameraMatrix);
                }
                cameraView.destroy(async, null);
                removeView(cameraView);
                cameraView = null;
                dualCameraMatrix = null;
                placeholder.setVisibility(View.VISIBLE);
            }
        }

        private void setCameraZoom(float cameraZoom) {
            this.cameraZoom = clamp(cameraZoom, 1f, 0f);
            if (cameraView != null) {
                cameraView.setZoom(this.cameraZoom);
            }
        }

        private void initFlashModes() {
            if (frontCameraFlashMode == -1) {
                frontCameraFlashMode = clamp(
                    MessagesController.getGlobalMainSettings().getInt("frontflash", 1),
                    2,
                    0
                );
            }

            if (frontCameraFlashModes.isEmpty()) {
                frontCameraFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
                frontCameraFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
                frontCameraFlashModes.add(Camera.Parameters.FLASH_MODE_ON);
            }

            String flashMode = getLastSavedFlashMode();
            setCameraFlashMode(flashMode, false);
        }

        @NonNull
        private String getLastSavedFlashMode() {
            String flashMode = Camera.Parameters.FLASH_MODE_OFF;
            if (cameraView != null) {
                if (cameraView.isFrontface()) {
                    flashMode = frontCameraFlashModes.get(frontCameraFlashMode);
                } else {
                    if (cameraView.getCameraSession() != null) {
                        String backCameraFlashMode = cameraView.getCameraSession().getCurrentFlashMode();
                        if (backCameraFlashMode != null) {
                            flashMode = backCameraFlashMode;
                        }
                    }
                }
            }
            return flashMode;
        }

        private void toggleFlashMode() {
            String nextFlashMode = getNextFlashMode();
            if (nextFlashMode != null) {
                setCameraFlashMode(nextFlashMode, true);
            }
        }

        private void setCameraFlashMode(@NonNull String mode, boolean animated) {
            setCurrentFlashMode(mode);
            setCurrentFlashModeIcon(mode, false);
        }

        @Nullable
        private String getNextFlashMode() {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return null;
            }
            if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
                int nextIndex = frontCameraFlashMode + 1 < frontCameraFlashModes.size()
                    ? frontCameraFlashMode + 1
                    : 0;
                return frontCameraFlashModes.get(nextIndex);
            }
            return cameraView.getCameraSession().getNextFlashMode();
        }

        private void setCurrentFlashMode(@NonNull String mode) {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
                int index = frontCameraFlashModes.indexOf(mode);
                if (index >= 0) {
                    frontCameraFlashMode = index;
                    MessagesController.getGlobalMainSettings()
                        .edit()
                        .putInt("frontflash", frontCameraFlashMode)
                        .apply();
                }
                return;
            }
            cameraView.getCameraSession().setCurrentFlashMode(mode);
        }

        private void setCurrentFlashModeIcon(@NonNull String mode, boolean animated) {
            int iconResId = ResourcesCompat.ID_NULL;
            switch (mode) {
                case Camera.Parameters.FLASH_MODE_ON:
                    iconResId = R.drawable.media_photo_flash_on2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                    break;
                case Camera.Parameters.FLASH_MODE_AUTO:
                    iconResId = R.drawable.media_photo_flash_auto2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                    break;
                case Camera.Parameters.FLASH_MODE_OFF:
                    iconResId = R.drawable.media_photo_flash_off2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                    break;
            }
            flashButton.setIcon(iconResId, animated);
        }

        @Nullable
        private Bitmap getCameraBitmap() {
            if (cameraView != null) {
                return cameraView.getTextureView().getBitmap();
            }
            return null;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                requestApplyInsets();
                //applyWindowInsets(WindowInsetsCompat.toWindowInsetsCompat(getRootWindowInsets()));
            }
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
            if (cameraView != null && isNotAtDual(e)) {
                cameraView.focusToPoint((int) e.getRawX(), (int) e.getRawY());
                return true;
            }
            return false;
        }

        private boolean onDoubleTap(@NonNull MotionEvent e) {
            if (cameraView != null && isNotAtDual(e)) {
                cameraView.switchCamera();
                return true;
            }
            return false;
        }

        private boolean onDrag(float distanceY) {
            if (distanceY != 0) {
                float updatedDragProgress = dragProgress - distanceY / getMeasuredHeight();
                setDragProgress(updatedDragProgress);
                return true;
            }

            return false;
        }

        private boolean onFling(float velocityY) {
            if (dragProgress > CLOSE_ON_DRAG_ANCHOR_PERCENTAGE || velocityY > MIN_FLING_VELOCITY) {
                closeCamera();
                return true;
            }
            return false;
        }

        private boolean onScale(@NonNull ScaleGestureDetector detector) {
            float updatedZoom = cameraZoom + (detector.getScaleFactor() - 1.0f) * .75f;
            setCameraZoom(updatedZoom);
            return true;
        }

        private boolean isNotAtDual(@NonNull MotionEvent event) {
            return cameraView == null ||
                !cameraView.isAtDual(
                    event.getX() + getTranslationX(),
                    event.getY() + getTranslationY()
                );
        }


        private void applyWindowInsets(@NonNull WindowInsetsCompat insets) {
            int statusBarInset = AndroidUtilities.statusBarHeight;//insets
                //.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
                //.top;

            int navigationBarInset = insets
                .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
                .bottom;

            updateMargin(backButton, 0, statusBarInset, 0, 0);
            updateMargin(flashButton, 0, statusBarInset, 0, 0);
            updateMargin(dualButton, 0, statusBarInset, 0, 0);
        }

        private void updateMargin(
            @NonNull View view,
            int left,
            int top,
            int right,
            int bottom
        ) {
            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            lp.setMargins(left, top, right, bottom);
            view.setLayoutParams(lp);
        }

        private void checkActionButtonsPosition() {
            int right = 0;
            flashButton.setTranslationX(right);

            right -= dp(56);
            dualButton.setTranslationX(right);
        }

    }

}
