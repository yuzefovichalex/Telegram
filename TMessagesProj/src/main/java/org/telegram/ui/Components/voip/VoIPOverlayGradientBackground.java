package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;

public class VoIPOverlayGradientBackground extends FrameLayout {

    private static final int STATE_INITIATING = 1;
    private static final int STATE_ESTABLISHED = 2;
    private static final int STATE_WEAK_SIGNAL = 3;

    private final View initiatingCallBackground;
    private final MotionBackgroundDrawable initiatingCallBackgroundDrawable;
    private final ValueAnimator initiatingCallBackgroundAnimator;

    private final View establishedCallBackground;
    private final MotionBackgroundDrawable establishedCallBackgroundDrawable;
    private final ValueAnimator establishedCallBackgroundAnimator;
    private Animator establishedCallBackgroundRevealAnimator;

    private final View weakSignalBackground;
    private final MotionBackgroundDrawable weakSignalBackgroundDrawable;
    private final ValueAnimator weakSignalBackgroundAnimator;

    private int state = STATE_INITIATING;
    

    public VoIPOverlayGradientBackground(@NonNull Context context) {
        super(context);

        initiatingCallBackground = new View(context);
        initiatingCallBackgroundDrawable = new MotionBackgroundDrawable();
        initiatingCallBackgroundDrawable.setIndeterminateAnimation(true);
        initiatingCallBackgroundDrawable.setParentView(initiatingCallBackground);
        initiatingCallBackground.setBackground(initiatingCallBackgroundDrawable);

        initiatingCallBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f);
        initiatingCallBackgroundAnimator.setRepeatCount(ValueAnimator.INFINITE);
        initiatingCallBackgroundAnimator.setRepeatMode(ValueAnimator.REVERSE);
        initiatingCallBackgroundAnimator.setDuration(10000);
        initiatingCallBackgroundAnimator.addUpdateListener(animation -> {
            float blendRatio = (float) animation.getAnimatedValue();
            int color1 = ColorUtils.blendARGB(0xff20A4D7, 0xff08B0A3, blendRatio);
            int color2 = ColorUtils.blendARGB(0xff3F8BEA, 0xff17AAE4, blendRatio);
            int color3 = ColorUtils.blendARGB(0xff8148EC, 0xff3B7AF1, blendRatio);
            int color4 = ColorUtils.blendARGB(0xffB456D8, 0xff4576E9, blendRatio);
            initiatingCallBackgroundDrawable.setColors(color1, color2, color3, color4, 0, false);
            initiatingCallBackgroundDrawable.updateAnimation(true);
        });

        addView(initiatingCallBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        establishedCallBackground = new View(context);
        establishedCallBackgroundDrawable = new MotionBackgroundDrawable();
        establishedCallBackgroundDrawable.setIndeterminateAnimation(true);
        establishedCallBackgroundDrawable.setParentView(establishedCallBackground);
        establishedCallBackgroundDrawable.setColors(0xffA9CC66, 0xff5AB147, 0xff07BA63, 0xff07A9AC, 0, true);
        establishedCallBackground.setBackground(establishedCallBackgroundDrawable);

        establishedCallBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f, 2f);
        establishedCallBackgroundAnimator.setRepeatCount(ValueAnimator.INFINITE);
        establishedCallBackgroundAnimator.setRepeatMode(ValueAnimator.REVERSE);
        establishedCallBackgroundAnimator.setDuration(10000);;
        establishedCallBackgroundAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            float blendRatio = animatedValue <= 1f ? animatedValue : animatedValue - 1f;
            int color1;
            int color2;
            int color3;
            int color4;
            if (animatedValue <= 1f) {
                color1 = ColorUtils.blendARGB(0xffA9CC66, 0xff08B0A3, blendRatio);
                color2 = ColorUtils.blendARGB(0xff5AB147, 0xff17AAE4, blendRatio);
                color3 = ColorUtils.blendARGB(0xff07BA63, 0xff3B7AF1, blendRatio);
                color4 = ColorUtils.blendARGB(0xff07A9AC, 0xff4576E9, blendRatio);
            } else {
                color1 = ColorUtils.blendARGB(0xff08B0A3, 0xff20A4D7, blendRatio);
                color2 = ColorUtils.blendARGB(0xff17AAE4, 0xff3F8BEA, blendRatio);
                color3 = ColorUtils.blendARGB(0xff3B7AF1, 0xff8148EC, blendRatio);
                color4 = ColorUtils.blendARGB(0xff4576E9, 0xffB456D8, blendRatio);
            }
            establishedCallBackgroundDrawable.setColors(color1, color2, color3, color4, 0, false);
            establishedCallBackgroundDrawable.updateAnimation(true);
        });

        addView(establishedCallBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        establishedCallBackground.setVisibility(View.INVISIBLE);

        weakSignalBackground = new View(context);
        weakSignalBackgroundDrawable = new MotionBackgroundDrawable();
        weakSignalBackgroundDrawable.setIndeterminateAnimation(true);
        weakSignalBackgroundDrawable.setParentView(weakSignalBackground);
        weakSignalBackgroundDrawable.setColors(0xFFDB904C, 0xFFDB904C, 0xFFE7618F, 0xFFE86958, 0, true);
        weakSignalBackground.setBackground(weakSignalBackgroundDrawable);

        weakSignalBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f);
        weakSignalBackgroundAnimator.setRepeatCount(ValueAnimator.INFINITE);
        weakSignalBackgroundAnimator.setRepeatMode(ValueAnimator.REVERSE);
        weakSignalBackgroundAnimator.setDuration(10000);
        weakSignalBackgroundAnimator.addUpdateListener(animation -> {
            weakSignalBackgroundDrawable.updateAnimation(true);
        });

        addView(weakSignalBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        weakSignalBackground.setVisibility(View.INVISIBLE);
    }


    public void handleCallInitialize() {
        if (!initiatingCallBackgroundAnimator.isStarted()) {
            initiatingCallBackgroundAnimator.start();
        }
    }

    public void handleCallEstablish(int cx, int cy, float startRadius) {
        if (establishedCallBackground.getVisibility() == View.VISIBLE ||
            establishedCallBackgroundRevealAnimator != null && establishedCallBackgroundRevealAnimator.isStarted()
        ) {
            return;
        }
        
        establishedCallBackgroundRevealAnimator = ViewAnimationUtils.createCircularReveal(
            establishedCallBackground,
            cx, cy,
            startRadius,
            getMeasuredHeight()
        );
        establishedCallBackgroundRevealAnimator.addListener(
            new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    initiatingCallBackgroundAnimator.cancel();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    establishedCallBackgroundAnimator.start();
                    removeView(initiatingCallBackground);
                }
            }
        );
        int color1 = 0xffA9CC66;
        int color2 = 0xff5AB147;
        int color3 = 0xff07BA63;
        int color4 = 0xff07A9AC;
        establishedCallBackgroundDrawable.setColors(color1, color2, color3, color4, 0, true);
        establishedCallBackground.setVisibility(View.VISIBLE);
        establishedCallBackgroundRevealAnimator.start();

        state = STATE_ESTABLISHED;
    }

    public void setWeakState(boolean isShowing) {
        if (isShowing && state == STATE_WEAK_SIGNAL || !isShowing && state != STATE_WEAK_SIGNAL) {
            return;
        }

        weakSignalBackground.setVisibility(View.VISIBLE);
        if (isShowing) {
            establishedCallBackgroundAnimator.pause();
            weakSignalBackground.setAlpha(0f);
            weakSignalBackground.animate()
                .alpha(1f)
                .withEndAction(weakSignalBackgroundAnimator::start);
            state = STATE_WEAK_SIGNAL;
        } else {
            weakSignalBackground.setAlpha(1f);
            weakSignalBackground.animate()
                .alpha(0f)
                .withEndAction(() -> {
                    establishedCallBackgroundAnimator.resume();
                    weakSignalBackground.setVisibility(View.INVISIBLE);
            });
            state = STATE_ESTABLISHED;
        }
    }

}
