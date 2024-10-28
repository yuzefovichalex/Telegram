package org.telegram.ui.Components.share;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

public class ShareDrawable extends Drawable {

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final ImageReceiver imageReceiver = new ImageReceiver();

    private final Path clippingMask = new Path();


    public ShareDrawable() {
        imageReceiver.setRoundRadius(AndroidUtilities.dp(32));
        imageReceiver.setCrossfadeAlpha((byte) 0);
    }


    public void setInfo(int currentAccount, TLRPC.User user) {
        avatarDrawable.setInfo(currentAccount, user);
    }

    public void setInfo(int currentAccount, TLRPC.Chat chat) {
        avatarDrawable.setInfo(currentAccount, chat);
    }

    public void setAvatarType(int type) {
        avatarDrawable.setAvatarType(type);
    }

    public void setForUserOrChat(TLObject object) {
        imageReceiver.setForUserOrChat(object, avatarDrawable);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        clippingMask.rewind();
        clippingMask.addCircle(
            getBounds().centerX(),
            getBounds().centerY(),
            getBounds().height() / 2f,
            Path.Direction.CW
        );
        canvas.clipPath(clippingMask);

        Drawable drawable = imageReceiver.getDrawable() != null
            ? imageReceiver.getDrawable()
            : avatarDrawable;
        drawable.setBounds(getBounds());
        drawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        avatarDrawable.setAlpha(alpha);
        Drawable imageDrawable = imageReceiver.getDrawable();
        if (imageDrawable != null) {
            imageDrawable.setAlpha(alpha);
        }
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

}
