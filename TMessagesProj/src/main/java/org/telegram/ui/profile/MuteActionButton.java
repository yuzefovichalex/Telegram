package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RLottieDrawable;

public class MuteActionButton extends ProfileActionButton {

    private boolean isInitialized;

    private boolean isMuted;

    @NonNull
    private final RLottieDrawable muteDrawable;

    @NonNull
    private final RLottieDrawable unmuteDrawable;

    @Nullable
    private RLottieDrawable muteUnmuteDrawable;


    public MuteActionButton(boolean isMuted) {
        muteDrawable = new RLottieDrawable(
            R.raw.anim_profilemute,
            "profile_mute",
            dp(40f),
            dp(40f),
            true,
            null
        );

        unmuteDrawable = new RLottieDrawable(
            R.raw.anim_profileunmute,
            "profile_unmute",
            dp(40f),
            dp(40f),
            true,
            null
        );

        setIconPadding(-dp(6f));
        setMuted(isMuted, false);
    }


    @Override
    public void setIcon(@Nullable Drawable icon) {
        if (icon == muteDrawable || icon == unmuteDrawable) {
            super.setIcon(icon);
        }
    }

    public void setMuted(boolean muted, boolean animated) {
        if (isMuted == muted && isInitialized) {
            return;
        }

        isMuted = muted;

        setLabel(muted ? "Unmute" : "Mute");
        if (!isInitialized) {
            setIcon(muteUnmuteDrawable = muted ? unmuteDrawable : muteDrawable);
            isInitialized = true;
            return;
        }

        if (muteUnmuteDrawable != null) {
            muteUnmuteDrawable.stop();
            muteUnmuteDrawable.setProgress(0f);
        }

        setIcon(muteUnmuteDrawable = muted ? muteDrawable : unmuteDrawable);
        if (animated) {
            muteUnmuteDrawable.start();
        } else {
            muteUnmuteDrawable.setProgress(1f);
        }
    }

    @Override
    public void setContentColor(int color) {
        if (contentColor == color) {
            return;
        }

        contentColor = color;

        ColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        muteDrawable.setColorFilter(colorFilter);
        unmuteDrawable.setColorFilter(colorFilter);

        setLabelColor(color);
    }
}
