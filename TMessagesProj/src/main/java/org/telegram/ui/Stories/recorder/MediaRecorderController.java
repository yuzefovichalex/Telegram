package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.Utilities.clamp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;

import java.io.File;
import java.util.ArrayList;

public class MediaRecorderController implements CameraView.Callback {

    private static final String NO_FLASH_MODE = "no_flash_mode";


    @NonNull
    private Context context;

    @Nullable
    private CameraView cameraView;

    @Nullable
    private Callback callback;

    @NonNull
    private final ArrayList<String> frontFlashModes = new ArrayList<>();

    private float zoom;

    @NonNull
    private String frontFlashMode = NO_FLASH_MODE;
    private float frontFlashWarmth = -1f;
    private float frontFlashIntensity = -1f;

    @NonNull
    private String backFlashMode = NO_FLASH_MODE;

    private boolean isPreparing;
    private boolean isTakingPicture;
    private boolean isRecordingVideo;


    public MediaRecorderController(@NonNull Context context) {
        this.context = context;
    }


    public boolean isTakingPicture() {
        return isPreparing || isTakingPicture;
    }

    public boolean isRecordingVideo() {
        return isRecordingVideo;
    }

    public boolean isBusy() {
        return isPreparing || isTakingPicture || isRecordingVideo;
    }

    public boolean isFrontface() {
        return cameraView != null && cameraView.isFrontface();
    }

    public float getFrontFlashWarmth() {
        return frontFlashWarmth;
    }

    public float getFrontFlashIntensity() {
        return frontFlashIntensity;
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
            setZoom(this.zoom, true, true);
        }
    }

    private void checkFlashModes() {
        if (frontFlashModes.isEmpty()) {
            frontFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontFlashModes.add(Camera.Parameters.FLASH_MODE_ON);
        }

        if (frontFlashMode.equals(NO_FLASH_MODE)) {
            int frontFlashModeIdx = clamp(
                MessagesController.getGlobalMainSettings().getInt("frontflash", 1),
                2,
                0
            );
            frontFlashMode = frontFlashModes.get(frontFlashModeIdx);
        }

        if (backFlashMode.equals(NO_FLASH_MODE)) {
            backFlashMode = Camera.Parameters.FLASH_MODE_OFF;
            if (cameraView != null && !cameraView.isFrontface()) {
                CameraSessionWrapper cameraSession = cameraView.getCameraSession();
                if (cameraSession != null) {
                    cameraSession.setCurrentFlashMode(backFlashMode);
                }
            }
        }

        notifyCurrentFlashMode();
    }

    private void checkFrontFlashParams() {
        if (frontFlashWarmth == -1f) {
            frontFlashWarmth = MessagesController.getGlobalMainSettings()
                .getFloat("frontflash_warmth", .9f);
        }

        if (frontFlashIntensity == -1f) {
            frontFlashIntensity = MessagesController.getGlobalMainSettings()
                .getFloat("frontflash_intensity", 1);
        }

        if (callback != null) {
            callback.onFrontFlashWarmthChanged(frontFlashWarmth);
            callback.onFrontFlashIntensityChanged(frontFlashIntensity);
        }
    }

    public void detachCameraView() {
        if (cameraView != null) {
            cameraView.setCallback(null);
        }
        cameraView = null;
        zoom = 0f;
        frontFlashMode = NO_FLASH_MODE;
        frontFlashWarmth = -1f;
        frontFlashIntensity = -1f;
        backFlashMode = NO_FLASH_MODE;
        isPreparing = false;
        isTakingPicture = false;
        isRecordingVideo = false;
    }


    public void setZoomBy(float dZoom) {
        setZoom(zoom + dZoom);
    }

    public void setZoom(float zoom) {
        setZoom(zoom, false, false);
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
        if (!isTakingPicture() && cameraView != null) {
            if (cameraView.isFrontface()) {
                int currentIdx = frontFlashModes.indexOf(frontFlashMode);
                int nextIdx = currentIdx + 1 < frontFlashModes.size() ? currentIdx + 1 : 0;
                setFrontFlashMode(frontFlashModes.get(nextIdx));
            } else {
                CameraSessionWrapper cameraSession = cameraView.getCameraSession();
                if (cameraSession != null) {
                    setBackFlashMode(cameraSession.getNextFlashMode());
                }
            }
        }
    }

    private boolean setFrontFlashMode(@NonNull String flashMode) {
        int modeIdx = frontFlashModes.indexOf(flashMode);
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

    private boolean setBackFlashMode(@NonNull String flashMode) {
        if (cameraView != null) {
            CameraSessionWrapper cameraSession = cameraView.getCameraSession();
            if (cameraSession != null &&
                !cameraSession.getCurrentFlashMode().equals(flashMode)
            ) {
                backFlashMode = flashMode;
                cameraSession.setCurrentFlashMode(flashMode);
                if (callback != null) {
                    callback.onFlashModeChanged(flashMode, false);
                }
                return true;
            }
        }
        return false;
    }

    private void notifyCurrentFlashMode() {
        if (cameraView != null) {
            String activeFlashMode = cameraView.isFrontface() ? frontFlashMode : backFlashMode;
            if (!activeFlashMode.equals(NO_FLASH_MODE) && callback != null) {
                callback.onFlashModeChanged(activeFlashMode, cameraView.isFrontface());
            }
        }
    }

    public void setFrontFlashWarmth(float warmth) {
        if (frontFlashWarmth == warmth) {
            return;
        }

        frontFlashWarmth = warmth;
        if (callback != null) {
            callback.onFrontFlashWarmthChanged(warmth);
        }
    }

    public void setFrontFlashIntensity(float intensity) {
        if (frontFlashIntensity == intensity) {
            return;
        }

        frontFlashIntensity = intensity;
        if (callback != null) {
            callback.onFrontFlashIntensityChanged(intensity);
        }
    }

    public void saveCurrentFrontFlashParams() {
        if (!frontFlashMode.equals(NO_FLASH_MODE) &&
            frontFlashWarmth != -1f &&
            frontFlashIntensity != -1f
        ) {
            MessagesController.getGlobalMainSettings()
                .edit()
                .putFloat("frontflash_warmth", frontFlashWarmth)
                .putFloat("frontflash_intensity", frontFlashIntensity)
                .apply();
        }
    }


    public void focusToPoint(int x, int y) {
        if (!isTakingPicture() && cameraView != null) {
            cameraView.focusToPoint(x, y);
        }
    }

    public void toggleDual() {
        if (!isTakingPicture() && cameraView != null) {
            cameraView.toggleDual();
            if (callback != null) {
                callback.onDualToggle(cameraView.isDual());
            }
        }
    }

    public void switchCamera() {
        if (!isTakingPicture() && cameraView != null) {
            cameraView.switchCamera();
            if (callback != null) {
                callback.onCameraSwitch();
            }
        }
    }

    public boolean shouldUseDisplayFlash() {
        return cameraView != null &&
            cameraView.isFrontface() &&
            (frontFlashMode.equals(Camera.Parameters.FLASH_MODE_ON) ||
                frontFlashMode.equals(Camera.Parameters.FLASH_MODE_AUTO) && isLastFrameDark());
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
        boolean wasFrontFlashUsed
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

        isTakingPicture = CameraController.getInstance().takePicture(outputFile, false, cameraView.getCameraSessionObject(), orientation -> {
            isTakingPicture = false;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(outputFile.getAbsolutePath(), options);
                if (callback != null) {
                    callback.onTakePictureSuccess(
                        outputFile,
                        options.outWidth,
                        options.outHeight,
                        orientation,
                        isSameTakePictureOrientation,
                        wasFrontFlashUsed
                    );
                }
            } catch (Exception ignore) {

            }
        });

        if (!wasFrontFlashUsed) {
            cameraView.startTakePictureAnimation(true);
        }
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
    public void onCameraSwitch() {
        if (cameraView != null) {
            setZoom(0f, false, true);
            boolean changed;
            if (cameraView.isFrontface()) {
                changed = setFrontFlashMode(frontFlashMode);
            } else {
                changed = setBackFlashMode(backFlashMode);
            }
            if (!changed) {
                notifyCurrentFlashMode();
            }
        }
    }


    public interface Callback {
        void onZoomChanged(float zoom, boolean silent);
        void onFlashModeChanged(@NonNull String flashMode, boolean isFront);
        void onFrontFlashWarmthChanged(float warmth);
        void onFrontFlashIntensityChanged(float intensity);
        void onDualToggle(boolean isDual);
        void onCameraSwitch();
        void onTakePictureSuccess(
            @NonNull File outputFile,
            int width,
            int height,
            int orientation,
            boolean isSameTakePictureOrientation,
            boolean wasFrontFlashUsed
        );
    }

}
