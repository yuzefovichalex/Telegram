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
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Build;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ZoomControlView;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.io.File;

public class MediaRecorder extends FrameLayout implements Bulletin.Delegate {

    private static final int MIN_FLING_VELOCITY = 2000;


    @NonNull
    private final ContentView contentView;

    @NonNull
    private final GestureDetector gestureDetector;

    @NonNull
    private final ScaleGestureDetector scaleGestureDetector;

    private boolean isScaling;
    private boolean isDraggingHorizontally;
    private boolean isVerticalFlingDetected;
    private boolean isDraggingVertically;
    private boolean isHorizontalFlingDetected;


    public MediaRecorder(@NonNull Context context) {
        super(context);

        contentView = new ContentView(context);
        addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                contentView.onTapDown(e);
                return false;
            }

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
                if (!isScaling) {
                    if (isDraggingHorizontally) {
                        return contentView.onHorizontalDrag(distanceX);
                    } else if (isDraggingVertically) {
                        return contentView.onVerticalDrag(distanceY);
                    } else if (Math.abs(distanceX) > Math.abs(distanceY)) {
                        isDraggingHorizontally = true;
                        return contentView.onHorizontalDrag(distanceX);
                    } else {
                        isDraggingVertically = true;
                        return contentView.onVerticalDrag(distanceY);
                    }
                }
                return false;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                isHorizontalFlingDetected = Math.abs(velocityX) > MIN_FLING_VELOCITY;
                isVerticalFlingDetected = Math.abs(velocityY) > MIN_FLING_VELOCITY;
                return contentView.onFling(velocityX, velocityY);
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (!isDraggingHorizontally && !isDraggingVertically) {
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

    private boolean isDragging() {
        return isDraggingHorizontally || isDraggingVertically;
    }

    public void setCurrentAccount(int currentAccount) {
        contentView.setCurrentAccount(currentAccount);
    }

    public void setSecretChat(boolean isSecretChat) {
        contentView.setSecretChat(isSecretChat);
    }

    public void setCallback(@Nullable Callback callback) {
        contentView.setCallback(callback);
    }

    public void setPreviewSize(float previewSize) {
        contentView.setPreviewSize(previewSize);
    }

    public void setPreviewPosition(float x, float y) {
        contentView.setPreviewPosition(x, y);
    }

    public void setPreviewRadius(float topLeft, float topRight) {
        contentView.setPreviewRadius(topLeft, topRight);
    }

    public void setPreviewClip(
        float clipLeft,
        float clipTop,
        float clipRight,
        float clipBottom
    ) {
        contentView.setPreviewClip(clipLeft, clipTop, clipRight, clipBottom);
    }

    public void startCameraPreview() {
        contentView.startCameraPreview();
    }

    public void stopCameraPreview() {
        contentView.stopCameraPreview();
    }

    public void startCamera() {
        contentView.startCamera();
    }

    public void openCamera(boolean animated) {
        if (contentView.openCamera(animated)) {
            Bulletin.addDelegate(this, this);
        }
    }

    public void closeCamera(boolean animated) {
        if (contentView.closeCamera(animated)) {
            Bulletin.addDelegate(this, this);
        }
    }

    public void destroyCamera(boolean async) {
        contentView.destroyCamera(async);
    }

    public boolean handleBackPress() {
        return contentView.handleBackPress();
    }

    public boolean handleKeyEvent(int keyCode, @NonNull KeyEvent keyEvent) {
        return contentView.handleKeyEvent(keyCode, keyEvent);
    }

    public void onPause() {
        // TODO handle pause, e.g. pause video
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (contentView.isOpen()) {
            if (!isScaling && !isDragging() && contentView.canParentProcessEvent(ev)) {
                if (ev.getPointerCount() == 1) {
                    gestureDetector.onTouchEvent(ev);
                } else {
                    scaleGestureDetector.onTouchEvent(ev);
                }
            } else if (isScaling) {
                scaleGestureDetector.onTouchEvent(ev);
            } else if (isDragging()) {
                gestureDetector.onTouchEvent(ev);
            }

            if (ev.getActionMasked() == MotionEvent.ACTION_UP ||
                ev.getActionMasked() == MotionEvent.ACTION_CANCEL
            ) {
                if (isDraggingHorizontally && !isHorizontalFlingDetected) {
                    contentView.onHorizontalDragEnd();
                } else if (isDraggingVertically && !isVerticalFlingDetected) {
                    contentView.onVerticalDragEnd();
                }
                isDraggingHorizontally = false;
                isDraggingVertically = false;
                isHorizontalFlingDetected = false;
                isVerticalFlingDetected = false;
            }

            return super.dispatchTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    public int getBottomOffset(int tag) {
        // TODO change for landscape
        return AndroidUtilities.navigationBarHeight + dp(172);
    }

    private static class ContentView extends FrameLayout implements MediaRecorderController.Callback,
        RecordControl.Delegate
    {

        private static final long OPEN_ANIMATION_DURATION = 350L;
        private static final long CLOSE_ANIMATION_DURATION = 220L;
        private static final int MIN_FLING_VELOCITY = 2000;
        private static final float CLOSE_ON_DRAG_ANCHOR_PERCENTAGE = 0.15f;
        private static final long MIN_DRAG_RESET_ANIMATION_DURATION = 50L;
        private static final int SELECTOR_BACKGROUND_COLOR = 0x20FFFFFF;


        @NonNull
        private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();

        @NonNull
        private final MediaRecorderController mediaRecorderController;

        @NonNull
        private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();


        @NonNull
        private final CollageLayoutView2 collageLayoutView;

        @Nullable
        private DualCameraView cameraView;

        @NonNull
        private final ImageView placeholder;

        @NonNull
        private final ImageView cameraIcon;

        @NonNull
        private final FlashViews flashViews;

        @NonNull
        private final FlashViews.ImageViewInvertable backButton;

        @NonNull
        private final ToggleButton2 flashButton;

        @NonNull
        private final ToggleButton dualButton;

        @NonNull
        private final ToggleButton2 collageButton;

        @NonNull
        private final CollageLayoutButton.CollageLayoutListView collageListView;

        @NonNull
        private final VideoTimerView videoTimerView;

        @NonNull
        private final ZoomControlView zoomControlView;

        @NonNull
        private final RecordControl recordControl;

        @NonNull
        private final PhotoVideoSwitcherView photoVideoSwitcherView;

        @NonNull
        private final HintTextView bottomHintTextView;

        @Nullable
        private GalleryListView galleryListView;
        private boolean isGalleryVisible;
        private boolean isGalleryOpen;
        private boolean willGalleryBeOpen;
        private float galleryDragProgress;
        private boolean isCameraPausedByGallery;

        @NonNull
        private final ValueAnimator galleryOpenCloseAnimator = new ValueAnimator();


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
        private final float[] previewTopRadius = new float[2];

        @NonNull
        private final RectF fullSizeRect = new RectF();

        @NonNull
        private final RectF currentRect = new RectF();

        private final float[] currentRadii = new float[8];

        @NonNull
        private final Path clipPath = new Path();


        private final int maxDragRadius = dp(32f);
        private float dragProgress;
        private boolean canParentProcessTouchEvents = true;

        @NonNull
        private final ValueAnimator resetDragAnimator = new ValueAnimator();


        @Nullable
        private Matrix dualCameraMatrix;

        private boolean isZoomControlVisible;
        private boolean isZoomControlAnimationRunning;

        @NonNull
        private final Runnable hideZoomControlRunnable =
            () -> setZoomControlVisibility(false, null);


        @Nullable
        private Callback callback;


        @NonNull
        private CollageLayout selectedCollageLayout;
        private boolean isCollageInUse;


        private boolean isVideo;
        private int currentAccount;


        private ContentView(@NonNull Context context) {
            super(context);

            mediaRecorderController = new MediaRecorderController(context);
            mediaRecorderController.setCallback(this);

            openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            openCloseAnimator.addUpdateListener(animation -> {
                openCloseProgress = (float) animation.getAnimatedValue();
                invalidateInternal();
            });
            openCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    notificationsLocker.lock();
                    onOpenCloseAnimatorStart();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    notificationsLocker.unlock();
                    onOpenCloseAnimatorEnd();
                }
            });

            resetDragAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            resetDragAnimator.addUpdateListener(animation -> setVerticalDragProgress((float) animation.getAnimatedValue()));

            galleryOpenCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            galleryOpenCloseAnimator.addUpdateListener(animation -> setGalleryDragProgress((float) animation.getAnimatedValue()));
            galleryOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (isGalleryOpen && isCameraPausedByGallery) {
                        startCameraPreview();
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (galleryDragProgress == 0f) {
                        destroyGalleryListView();
                    }
                }
            });

            collageLayoutView = new CollageLayoutView2(
                context,
                new BlurringShader.BlurManager(this),
                this,
                resourcesProvider
            );
            collageLayoutView.setResetState(() -> resetCollageResult(false, true));
            addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            placeholder = new ImageView(context);
            placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(placeholder, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            cameraIcon = new ImageView(context);
            cameraIcon.setImageResource(R.drawable.instant_camera);
            addView(cameraIcon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            flashViews = new FlashViews(context, null, null, null);
            addView(flashViews.backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            addView(flashViews.foregroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            backButton = new FlashViews.ImageViewInvertable(context);
            backButton.setContentDescription(getString(R.string.AccDescrGoBack));
            backButton.setScaleType(ImageView.ScaleType.CENTER);
            backButton.setImageResource(R.drawable.msg_photo_back);
            backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            backButton.setBackground(Theme.createSelectorDrawable(SELECTOR_BACKGROUND_COLOR));
            backButton.setOnClickListener(v -> handleBackPress());
            flashViews.add(backButton);
            addView(backButton, LayoutHelper.createFrame(56, 56));

            flashButton = new ToggleButton2(context);
            flashButton.setBackground(Theme.createSelectorDrawable(SELECTOR_BACKGROUND_COLOR));
            flashButton.setOnClickListener(v -> mediaRecorderController.toggleFlashMode());
            flashButton.setOnLongClickListener(v -> startFrontFlashPreview());
            flashViews.add(flashButton);
            addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT));

            dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
            dualButton.setOnClickListener(v -> mediaRecorderController.toggleDual());
            dualButton.setVisibility(mediaRecorderController.isDualAvailable() ? View.VISIBLE : View.GONE);
            dualButton.setAlpha(mediaRecorderController.isDualAvailable() ? 1f : 0f);
            flashViews.add(dualButton);
            addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT));

            selectedCollageLayout = CollageLayout.getLayouts().get(6);

            collageButton = new ToggleButton2(context);
            collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(selectedCollageLayout), false);
            collageButton.setBackground(Theme.createSelectorDrawable(SELECTOR_BACKGROUND_COLOR));
            collageButton.setSelected(false);
            collageButton.setOnClickListener(v -> toggleCollageList());
            flashViews.add(collageButton);
            addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT));

            collageListView = new CollageLayoutButton.CollageLayoutListView(context, flashViews);
            collageListView.setOnLayoutClick(collageLayout -> setCollageLayout(collageLayout, collageListView.isVisible()));
            collageListView.setVisible(false, false);
            setCollageListVisibility(false, false);
            addView(collageListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));

            checkActionButtonsPosition();

            videoTimerView = new VideoTimerView(context);
            flashViews.add(videoTimerView);
            addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP, 168, 0, 168, 0));

            zoomControlView = new ZoomControlView(context);
            zoomControlView.setDelegate(new ZoomControlView.ZoomControlViewDelegate() {
                @Override
                public void didSetZoom(float zoom) {
                    mediaRecorderController.setZoom(zoom);
                }

                @Override
                public void onTapUp() {
                    AndroidUtilities.runOnUIThread(hideZoomControlRunnable, 2000);
                }
            });
            zoomControlView.setAlpha(0f);
            zoomControlView.setVisibility(View.GONE);
            addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM));

            recordControl = new RecordControl(context);
            recordControl.setDelegate(this);
            recordControl.startAsVideo(false);
            flashViews.add(recordControl);
            addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM));

            photoVideoSwitcherView = new PhotoVideoSwitcherView(context);
            photoVideoSwitcherView.setOnSwitchModeListener(this::switchMode);
            photoVideoSwitcherView.setOnSwitchingModeListener(recordControl::startAsVideoT);
            flashViews.add(photoVideoSwitcherView);
            addView(photoVideoSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

            bottomHintTextView = new HintTextView(context);
            flashViews.add(bottomHintTextView);
            addView(bottomHintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.BOTTOM, 8, 0, 8, 8));

            invalidateControlsState(false);
            applyWindowInsets();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ViewOutlineProvider innerOutlineProvider = new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRect(
                            (int) currentRect.left,
                            (int) currentRect.top,
                            (int) currentRect.right,
                            (int) currentRect.bottom
                        );
                    }
                };
                placeholder.setOutlineProvider(innerOutlineProvider);
                placeholder.setClipToOutline(true);
                collageLayoutView.setOutlineProvider(innerOutlineProvider);
                collageLayoutView.setClipToOutline(true);

                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        if (isOpen()) {
                            outline.setRoundRect(
                                (int) currentRect.left,
                                (int) currentRect.top,
                                (int) currentRect.right,
                                (int) currentRect.bottom,
                                getDragRadius()
                            );
                        } else {
                            float topLeftRadius = 0f;
                            float topRightRadius = 0f;
                            float radius = 0f;
                            if (currentRadii[0] > 0 || currentRadii[2] > 0) {
                                if (currentRadii[0] > currentRadii[2]) {
                                    topLeftRadius = radius = currentRadii[0];
                                } else {
                                    topRightRadius = radius = currentRadii[2];
                                }
                            }
                            outline.setRoundRect(
                                (int) (currentRect.left - topRightRadius),
                                (int) currentRect.top,
                                (int) (currentRect.right + topLeftRadius),
                                (int) (currentRect.bottom + radius),
                                radius
                            );
                        }
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

        private void setCallback(@Nullable Callback callback) {
            this.callback = callback;
        }

        public void setCurrentAccount(int currentAccount) {
            this.currentAccount = currentAccount;
            mediaRecorderController.setCurrentAccount(currentAccount);
        }

        public void setSecretChat(boolean isSecretChat) {
            mediaRecorderController.setSecretChat(isSecretChat);
        }

        private void setPreviewSize(float previewSize) {
            if (this.previewSize == previewSize) {
                return;
            }

            this.previewSize = previewSize;
            invalidateInternal();
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

        private void setPreviewRadius(float topLeft, float topRight) {
            previewTopRadius[0] = topLeft;
            previewTopRadius[1] = topRight;
            invalidateInternal();
        }

        private void setPreviewClip(
            float clipLeft,
            float clipTop,
            float clipRight,
            float clipBottom
        ) {
            previewClipRect.set(clipLeft, clipTop, clipRight, clipBottom);
            invalidateInternal();
        }

        private void invalidateInternal() {
            if (getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
                return;
            }

            int minSide = Math.min(getMeasuredWidth(), getMeasuredHeight());
            float outerScale = lerp(previewSize / minSide, getDragScale(), openCloseProgress);
            setScaleX(outerScale);
            setScaleY(outerScale);
            setTranslationX(lerp(previewAbsoluteX, 0f, openCloseProgress));
            setTranslationY(lerp(previewAbsoluteY, getDragTranslationY(), openCloseProgress));

            float invertedProgress = 1f - openCloseProgress;
            cameraIcon.setAlpha(invertedProgress * invertedProgress);
            cameraIcon.setScaleX(1f / outerScale);
            cameraIcon.setScaleY(1f / outerScale);

            float previewLeft = (getMeasuredWidth() - minSide) / 2f;
            float previewTop = (getMeasuredHeight() - minSide) / 2f;
            float previewRight = previewLeft + minSide;
            float previewBottom = previewTop + minSide;

            float previewClippedTop = clamp(
                previewTop + previewClipRect.top / outerScale,
                previewBottom,
                previewTop
            );
            float previewClippedBottom = clamp(
                previewBottom - previewClipRect.bottom / outerScale,
                previewBottom,
                previewClippedTop
            );

            previewRect.set(
                previewLeft,
                previewClippedTop,
                previewRight,
                previewClippedBottom
            );

            lerp(previewRect, fullSizeRect, openCloseProgress, currentRect);

            float dragRadius = getDragRadius();
            setClipRadius(
                lerp(previewTopRadius[0] * (1f / outerScale), dragRadius, openCloseProgress),
                lerp(previewTopRadius[1] * (1f / outerScale), dragRadius, openCloseProgress),
                lerp(0, dragRadius, openCloseProgress),
                lerp(0, dragRadius, openCloseProgress)
            );
            invalidateClip();

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

        private boolean setVerticalDragProgress(float dragProgress) {
            if (isOpenCloseAnimationRunning) {
                return false;
            }

            float verifiedProgress = clamp(dragProgress, 1f, 0f);
            if (verifiedProgress == this.dragProgress) {
                return false;
            }

            this.dragProgress = verifiedProgress;

            float scale = getDragScale();
            setScaleX(scale);
            setScaleY(scale);
            setTranslationY(getDragTranslationY());

            float dragRadius = getDragRadius();
            setClipRadius(dragRadius, dragRadius, dragRadius, dragRadius);
            invalidateClip();

            return true;
        }

        private void resetVerticalDragProgress() {
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

        private void invalidateClip() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                placeholder.invalidateOutline();
                collageLayoutView.invalidateOutline();
                invalidateOutline();
            } else {
                clipPath.reset();
                clipPath.addRoundRect(currentRect, currentRadii, Path.Direction.CW);
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
                mediaRecorderController.attachCameraView(cameraView);
            });
            cameraView.setContentDescription(LocaleController.getString(R.string.AccDescrInstantCamera));

            dualCameraMatrix = cameraView.getSavedDualMatrix();
            invalidateDualCameraScale();

            collageLayoutView.setCameraView(cameraView);

            invalidateInternal();
        }

        private boolean openCamera(boolean animated) {
            if (fullSizeRect.isEmpty() || isOpenOrOpening || isOpenCloseAnimationRunning) {
                return false;
            }

            if (animated) {
                openCloseAnimator.setFloatValues(openCloseProgress, 1f);
                openCloseAnimator.setDuration(OPEN_ANIMATION_DURATION);
                openCloseAnimator.start();
            } else {
                openCloseProgress = 1f;
                onOpenCloseAnimatorStart();
                invalidateInternal();
                onOpenCloseAnimatorEnd();
            }

            invalidateControlsState(true);
            recordControl.updateGalleryImage(false);

            return true;
        }

        private boolean closeCamera(boolean animated) {
            if (!isOpenOrOpening || isOpenCloseAnimationRunning) {
                return false;
            }

            resetDragAnimator.cancel();

            if (animated) {
                openCloseAnimator.setFloatValues(openCloseProgress, 0f);
                openCloseAnimator.setDuration(CLOSE_ANIMATION_DURATION);
                openCloseAnimator.start();
            } else {
                openCloseProgress = 0f;
                onOpenCloseAnimatorStart();
                invalidateInternal();
                onOpenCloseAnimatorEnd();
            }

            resetCollageResult(true, false);

            if (isCollageInUse) {
                collageLayoutView.setLayout(null, true);
                if (collageLayoutView.hasContent()) {
                    collageLayoutView.clear(true);
                }
            }

            if (callback != null) {
                callback.onLockOrientationRequest(false);
                callback.onClose();
            }

            return true;
        }

        private void onOpenCloseAnimatorStart() {
            if (isOpenOrOpening && cameraView != null && cameraView.isDual()) {
                if (dualCameraMatrix == null) {
                    dualCameraMatrix = new Matrix();
                }
                dualCameraMatrix.set(cameraView.getDualPosition());
            }
            isOpenOrOpening = !isOpenOrOpening;
            isOpenCloseAnimationRunning = true;
        }

        private void onOpenCloseAnimatorEnd() {
            isOpenCloseAnimationRunning = false;
            dragProgress = 0f;
            setSystemBarsVisibility(!isOpenOrOpening);
            if (isOpenOrOpening && callback != null) {
                callback.onOpen();
            }
        }

        private void destroyCamera(boolean async) {
            if (cameraView != null) {
                mediaRecorderController.saveCameraThumb();
                collageLayoutView.setCameraView(null);
                cameraView.setDelegate(null);
                if (dualCameraMatrix != null) {
                    Matrix m = cameraView.getDualPosition();
                    m.set(dualCameraMatrix);
                }
                mediaRecorderController.detachCameraView();
                cameraView.destroy(async, null);
                cameraView = null;
            }

            dualCameraMatrix = null;
            isCameraPausedByGallery = false;
            placeholder.setVisibility(View.VISIBLE);

            isCollageInUse = false;
            selectedCollageLayout = CollageLayout.getLayouts().get(6);
            collageButton.setSelected(false);
            collageLayoutView.setLayout(null, false);

            setZoomControlVisibility(false, null);
            invalidateControlsState(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mediaRecorderController.loadLastSavedCameraThumb();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            fullSizeRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            invalidateInternal();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                canvas.save();
                canvas.clipPath(clipPath);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                super.dispatchDraw(canvas);
            }
        }

        private void onTapDown(@NonNull MotionEvent e) {
            if (isGalleryOpen && galleryListView != null && e.getY() < galleryListView.top()) {
                animateGalleryVisibility(false);
            } else if (collageListView.isVisible()) {
                setCollageListVisibility(false, true);
            }
        }

        private boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (!isGalleryVisible && isNotAtDual(e)) {
                if (collageListView.isVisible()) {
                    setCollageListVisibility(false, true);
                } else if (!isCollageInUse) {
                    mediaRecorderController.focusToPoint((int) e.getRawX(), (int) e.getRawY());
                }
                return true;
            }
            return false;
        }

        private boolean onDoubleTap(@NonNull MotionEvent e) {
            if (!isGalleryVisible && isNotAtDual(e)) {
                mediaRecorderController.switchCamera();
                return true;
            }
            return false;
        }

        private boolean onHorizontalDrag(float distance) {
            if (distance != 0 && !mediaRecorderController.isBusy()) {
                photoVideoSwitcherView.scrollX(distance);
                return true;
            }

            return false;
        }

        private void onHorizontalDragEnd() {
            photoVideoSwitcherView.stopScroll(0);
        }

        private boolean onVerticalDrag(float distance) {
            if (distance != 0 && !mediaRecorderController.isBusy()) {
                float dOffset = distance / getMeasuredHeight();
                if (isGalleryVisible) {
                    GalleryListView galleryListView = getOrCreateGalleryListView();
                    if (galleryDragProgress == 1f && dOffset > 0f) {
                        galleryListView.ignoreScroll = false;
                    } else if (!galleryOpenCloseAnimator.isRunning() &&
                        (galleryDragProgress == 1f &&
                            dOffset < 0f &&
                            isGalleryNotScrolled() ||
                            galleryDragProgress != 1f)
                    ) {
                        galleryListView.ignoreScroll = true;
                        setGalleryDragProgress(galleryDragProgress + dOffset);
                    }
                } else {
                    float updatedDragProgress = dragProgress - dOffset;
                    boolean isDragApplied = setVerticalDragProgress(updatedDragProgress);
                    if (!isDragApplied && dragProgress == 0f && distance > 0) {
                        setGalleryDragProgress(dOffset);
                    }
                }
                return true;
            }

            return false;
        }

        private void onVerticalDragEnd() {
            if (isGalleryVisible) {
                animateGalleryVisibility(galleryDragProgress > .3f);
            } else if (dragProgress > CLOSE_ON_DRAG_ANCHOR_PERCENTAGE) {
                closeCamera(true);
            } else {
                resetVerticalDragProgress();
            }
        }

        private boolean onFling(float velocityX, float velocityY) {
            if (mediaRecorderController.isBusy() ||
                resetDragAnimator.isRunning() ||
                galleryOpenCloseAnimator.isRunning()
            ) {
                return false;
            }

            float absoluteX = Math.abs(velocityX);
            float absoluteY = Math.abs(velocityY);
            if (absoluteX > absoluteY) {
                if (absoluteX > MIN_FLING_VELOCITY) {
                    photoVideoSwitcherView.stopScroll(velocityX);
                    return true;
                }
            } else {
                if (velocityY < 0 &&
                    !isGalleryOpen &&
                    absoluteY > MIN_FLING_VELOCITY &&
                    dragProgress == 0f
                ) {
                    animateGalleryVisibility(true);
                    return true;
                } else if (velocityY > MIN_FLING_VELOCITY) {
                    if (isGalleryVisible && isGalleryNotScrolled()) {
                        animateGalleryVisibility(false);
                    } else if (!isGalleryVisible) {
                        closeCamera(true);
                    }
                    return true;
                }
            }

            return false;
        }

        private boolean onScale(@NonNull ScaleGestureDetector detector) {
            mediaRecorderController.setZoomBy((detector.getScaleFactor() - 1.0f) * .75f);
            return true;
        }

        private boolean canParentProcessEvent(@NonNull MotionEvent event) {
            return canParentProcessTouchEvents && isNotOnControls(event);
        }

        private boolean isNotOnControls(@NonNull MotionEvent event) {
            boolean isOnControls = isOnHitRect(backButton, event) ||
                isOnHitRect(dualButton, event) ||
                isOnHitRect(flashButton, event) ||
                isOnHitRect(collageButton, event) ||
                collageListView.isVisible() && isOnHitRect(collageListView, event) ||
                zoomControlView.getVisibility() == View.VISIBLE && isOnHitRect(zoomControlView, event) ||
                isOnHitRect(recordControl, event) ||
                isOnHitRect(photoVideoSwitcherView, event);
            return !isOnControls &&
                isNotAtDual(event) &&
                !collageListView.isTouch() &&
                !zoomControlView.isTouch() &&
                !recordControl.isTouch() &&
                !photoVideoSwitcherView.isTouch();
        }

        private boolean isOnHitRect(@NonNull View view, @NonNull MotionEvent event) {
            view.getHitRect(AndroidUtilities.rectTmp2);
            return AndroidUtilities.rectTmp2.contains((int) event.getX(), (int) event.getY());
        }

        private boolean isNotAtDual(@NonNull MotionEvent event) {
            return cameraView == null ||
                !cameraView.isAtDual(
                    event.getX() + getTranslationX(),
                    event.getY() + getTranslationY()
                );
        }


        private void applyWindowInsets() {
            int statusBarInset = AndroidUtilities.statusBarHeight;
            int navigationBarInset = AndroidUtilities.navigationBarHeight;
            updateMargin(backButton, 0, statusBarInset, 0, 0);
            updateMargin(flashButton, 0, statusBarInset, 0, 0);
            updateMargin(dualButton, 0, statusBarInset, 0, 0);
            updateMargin(collageButton, 0, statusBarInset, 0, 0);
            updateMargin(collageListView, dp(16), statusBarInset, dp(56), 0);
            updateMargin(videoTimerView, 0, statusBarInset, 0, 0);
            updateMargin(zoomControlView, 0, 0, 0, navigationBarInset + dp(188));
            updateMargin(recordControl, 0, 0, 0, navigationBarInset + dp(80));
            updateMargin(photoVideoSwitcherView, 0, 0, 0, navigationBarInset + dp(16));
            updateMargin(bottomHintTextView, 0, 0, 0, navigationBarInset + dp(32));
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
            float right = 0;
            flashButton.setTranslationX(right);

            right -= dp(46) * flashButton.getAlpha();
            dualButton.setTranslationX(right);

            right -= dp(46) * dualButton.getAlpha();
            collageButton.setTranslationX(right);
        }

        @Override
        public void onCameraThumbLoad(@Nullable Bitmap thumb) {
            if (thumb != null) {
                placeholder.setImageBitmap(thumb);
            } else {
                placeholder.setImageResource(R.drawable.icplaceholder);
            }
        }

        @Override
        public void onZoomChanged(float zoom, boolean silent) {
            zoomControlView.setZoom(zoom, false);
            if (!silent) {
                AndroidUtilities.cancelRunOnUIThread(hideZoomControlRunnable);
                setZoomControlVisibility(true, () -> {
                    if (!zoomControlView.isTouch()) {
                        AndroidUtilities.runOnUIThread(hideZoomControlRunnable, 2000);
                    }
                });
            }
        }

        @Override
        public void onFlashModeChanged(@NonNull String flashMode, boolean isFront) {
            setCurrentFlashModeIcon(flashMode);
            if (isFront && mediaRecorderController.isRecordingVideo()) {
                if (mediaRecorderController.shouldUseDisplayFlash()) {
                    flashViews.flashIn();
                } else {
                    flashViews.flashOut();
                }
            }
        }

        private void setCurrentFlashModeIcon(@NonNull String mode) {
            int iconResId = ResourcesCompat.ID_NULL;
            switch (mode) {
                case Camera.Parameters.FLASH_MODE_ON:
                case Camera.Parameters.FLASH_MODE_TORCH:
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
            flashButton.setIcon(iconResId, true);
        }

        @Override
        public void onFrontFlashWarmthChanged(float warmth) {
            flashViews.setWarmth(warmth);
        }

        @Override
        public void onFrontFlashIntensityChanged(float intensity) {
            flashViews.setIntensity(intensity);
        }

        @Override
        public void onPhotoShoot() {
            setCollageListVisibility(false, true);

            if (callback != null && !callback.canTakePicture() ||
                mediaRecorderController.isBusy()
            ) {
                return;
            }

            Runnable onStart = callback != null
                ? () -> callback.onLockOrientationRequest(true)
                : null;

            if (mediaRecorderController.shouldUseDisplayFlash()) {
                mediaRecorderController.setPreparing(true);
                flashViews.flashIn(() ->
                    mediaRecorderController.takePicture(
                        false,
                        collageLayoutView.hasLayout(),
                        onStart
                    )
                );
            } else {
                mediaRecorderController.takePicture(
                    true,
                    collageLayoutView.hasLayout(),
                    onStart
                );
            }
        }

        @Override
        public void onTakePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation,
            boolean isSameTakePictureOrientation
        ) {
            flashViews.flashOut(() -> {
                if (callback != null) {
                    callback.onLockOrientationRequest(false);
                }

                if (collageLayoutView.hasLayout()) {
                    StoryEntry entry = StoryEntry.fromPhotoShoot(outputFile, 0);
                    pushCollageEntry(entry);
                } else if (callback != null) {
                    callback.onTakePictureSuccess(
                        outputFile,
                        width, height,
                        orientation, isSameTakePictureOrientation
                    );
                }
            });
        }

        @Override
        public boolean canRecordAudio() {
            return callback != null && callback.canRecordVideo();
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            setCollageListVisibility(false, true);

            if (mediaRecorderController.isBusy()) {
                return;
            }

            Runnable onStart = callback != null
                ? () -> {
                    callback.onLockOrientationRequest(true);
                    if (whenStarted != null) {
                        whenStarted.run();
                    }
                }
                : whenStarted;

            if (mediaRecorderController.shouldUseDisplayFlash()) {
                mediaRecorderController.setPreparing(true);
                flashViews.flashIn(() ->
                    mediaRecorderController.startVideoRecord(false, onStart)
                );
            } else {
                mediaRecorderController.startVideoRecord(false, onStart);
            }

            videoTimerView.setRecording(true, true);
            int hintResId = byLongPress ? R.string.StoryHintSwipeToZoom : R.string.StoryHintPinchToZoom;
            bottomHintTextView.setText(LocaleController.getString(hintResId), false);
            invalidateControlsState(true);
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onVideoRecordLocked() {
            bottomHintTextView.setText(LocaleController.getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public void onVideoRecordPause() {
            mediaRecorderController.stopVideoRecord(true);
        }

        @Override
        public void onVideoRecordResume() {
            mediaRecorderController.startVideoRecord(false, null);
        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            setCollageListVisibility(false, true);
            videoTimerView.setRecording(false, true);
            mediaRecorderController.stopVideoRecord(false);
            if (callback != null) {
                callback.onLockOrientationRequest(false);
            }
            invalidateControlsState(true);
        }

        @Override
        public void onRecordVideoSuccess(
            @NonNull File outputFile,
            @NonNull String thumbPath,
            int width,
            int height,
            long duration
        ) {
            recordControl.stopRecordingLoading(true);
            notifyRecordVideoSuccess(outputFile, thumbPath, width, height, duration);
        }

        private void notifyRecordVideoSuccess(
            @NonNull File outputFile,
            @NonNull String thumbPath,
            int width,
            int height,
            long duration
        ) {
            flashViews.flashOut(() -> {
                if (collageLayoutView.hasLayout()) {
                    StoryEntry entry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
                    pushCollageEntry(entry);
                } else if (callback != null) {
                    callback.onRecordVideoSuccess(outputFile, thumbPath, width, height, duration);
                }
                videoTimerView.setDuration(0, false);
                invalidateControlsState(true);
            });
        }

        @Override
        public void onRecordVideoFailure() {
            Toast.makeText(getContext(), "Unable to process recorded video", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onGalleryClick() {
            animateGalleryVisibility(true);
        }

        @Override
        public void onFlipClick() {
            if (mediaRecorderController.isRecordingVideo() &&
                mediaRecorderController.isFrontface()
            ) {
                flashViews.flashOut();
            }
            mediaRecorderController.switchCamera();
        }

        @Override
        public void onCameraSwitchRequest() {
            recordControl.rotateFlip(180);
        }

        @Override
        public void onCameraSwitchDone() {
            if (mediaRecorderController.isRecordingVideo() &&
                mediaRecorderController.shouldUseDisplayFlash()
            ) {
                flashViews.flashIn();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (!collageLayoutView.hasLayout()) {
                mediaRecorderController.toggleDual();
            }
        }

        @Override
        public void onDualToggle(boolean isDual) {
            dualButton.setValue(isDual);
        }

        @Override
        public void onZoom(float zoom) {
            mediaRecorderController.setZoom(zoom, true);
        }

        @Override
        public void onCheckClick() {
            if (callback != null && collageLayoutView.isFilled()) {
                collageLayoutView.setTouchable(false);
                bottomHintTextView.setText(LocaleController.getString(R.string.CollageProcessing), true);
                StoryEntry collageEntry = StoryEntry.asCollage(
                    collageLayoutView.getLayout(),
                    collageLayoutView.getContent(),
                    false
                );
                mediaRecorderController.convertCollageToMedia(
                    collageEntry,
                    () -> invalidateControlsState(true),
                    () -> {
                        recordControl.setProcessingProgress(0f, true);
                        resetCollageResult(true, true);
                    }
                );
            } else {
                resetCollageResult(true, true);
            }
        }

        @Override
        public void onCancelClick() {
            mediaRecorderController.cancelLatestCollageConversion();
            collageLayoutView.setTouchable(true);
            invalidateControlsState(true);
        }

        @Override
        public void onCollageConversionProgress(float progress) {
            recordControl.setProcessingProgress(progress, true);
        }

        @Override
        public void onCollageConversionCancel() {
            recordControl.setProcessingProgress(0f, true);
            bottomHintTextView.setText(LocaleController.getString(R.string.StoryCollageReorderHint), true);
        }

        @Override
        public void onCollagePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation
        ) {
            if (callback != null) {
                callback.onTakePictureSuccess(outputFile, width, height, orientation, true);
            }
        }

        @Override
        public void onCollageVideoSuccess(
            @NonNull File outputFile,
            @Nullable String thumbPath,
            int width,
            int height,
            long duration
        ) {
            if (callback != null) {
                callback.onRecordVideoSuccess(outputFile, thumbPath, width, height, duration);
            }
        }

        private void startCameraPreview() {
            mediaRecorderController.startPreview();
        }

        private void stopCameraPreview() {
            mediaRecorderController.stopPreview();
        }

        private boolean startFrontFlashPreview() {
            if (!mediaRecorderController.isFrontface() || mediaRecorderController.isBusy()) {
                return false;
            }

            flashButton.setSelected(true);
            flashViews.previewStart();
            ItemOptions.makeOptions(this, resourcesProvider, flashButton)
                .addView(
                    new SliderView(getContext(), SliderView.TYPE_WARMTH)
                        .setValue(mediaRecorderController.getDisplayFlashWarmth())
                        .setOnValueChange(mediaRecorderController::setDisplayFlashWarmth)
                )
                .addSpaceGap()
                .addView(
                    new SliderView(getContext(), SliderView.TYPE_INTENSITY)
                        .setMinMax(.65f, 1f)
                        .setValue(mediaRecorderController.getDisplayFlashIntensity())
                        .setOnValueChange(mediaRecorderController::setDisplayFlashIntensity)
                )
                .setDimAlpha(0)
                .setGravity(Gravity.RIGHT)
                .translate(dp(46), -dp(4))
                .setBackgroundColor(0xbb1b1b1b)
                .setOnDismiss(() -> {
                    mediaRecorderController.saveCurrentFrontFlashParams();
                    flashViews.previewEnd();
                    flashButton.setSelected(false);
                })
                .show();

            return true;
        }

        @NonNull
        private GalleryListView getOrCreateGalleryListView() {
            if (galleryListView == null) {
                galleryListView = new GalleryListView(
                    currentAccount,
                    getContext(),
                    resourcesProvider,
                    null,
                    true
                ) {
                    @Override
                    protected void firstLayout() {
                        setGalleryDragProgress(galleryDragProgress);
                    }

                    @Override
                    protected void onFullScreen(boolean isFullscreen) {
                        setSystemBarsVisibility(isFullscreen);
                        if (isFullscreen) {
                            stopCameraPreview();
                        } else {
                            startCameraPreview();
                        }
                        isCameraPausedByGallery = isFullscreen;
                    }
                };
                galleryListView.setOnBackClickListener(() -> animateGalleryVisibility(false));
                galleryListView.setOnSelectListener(this::useGalleryEntry);
                galleryListView.allowSearch(false);
                galleryListView.ignoreScroll = true;
                addView(galleryListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            return galleryListView;
        }

        private void useGalleryEntry(
            @Nullable Object entry,
            @NonNull Integer position,
            @Nullable Bitmap blurredThumb
        ) {
            animateGalleryVisibility(false);
            if (entry instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                if (collageLayoutView.hasLayout()) {
                    StoryEntry collageEntry = StoryEntry.fromPhotoEntry(photoEntry);
                    collageEntry.blurredVideoThumb = blurredThumb;
                    pushCollageEntry(collageEntry);
                } else if (galleryListView != null && callback != null) {
                    callback.onGalleryPhotoSelect(galleryListView.selectedAlbum, photoEntry, position);
                }
            }
        }

        private void destroyGalleryListView() {
            if (galleryListView == null) {
                return;
            }

            galleryOpenCloseAnimator.cancel();
            galleryDragProgress = 0f;
            isGalleryVisible = false;
            isGalleryOpen = false;
            removeView(galleryListView);
            galleryListView = null;
        }

        private boolean isGalleryNotScrolled() {
            return galleryListView == null || !galleryListView.isScrolled();
        }

        private void setGalleryDragProgress(float dragProgress) {
            if (galleryDragProgress == 0f &&
                (mediaRecorderController.isBusy() || collageLayoutView.isFilled())
            ) {
                return;
            }

            galleryDragProgress = clamp(dragProgress, 1f, 0f);
            isGalleryVisible = galleryDragProgress != 0f;
            isGalleryOpen = galleryDragProgress == 1f;

            GalleryListView galleryListView = getOrCreateGalleryListView();
            if (!galleryListView.firstLayout) {
                galleryListView.setTranslationY((1f - galleryDragProgress) * (getMeasuredHeight() - galleryListView.top()));
            }
            galleryListView.ignoreScroll = !isGalleryOpen;
        }

        private void animateGalleryVisibility(boolean isOpen) {
            if (isOpen && isGalleryOpen ||
                !isOpen && !isGalleryVisible ||
                galleryOpenCloseAnimator.isRunning() && willGalleryBeOpen == isOpen
            ) {
                return;
            }

            willGalleryBeOpen = isOpen;
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator.setFloatValues(galleryDragProgress, isOpen ? 1f : 0f);
            galleryOpenCloseAnimator.start();
        }

        private void toggleCollageList() {
            isCollageInUse = !collageListView.isVisible();
            if (isCollageInUse && mediaRecorderController.isDual()) {
                mediaRecorderController.toggleDual();
            }
            setCollageListVisibility(!collageListView.isVisible(), true);
            if (!isCollageInUse) {
                canParentProcessTouchEvents = true;
            }
        }

        private void setCollageListVisibility(boolean isVisible, boolean animated) {
            if (isVisible == collageListView.isVisible()) {
                return;
            }

            collageListView.setSelected(isVisible ? selectedCollageLayout : null);
            collageListView.setVisible(isVisible, animated);

            collageButton.setSelected(!isVisible && isCollageInUse, animated);

            invalidateControlsState(animated);
        }

        private void setCollageLayout(@NonNull CollageLayout collageLayout, boolean animated) {
            CollageLayout previousLayout = selectedCollageLayout;
            selectedCollageLayout = collageLayout;

            boolean isCollageListVisible = collageListView.isVisible();
            if (isCollageListVisible) {
                collageListView.setSelected(collageLayout);
                if (previousLayout == selectedCollageLayout) {
                    setCollageListVisibility(false, animated);
                }
            }

            if (!selectedCollageLayout.isValid()) {
                selectedCollageLayout = CollageLayout.getLayouts().get(6);
                if (isCollageInUse) {
                    toggleCollageList();
                    return;
                }
            }

            invalidateControlsState(animated);
            recordControl.setCollageProgress(collageLayoutView.getFilledProgress(), true);

            canParentProcessTouchEvents = collageLayoutView.getFilledProgress() != 1f;
        }

        private void invalidateControlsState(boolean animated) {
            if (isCollageInUse) {
                collageLayoutView.setLayout(selectedCollageLayout, animated);
            } else {
                collageLayoutView.setLayout(null, animated);
                collageLayoutView.clear(true);
            }

            Drawable icon = new CollageLayoutButton.CollageLayoutDrawable(
                selectedCollageLayout,
                collageListView.isVisible()
            );
            collageButton.setIcon(icon, false);

            boolean isCollageListVisible = collageListView.isVisible();
            boolean hasEmptyParts = collageLayoutView.getFilledProgress() != 1f;
            setActionButtonVisibility(backButton, isOpenOrOpening && !isCollageListVisible, animated);
            setActionButtonVisibility(flashButton, isOpenOrOpening && !isCollageListVisible && hasEmptyParts, animated);
            setActionButtonVisibility(dualButton, isOpenOrOpening && !isCollageInUse && !isCollageListVisible && mediaRecorderController.isDualAvailable(), animated);
            setActionButtonVisibility(collageButton, isOpenOrOpening && !mediaRecorderController.isProcessing(), animated);
            setVideoTimerVisibility(isOpenOrOpening && !isCollageListVisible && hasEmptyParts && isVideo, animated);
            setPhotoVideoSwitcherVisibility(isOpenOrOpening && hasEmptyParts && !mediaRecorderController.isBusy(), animated);
            setBottomHintTextViewVisibility(isOpenOrOpening && mediaRecorderController.isRecordingVideo() || !hasEmptyParts, animated);
            recordControl.setCollageProgress(collageLayoutView.getFilledProgress(), animated);
            setRecordControlVisibility(isOpenOrOpening, animated);
            collageListView.setVisible(collageListView.isVisible() && !mediaRecorderController.isProcessing(), animated);
        }

        private void pushCollageEntry(@NonNull StoryEntry entry) {
            if (collageLayoutView.push(entry)) {
                onCollageDone();
            }
            recordControl.setCollageProgress(collageLayoutView.getFilledProgress(), true);
        }

        private void onCollageDone() {
            canParentProcessTouchEvents = false;
            bottomHintTextView.setText(LocaleController.getString(R.string.StoryCollageReorderHint), false);
            invalidateControlsState(true);
        }

        private void resetCollageResult(boolean clear, boolean animated) {
            if (clear) {
                collageLayoutView.clear(true);
            }

            collageLayoutView.setTouchable(true);
            setCollageLayout(collageLayoutView.getLayout(), animated);
        }

        private void switchMode(boolean isVideo) {
            if (mediaRecorderController.isBusy()) {
                return;
            }

            this.isVideo = isVideo;
            setCollageListVisibility(false, true);
            setVideoTimerVisibility(isVideo, true);
            recordControl.startAsVideo(isVideo);
        }

        public boolean handleBackPress() {
            if (isOpen()) {
                if (isGalleryVisible) {
                    animateGalleryVisibility(false);
                } else if (collageListView.isVisible()) {
                    setCollageListVisibility(false, true);
                } else if (mediaRecorderController.isRecordingVideo()) {
                    recordControl.stopRecording();
                } else if (collageLayoutView.hasLayout() && collageLayoutView.hasContent()) {
                    if (!mediaRecorderController.isBusy()) {
                        resetCollageResult(true, true);
                    } else if (mediaRecorderController.isProcessing()) {
                        mediaRecorderController.cancelLatestCollageConversion();
                    }
                } else if (!mediaRecorderController.isBusy()) {
                    closeCamera(true);
                }
                return true;
            }
            return false;
        }

        private boolean handleKeyEvent(int keyCode, @NonNull KeyEvent keyEvent) {
            if (isOpen() &&
                !collageLayoutView.isFilled() &&
                keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ) {
                if (keyEvent.getRepeatCount() == 0) {
                    if (!mediaRecorderController.isBusy()) {
                        if (isVideo) {
                            recordControl.startRecording();
                        } else {
                            recordControl.callPhotoShoot();
                        }
                    } else if (mediaRecorderController.isRecordingVideo()) {
                        recordControl.stopRecording();
                    }
                }
                return true;
            }
            return false;
        }

        private void setSystemBarsVisibility(boolean isVisible) {
            int visibility = !isVisible
                ? View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                : View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            setSystemUiVisibility(visibility);
        }

        private void setActionButtonVisibility(
            @NonNull View button,
            boolean isVisible,
            boolean animated
        ) {
            setVisibility(
                button,
                isVisible,
                animated ? 320 : 0,
                0f,
                null,
                this::checkActionButtonsPosition,
                null
            );
        }

        private void setVideoTimerVisibility(boolean isVisible, boolean animated) {
            setVisibility(videoTimerView, isVisible, animated ? 350 : 0);
        }

        private void setRecordControlVisibility(boolean isVisible, boolean animated) {
            setVisibility(recordControl, isVisible, animated ? 350 : 0);
        }

        private void setPhotoVideoSwitcherVisibility(boolean isVisible, boolean animated) {
            setVisibility(photoVideoSwitcherView, isVisible, animated ? 260 : 0, dp(24f));
        }

        private void setBottomHintTextViewVisibility(boolean isVisible, boolean animated) {
            setVisibility(bottomHintTextView, isVisible, animated ? 260 : 0, -dp(32f));
        }

        private void setZoomControlVisibility(
            boolean isVisible,
            @Nullable Runnable onAnimationEnd
        ) {
            if (isZoomControlVisible == isVisible || isZoomControlAnimationRunning) {
                if (onAnimationEnd != null) {
                    onAnimationEnd.run();
                }
                return;
            }

            setVisibility(
                zoomControlView,
                isVisible,
                180,
                0f,
                () -> isZoomControlAnimationRunning = true,
                null,
                () -> {
                    isZoomControlVisible = isVisible;
                    isZoomControlAnimationRunning = false;
                }
            );
        }

        private void setVisibility(
            @NonNull View view,
            boolean isVisible,
            long duration
        ) {
            setVisibility(view, isVisible, duration, 0f);
        }

        private void setVisibility(
            @NonNull View view,
            boolean isVisible,
            long duration,
            float additionalTranslationY
        ) {
            setVisibility(view, isVisible, duration, additionalTranslationY, null, null, null);
        }

        private void setVisibility(
            @NonNull View view,
            boolean isVisible,
            long duration,
            float additionalTranslationY,
            @Nullable Runnable onStart,
            @Nullable Runnable onUpdate,
            @Nullable Runnable onEnd
        ) {
            view.animate().cancel();
            if (duration > 0) {
                view.animate()
                    .alpha(isVisible ? 1f : 0f)
                    .translationY(isVisible ? 0f : additionalTranslationY)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setDuration(duration)
                    .setUpdateListener(animation -> {
                        if (onUpdate != null) {
                            onUpdate.run();
                        }
                    })
                    .withStartAction(() -> {
                        if (isVisible) {
                            view.setVisibility(View.VISIBLE);
                        }
                        if (onStart != null) {
                            onStart.run();
                        }
                    })
                    .withEndAction(() -> {
                        if (!isVisible) {
                            view.setVisibility(View.GONE);
                        }
                        if (onEnd != null) {
                            onEnd.run();
                        }
                    });
            } else {
                if (onStart != null) {
                    onStart.run();
                }
                view.setAlpha(isVisible ? 1f : 0f);
                view.setTranslationY(isVisible ? 0f : additionalTranslationY);
                view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
                if (onUpdate != null) {
                    onUpdate.run();
                }
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        }

    }


    public interface Callback {
        void onOpen();
        boolean canTakePicture();
        boolean canRecordVideo();
        void onLockOrientationRequest(boolean isLocked);
        void onTakePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation,
            boolean isSameTakePictureOrientation
        );
        void onGalleryPhotoSelect(
            @NonNull MediaController.AlbumEntry album,
            @NonNull MediaController.PhotoEntry entry,
            int position
        );
        void onRecordVideoSuccess(
            @NonNull File outputFile,
            @Nullable String thumbPath,
            int width,
            int height,
            long duration
        );
        void onClose();
    }

}
