package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.Utilities.clamp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.provider.MediaStore;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class MediaRecorderController implements CameraView.Callback {

    private static final String NO_FLASH_MODE = "no_flash_mode";


    @NonNull
    private final Context context;

    @Nullable
    private CameraView cameraView;

    @Nullable
    private Callback callback;

    private final boolean isDualAvailable;

    @NonNull
    private final ArrayList<String> displayFlashModes = new ArrayList<>();

    private float zoom;

    @NonNull
    private String frontFlashMode = NO_FLASH_MODE;
    private float displayFlashWarmth = -1f;
    private float displayFlashIntensity = -1f;

    @NonNull
    private String backFlashMode = NO_FLASH_MODE;

    private boolean isCameraSwitchInProgress;
    private boolean isPreparing;
    private boolean isTakingPicture;
    private boolean isRecordingVideo;
    private boolean isProcessing;

    @Nullable
    private DownloadButton.BuildingVideo videoBuilder;


    public MediaRecorderController(@NonNull Context context) {
        this.context = context;
        isDualAvailable = DualCameraView.dualAvailableStatic(context);
    }


    public boolean isTakingPicture() {
        return isPreparing || isTakingPicture;
    }

    public boolean isRecordingVideo() {
        return isPreparing || isRecordingVideo;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public boolean isBusy() {
        return isPreparing || isTakingPicture || isRecordingVideo || isProcessing;
    }

    public boolean isFrontface() {
        return cameraView != null && cameraView.isFrontface();
    }

    public boolean isDualAvailable() {
        return isDualAvailable;
    }

    public boolean isDual() {
        return cameraView != null && cameraView.isDual();
    }

    public float getDisplayFlashWarmth() {
        return displayFlashWarmth;
    }

    public float getDisplayFlashIntensity() {
        return displayFlashIntensity;
    }

    public void setPreparing(boolean preparing) {
        isPreparing = preparing;
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void attachCameraView(@Nullable CameraView cameraView) {
        this.cameraView = cameraView;
        if (cameraView != null) {
            cameraView.setCallback(this);
            checkFlashModes();
            checkFrontFlashParams();
            checkDual();
            setZoom(this.zoom, true, true);
        }
    }

    private void checkFlashModes() {
        if (displayFlashModes.isEmpty()) {
            displayFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            displayFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            displayFlashModes.add(Camera.Parameters.FLASH_MODE_ON);
        }

        if (frontFlashMode.equals(NO_FLASH_MODE)) {
            int frontFlashModeIdx = clamp(
                MessagesController.getGlobalMainSettings().getInt("frontflash", 0),
                2,
                0
            );
            frontFlashMode = displayFlashModes.get(frontFlashModeIdx);
        }

        if (backFlashMode.equals(NO_FLASH_MODE)) {
            backFlashMode = Camera.Parameters.FLASH_MODE_OFF;
        }

        checkActiveFlashMode();
    }

    private void checkActiveFlashMode() {
        if (cameraView != null) {
            if (isRecordingVideo) {
                if (cameraView.isFrontface()) {
                    frontFlashMode = shouldUseFlash(frontFlashMode)
                        ? Camera.Parameters.FLASH_MODE_TORCH
                        : Camera.Parameters.FLASH_MODE_OFF;
                } else {
                    backFlashMode = shouldUseFlash(backFlashMode)
                        ? Camera.Parameters.FLASH_MODE_TORCH
                        : Camera.Parameters.FLASH_MODE_OFF;
                }
            } else {
                frontFlashMode = frontFlashMode.equals(Camera.Parameters.FLASH_MODE_TORCH)
                    ? Camera.Parameters.FLASH_MODE_ON
                    : frontFlashMode;
                backFlashMode = backFlashMode.equals(Camera.Parameters.FLASH_MODE_TORCH)
                    ? Camera.Parameters.FLASH_MODE_ON
                    : backFlashMode;
            }

            CameraSessionWrapper cameraSession = cameraView.getCameraSession();
            if (cameraSession != null) {
                boolean isFrontface = cameraView.isFrontface();
                String activeFlashMode = isFrontface ? frontFlashMode : backFlashMode;
                boolean changed;

                if (!isFrontface || cameraSession.hasFlashModes()) {
                    changed = setFlashMode(cameraSession, activeFlashMode, isFrontface);
                } else {
                    changed = setFrontFlashMode(activeFlashMode);
                }

                if (!changed && callback != null && !activeFlashMode.equals(NO_FLASH_MODE)) {
                    callback.onFlashModeChanged(activeFlashMode, cameraView.isFrontface());
                }
            }
        }
    }

    private void checkFrontFlashParams() {
        if (displayFlashWarmth == -1f) {
            displayFlashWarmth = MessagesController.getGlobalMainSettings()
                .getFloat("frontflash_warmth", .9f);
        }

        if (displayFlashIntensity == -1f) {
            displayFlashIntensity = MessagesController.getGlobalMainSettings()
                .getFloat("frontflash_intensity", 1);
        }

        if (callback != null) {
            callback.onFrontFlashWarmthChanged(displayFlashWarmth);
            callback.onFrontFlashIntensityChanged(displayFlashIntensity);
        }
    }

    private void checkDual() {
        if (cameraView != null && callback != null) {
            callback.onDualToggle(cameraView.isDual());
        }
    }

    public void detachCameraView() {
        if (cameraView != null) {
            cameraView.setCallback(null);
        }
        cameraView = null;
        zoom = 0f;
        frontFlashMode = NO_FLASH_MODE;
        displayFlashWarmth = -1f;
        displayFlashIntensity = -1f;
        backFlashMode = NO_FLASH_MODE;
        isCameraSwitchInProgress = false;
        isPreparing = false;
        isTakingPicture = false;
        isRecordingVideo = false;
        cancelLatestCollageConversion();
    }


    public void setZoomBy(float dZoom) {
        setZoom(zoom + dZoom);
    }

    public void setZoom(float zoom) {
        setZoom(zoom, false);
    }

    public void setZoom(float zoom, boolean silent) {
        setZoom(zoom, false, silent);
    }

    private void setZoom(float zoom, boolean force, boolean silent) {
        if (isTakingPicture()) {
            return;
        }

        float newZoom = clamp(zoom, 1f, 0f);
        if (this.zoom == newZoom && !force) {
            return;
        }

        this.zoom = newZoom;
        if (cameraView != null) {
            cameraView.setZoom(newZoom);
            if (callback != null) {
                callback.onZoomChanged(newZoom, silent);
            }
        }
    }


    public void toggleFlashMode() {
        if (!isCameraSwitchInProgress && !isTakingPicture() && cameraView != null) {
            CameraSessionWrapper cameraSession = cameraView.getCameraSession();
            if (cameraSession != null) {
                if (!cameraView.isFrontface() || cameraSession.hasFlashModes()) {
                    String mode = cameraSession.getNextFlashMode(isRecordingVideo);
                    setFlashMode(
                        cameraSession,
                        filterVideoRecordingAutoFlashMode(mode),
                        cameraView.isFrontface()
                    );
                } else {
                    int currentIdx = displayFlashModes.indexOf(frontFlashMode);
                    int nextIdx = currentIdx + 1 < displayFlashModes.size() ? currentIdx + 1 : 0;
                    String mode = displayFlashModes.get(nextIdx);
                    setFrontFlashMode(filterVideoRecordingAutoFlashMode(mode));
                }
            }
        }
    }

    @NonNull
    public String filterVideoRecordingAutoFlashMode(@NonNull String mode) {
        return isRecordingVideo && mode.equals(Camera.Parameters.FLASH_MODE_AUTO)
            ? Camera.Parameters.FLASH_MODE_TORCH
            : mode;
    }

    private boolean setFrontFlashMode(@NonNull String flashMode) {
        int modeIdx = flashMode.equals(Camera.Parameters.FLASH_MODE_TORCH)
            ? displayFlashModes.indexOf(Camera.Parameters.FLASH_MODE_ON)
            : displayFlashModes.indexOf(flashMode);
        if (modeIdx != -1 && !frontFlashMode.equals(flashMode)) {
            frontFlashMode = flashMode;
            MessagesController.getGlobalMainSettings()
                .edit()
                .putInt("frontflash", modeIdx)
                .apply();
            if (callback != null) {
                callback.onFlashModeChanged(flashMode, true);
            }
            return true;
        }
        return false;
    }

    private boolean setFlashMode(
        @NonNull CameraSessionWrapper cameraSession,
        @NonNull String flashMode,
        boolean isFront
    ) {
        if (!cameraSession.getCurrentFlashMode().equals(flashMode)) {
            cameraSession.setCurrentFlashMode(flashMode);
            if (isFront) {
                frontFlashMode = flashMode;
            } else {
                backFlashMode = flashMode;
            }
            if (callback != null) {
                callback.onFlashModeChanged(flashMode, isFront);
            }
            return true;
        }
        return false;
    }

    public void setDisplayFlashWarmth(float warmth) {
        if (displayFlashWarmth == warmth) {
            return;
        }

        displayFlashWarmth = warmth;
        if (callback != null) {
            callback.onFrontFlashWarmthChanged(warmth);
        }
    }

    public void setDisplayFlashIntensity(float intensity) {
        if (displayFlashIntensity == intensity) {
            return;
        }

        displayFlashIntensity = intensity;
        if (callback != null) {
            callback.onFrontFlashIntensityChanged(intensity);
        }
    }

    public void saveCurrentFrontFlashParams() {
        if (!frontFlashMode.equals(NO_FLASH_MODE) &&
            displayFlashWarmth != -1f &&
            displayFlashIntensity != -1f
        ) {
            MessagesController.getGlobalMainSettings()
                .edit()
                .putFloat("frontflash_warmth", displayFlashWarmth)
                .putFloat("frontflash_intensity", displayFlashIntensity)
                .apply();
        }
    }


    public void focusToPoint(int x, int y) {
        if (!isTakingPicture() && cameraView != null) {
            cameraView.focusToPoint(x, y);
        }
    }

    public void toggleDual() {
        if (isDualAvailable && !isTakingPicture() && cameraView != null) {
            cameraView.toggleDual();
            checkActiveFlashMode();
            if (callback != null) {
                callback.onDualToggle(cameraView.isDual());
            }
        }
    }

    public void switchCamera() {
        if (!isCameraSwitchInProgress && !isTakingPicture() && cameraView != null) {
            cameraView.switchCamera();
            if (callback != null) {
                callback.onCameraSwitchRequest();
            }
        }
    }

    public boolean shouldUseDisplayFlash() {
        return cameraView != null &&
            cameraView.isFrontface() &&
            shouldUseFlash(frontFlashMode);
    }

    private boolean shouldUseFlash(@NonNull String mode) {
        return mode.equals(Camera.Parameters.FLASH_MODE_ON) ||
            mode.equals(Camera.Parameters.FLASH_MODE_TORCH) ||
            mode.equals(Camera.Parameters.FLASH_MODE_AUTO) && isLastFrameDark();
    }

    public void startPreview() {
        if (cameraView != null) {
            CameraController.getInstance().startPreview(cameraView.getCameraSessionObject());
        }
    }

    public void stopPreview() {
        if (cameraView != null) {
            CameraController.getInstance().stopPreview(cameraView.getCameraSessionObject());
        }
    }

    public void takePicture(
        boolean isSecretChat,
        boolean showCameraAnimation,
        boolean forceTakeFromTexture,
        @Nullable Runnable onStart
    ) {
        isPreparing = false;

        if (cameraView == null || isTakingPicture) {
            return;
        }

        CameraSessionWrapper cameraSession = cameraView.getCameraSession();
        if (cameraSession == null) {
            return;
        }

        boolean isSameTakePictureOrientation = cameraSession.isSameTakePictureOrientation();
        File outputFile = AndroidUtilities.generatePicturePath(isSecretChat, null);
        if (outputFile == null) {
            return;
        }

        checkActiveFlashMode();

        if (showCameraAnimation) {
            cameraView.startTakePictureAnimation(true);
        }

        if (cameraView.isDual() || forceTakeFromTexture) {
            isTakingPicture = true;

            if (onStart != null) {
                onStart.run();
            }

            String currentFlashMode = cameraView.isFrontface() ? frontFlashMode : backFlashMode;
            boolean enableTorch = cameraSession.isTorchModeSupported() && shouldUseFlash(currentFlashMode);
            if (enableTorch) {
                cameraSession.setCurrentFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                AndroidUtilities.runOnUIThread(() -> {
                    takePictureFromTexture(outputFile, isSameTakePictureOrientation);
                    cameraSession.setCurrentFlashMode(currentFlashMode);
                    isTakingPicture = false;
                }, 2000);
            } else {
                takePictureFromTexture(outputFile, isSameTakePictureOrientation);
                isTakingPicture = false;
            }
        } else {
            isTakingPicture = CameraController.getInstance().takePicture(outputFile, false, cameraView.getCameraSessionObject(), orientation -> {
                isTakingPicture = false;
                try {
                    onPictureReady(outputFile, orientation, isSameTakePictureOrientation);
                } catch (Exception ignore) {

                }
            });
            if (isTakingPicture && onStart != null) {
                onStart.run();
            }
        }
    }

    private void takePictureFromTexture(
        @NonNull File outputFile,
        boolean isSameTakePictureOrientation
    ) {
        Bitmap lastFrame = getLastFrame();
        if (lastFrame != null) {
            try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                lastFrame.compress(Bitmap.CompressFormat.JPEG, 100, out);
                onPictureReady(outputFile, 0, isSameTakePictureOrientation);
            } catch (Exception e) {
                FileLog.e(e);
            }
            lastFrame.recycle();
        }
    }

    public void convertCollageToMedia(
        @NonNull StoryEntry entry,
        boolean isSecretChat,
        @Nullable Runnable onStart,
        @Nullable Runnable onDone
    ) {
        if (entry.wouldBeVideo()) {
            convertCollageToVideo(entry, isSecretChat, onStart, onDone);
        } else {
            convertCollageToPicture(entry, isSecretChat, onStart, onDone);
        }
    }

    private void convertCollageToPicture(
        @NonNull StoryEntry entry,
        boolean isSecretChat,
        @Nullable Runnable onStart,
        @Nullable Runnable onDone
    ) {
        if (callback == null || isBusy()) {
            return;
        }

        isProcessing = true;

        if (onStart != null) {
            onStart.run();
        }

        File outputFile = AndroidUtilities.generatePicturePath(isSecretChat, null);
        if (outputFile != null) {
            entry.buildPhoto(outputFile);
            isProcessing = false;
            if (onDone != null) {
                onDone.run();
            }
            callback.onCollagePictureSuccess(
                outputFile,
                entry.width,
                entry.height,
                entry.orientation
            );
        }

        isProcessing = false;
    }

    private void convertCollageToVideo(
        @NonNull StoryEntry entry,
        boolean isSecretChat,
        @Nullable Runnable onStart,
        @Nullable Runnable onDone
    ) {
        if (callback == null || isBusy()) {
            return;
        }

        isProcessing = true;

        if (onStart != null) {
            onStart.run();
        }

        File outputFile = AndroidUtilities.generateVideoPath(isSecretChat);
        if (outputFile != null) {
            videoBuilder = new DownloadButton.BuildingVideo(
                0,
                entry,
                outputFile,
                () -> {
                    isProcessing = false;
                    videoBuilder = null;
                    if (onDone != null) {
                        onDone.run();
                    }
                    callback.onCollageVideoSuccess(
                        outputFile,
                        generateVideoThumb(outputFile),
                        entry.width,
                        entry.height,
                        entry.duration
                    );
                },
                callback::onCollageConversionProgress,
                () -> callback.onCollageConversionCancel()
            );
            videoBuilder.start();
        }

        isProcessing = false;
    }

    @Nullable
    private String generateVideoThumb(@NonNull File videoFile) {
        Bitmap thumbBitmap = SendMessagesHelper.createVideoThumbnail(
            videoFile.getAbsolutePath(),
            MediaStore.Video.Thumbnails.MINI_KIND
        );
        if (thumbBitmap == null) {
            return null;
        }

        File thumbFile = new File(
            FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
            Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg"
        );
        try (FileOutputStream stream = new FileOutputStream(thumbFile)) {
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }

        SharedConfig.saveConfig();

        ImageLoader.getInstance().putImageToCache(
            new BitmapDrawable(context.getResources(), thumbBitmap),
            Utilities.MD5(thumbFile.getAbsolutePath()),
            false
        );

        return thumbFile.getAbsolutePath();
    }

    public void cancelLatestCollageConversion() {
        if (!isProcessing || videoBuilder == null) {
            return;
        }

        videoBuilder.stop(true);
        isProcessing = false;
        videoBuilder = null;
    }

    private void onPictureReady(
        @NonNull File outputFile,
        int orientation,
        boolean isSameTakePictureOrientation
    ) {
        if (callback != null) {
            BitmapFactory.Options options = decodeBitmap(outputFile.getAbsolutePath());
            callback.onTakePictureSuccess(
                outputFile,
                options.outWidth,
                options.outHeight,
                orientation,
                isSameTakePictureOrientation
            );
        }
    }

    public void startVideoRecord(
        boolean isSecretChat,
        boolean mirror,
        @Nullable Runnable onStart
    ) {
        isPreparing = false;

        if (cameraView == null || isRecordingVideo) {
            return;
        }

        File outputFile = AndroidUtilities.generateVideoPath(isSecretChat);
        if (outputFile == null) {
            return;
        }

        isRecordingVideo = true;
        CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, mirror, ((thumbPath, duration) -> {
            isRecordingVideo = false;
            checkActiveFlashMode();
            try {
                BitmapFactory.Options options = decodeBitmap(thumbPath);
                if (callback != null) {
                    callback.onRecordVideoSuccess(
                        outputFile,
                        thumbPath,
                        options.outWidth,
                        options.outHeight,
                        duration
                    );
                }
            } catch (Exception ignore) {

            }
        }), () -> {
            checkActiveFlashMode();
            if (onStart != null) {
                onStart.run();
            }
        }, cameraView);
    }

    public void stopVideoRecord() {
        if (cameraView == null || !isRecordingVideo) {
            return;
        }

        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionObject(), false);
    }

    @NonNull
    private BitmapFactory.Options decodeBitmap(@NonNull String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(path).getAbsolutePath(), options);
        return options;
    }

    private boolean isLastFrameDark() {
        Bitmap lastFrame = getLastFrame();
        if (lastFrame == null) {
            return false;
        }

        float l = 0;
        final int sx = lastFrame.getWidth() / 12;
        final int sy = lastFrame.getHeight() / 12;
        for (int x = 0; x < 10; ++x) {
            for (int y = 0; y < 10; ++y) {
                l += AndroidUtilities.computePerceivedBrightness(
                    lastFrame.getPixel((1 + x) * sx, (1 + y) * sy)
                );
            }
        }
        l /= 100;
        lastFrame.recycle();
        return l < .22f;
    }

    @Nullable
    public Bitmap getLastFrame() {
        if (cameraView == null) {
            return null;
        }

        TextureView textureView = cameraView.getTextureView();
        if (textureView == null) {
            return null;
        }

        return textureView.getBitmap();
    }


    @Override
    public void onCameraSwitchRequest() {
        isCameraSwitchInProgress = true;
    }

    @Override
    public void onCameraSwitchDone() {
        if (cameraView != null) {
            setZoom(0f, false, true);
            checkActiveFlashMode();
            if (callback != null) {
                callback.onCameraSwitchDone();
            }
        }
        isCameraSwitchInProgress = false;
    }


    public interface Callback {
        void onZoomChanged(float zoom, boolean silent);
        void onFlashModeChanged(@NonNull String flashMode, boolean isFront);
        void onFrontFlashWarmthChanged(float warmth);
        void onFrontFlashIntensityChanged(float intensity);
        void onDualToggle(boolean isDual);
        void onCameraSwitchRequest();
        void onCameraSwitchDone();
        void onTakePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation,
            boolean isSameTakePictureOrientation
        );
        void onRecordVideoSuccess(
            @NonNull File outputFile,
            @NonNull String thumbPath,
            int width,
            int height,
            long duration
        );
        void onCollageConversionProgress(float progress);
        void onCollageConversionCancel();
        void onCollagePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation
        );
        void onCollageVideoSuccess(
            @NonNull File outputFile,
            @Nullable String thumbPath,
            int width,
            int height,
            long duration
        );
    }

}
