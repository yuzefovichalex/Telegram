package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.SharedConfig;

public class ShadingView extends View {

    public static final int TOP_BOTTOM = 0;
    public static final int BOTTOM_TOP = 1;

    @ColorInt
    private int color = 0x42000000;
    private int solidHeight = 0;
    private int gradientHeight = 0;

    private int orientation = TOP_BOTTOM;

    @NonNull
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @NonNull
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isBlurEnabled;

    @Nullable
    private RenderNode blurRenderNode;

    @NonNull
    private final Rect blurRect = new Rect();

    @NonNull
    private final Paint blurMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private Bitmap blurMaskBitmap;

    public ShadingView(@NonNull Context context) {
        super(context);

        solidPaint.setColor(color);
        blurMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }


    public void setBlurEnabled(boolean enabled) {
        if (isBlurEnabled == enabled) {
            return;
        }

        if (enabled && canUseBlur()) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
            blurRenderNode = new RenderNode("blur_node");
            blurRenderNode.setRenderEffect(
                RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
            );
            isBlurEnabled = true;
        } else {
            setLayerType(LAYER_TYPE_NONE, null);
            blurRenderNode = null;
            isBlurEnabled = false;
        }
    }

    public void setBlurRect(@NonNull Rect rect) {
        if (blurRect.equals(rect)) {
            return;
        }

        blurRect.set(rect);
    }

    private boolean canUseBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.chatBlurEnabled();
    }

    public void setColor(@ColorInt int color) {
        if (this.color != color) {
            this.color = color;
            solidPaint.setColor(color);
            recreateGradient();
            invalidate();
        }
    }

    public void setSolidHeight(int height) {
        if (solidHeight != height) {
            solidHeight = height;
            if (!isInLayout()) {
                requestLayout();
            }
        }
    }

    public void setGradientHeight(int height) {
        if (gradientHeight != height) {
            gradientHeight = height;
            recreateGradient();
            if (!isInLayout()) {
                requestLayout();
            }
        }
    }

    public void setOrientation(int orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            recreateGradient();
            invalidate();
        }
    }

    private void recreateGradient() {
        LinearGradient gradient = new LinearGradient(
            0, orientation == TOP_BOTTOM ? 0 : gradientHeight,
            0, orientation == TOP_BOTTOM ? gradientHeight : 0,
            new int[] { color, Color.TRANSPARENT },
            null,
            Shader.TileMode.CLAMP
        );
        gradientPaint.setShader(gradient);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        int desiredHeight = solidHeight + gradientHeight;
        int finalHeight = Math.min(availableHeight, desiredHeight);
        int finalHeightSpec = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, finalHeightSpec);

        if (isBlurEnabled && getMeasuredWidth() != 0 && getMeasuredHeight() != 0 &&
            (blurMaskBitmap == null ||
                blurMaskBitmap.getWidth() != getMeasuredWidth() ||
                blurMaskBitmap.getHeight() != getMeasuredHeight())
        ) {
            invalidateBlurMask();
        }
    }

    private void invalidateBlurMask() {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        blurMaskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas blurMaskCanvas = new Canvas(blurMaskBitmap);
        LinearGradient alphaGradient = new LinearGradient(
            0, 0, 0, dp(16),
            0x00FFFFFF, 0xFFFFFFFF,
            Shader.TileMode.CLAMP
        );
        Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setShader(alphaGradient);
        blurMaskCanvas.drawRect(0f, 0f, width, height, gradientPaint);
    }

    @Nullable
    public Canvas beginBlurRecording() {
        if (canUseBlur() && blurRenderNode != null && !blurRect.isEmpty()) {
            blurRenderNode.setPosition(blurRect);
            Canvas blurCanvas = blurRenderNode.beginRecording();
            blurCanvas.translate(blurRect.left, -blurRect.top);
            return blurCanvas;
        }
        return null;
    }

    public void endBlurRecording() {
        if (canUseBlur() && blurRenderNode != null) {
            blurRenderNode.endRecording();
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (canUseBlur() && blurRenderNode != null && !blurRect.isEmpty()) {
            canvas.drawRenderNode(blurRenderNode);
            if (blurMaskBitmap != null) {
                canvas.save();
                // TODO add support for TOP_BOTTOM
                canvas.translate(blurRect.left, blurRect.top);
                canvas.drawBitmap(blurMaskBitmap, 0f, 0f, blurMaskPaint);
                canvas.restore();
            }
        }

        if (orientation == TOP_BOTTOM) {
            canvas.drawRect(0f, 0f, getMeasuredWidth(), solidHeight, solidPaint);
            canvas.save();
            canvas.translate(0f, solidHeight);
            canvas.drawRect(0f, 0f, getMeasuredWidth(), gradientHeight, gradientPaint);
            canvas.restore();
        } else {
            canvas.drawRect(0f, 0f, getMeasuredWidth(), gradientHeight, gradientPaint);
            canvas.drawRect(0f, gradientHeight, getMeasuredWidth(), solidHeight + gradientHeight, solidPaint);
        }
    }

}
