package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.GroupCallActivity;

public class VoIPUserPhotoView extends View {

    private final ImageReceiver avatarImageReceiver = new ImageReceiver();
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();

    private final BlobDrawable tinyWaveDrawable;
    private final BlobDrawable bigWaveDrawable;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float amplitude;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;
    private float cx, cy;

    public VoIPUserPhotoView(@NonNull Context context) {
        super(context);

        avatarImageReceiver.setImage(null, null, avatarDrawable, null, 0);

        tinyWaveDrawable = new BlobDrawable(9);
        bigWaveDrawable = new BlobDrawable(12);

        tinyWaveDrawable.minRadius = AndroidUtilities.dp(88);
        tinyWaveDrawable.maxRadius = AndroidUtilities.dp(96);
        tinyWaveDrawable.generateBlob();

        bigWaveDrawable.minRadius = AndroidUtilities.dp(104);
        bigWaveDrawable.maxRadius = AndroidUtilities.dp(108);
        bigWaveDrawable.generateBlob();

        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (255 * 0.1f));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float size = AndroidUtilities.dp(157);
        cx = getMeasuredWidth() >> 1;
        cy = (getMeasuredHeight() >> 1) + (GroupCallActivity.isLandscapeMode ? 0 : -getMeasuredHeight() * 0.12f);
        avatarImageReceiver.setRoundRadius((int) (size / 2f));
        avatarImageReceiver.setImageCoords(cx - size / 2, cy - size / 2, size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (animateToAmplitude != amplitude) {
            amplitude += animateAmplitudeDiff * 16;
            if (animateAmplitudeDiff > 0) {
                if (amplitude > animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            } else {
                if (amplitude < animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            }
        }

        float scale = 1f + 0.8f * amplitude;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);

        bigWaveDrawable.update(amplitude, 1f);
        tinyWaveDrawable.update(amplitude, 1f);

        bigWaveDrawable.draw(cx, cy, canvas, paint);
        tinyWaveDrawable.draw(cx, cy, canvas, paint);
        canvas.restore();

        scale = 1f + 0.2f * amplitude;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        avatarImageReceiver.draw(canvas);
        canvas.restore();

        invalidate();
    }

    public void setUserInfo(TLRPC.User user) {
        if (user != null) {
            avatarDrawable.setInfo(user.id, user.first_name, user.last_name, null);
        }
    }

    public void setAmplitude(double value) {
        float amplitude = (float) value / 80f;
        if (amplitude > 1f) {
            amplitude = 1f;
        } else if (amplitude < 0) {
            amplitude = 0;
        }
        animateToAmplitude = amplitude;
        animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImageReceiver.onDetachedFromWindow();
    }
}
