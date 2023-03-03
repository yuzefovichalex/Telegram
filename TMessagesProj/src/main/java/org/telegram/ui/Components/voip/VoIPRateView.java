package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class VoIPRateView extends LinearLayout {

    private static final int BACKGROUND_COLOR = 0x20000000;

    private static final int STAR_COUNT = 5;

    private static final int STAR_SIZE_DP = 32;
    private static final int HIGH_RATE_EFFECT_SIZE_DP = STAR_SIZE_DP * 4;

    private final LinearLayout starsLayout;
    private final CheckableRLottieImageView[] starViews = new CheckableRLottieImageView[STAR_COUNT];

    private final RLottieImageView highRateEffectView;

    private int lastHighRatePosition;
    private final Runnable removeHighRateEffect = new Runnable() {
        @Override
        public void run() {
            getOverlay().remove(highRateEffectView);
        }
    };

    private int rate;

    @Nullable
    private OnRateChangeListener onRateChangeListener;


    public VoIPRateView(@NonNull Context context) {
        super(context);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(
            AndroidUtilities.dp(24),
            AndroidUtilities.dp(24),
            AndroidUtilities.dp(24),
            AndroidUtilities.dp(24)
        );
        setBackground(
            Theme.createRoundRectDrawable(AndroidUtilities.dp(24), BACKGROUND_COLOR)
        );
        setClipChildren(false);
        setClipToPadding(false);

        TextView title = new TextView(context);
        title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.WHITE);
        title.setText("Rate this call");
        addView(
            title,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4)
        );

        TextView description = new TextView(context);
        description.setGravity(Gravity.CENTER);
        description.setTextColor(Color.WHITE);
        description.setText("Please rate the quality of this call.");
        addView(
            description,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12)
        );

        starsLayout = new LinearLayout(context);
        starsLayout.setOrientation(HORIZONTAL);
        starsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        starsLayout.setClipChildren(false);
        addView(
            starsLayout,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
        );

        for (int i = 0; i < STAR_COUNT; i++) {
            int position = i;
            CheckableRLottieImageView starView = new CheckableRLottieImageView(context);
            starView.setAnimation(R.raw.check_star, STAR_SIZE_DP, STAR_SIZE_DP);
            starView.setOnClickListener(view -> handleClick(position));
            starViews[i] = starView;
            int leftMargin = i != 0 ? STAR_SIZE_DP / 4 : 0;
            starsLayout.addView(
                starView,
                LayoutHelper.createLinear(STAR_SIZE_DP, STAR_SIZE_DP, leftMargin, 0, 0, 0)
            );
        }

        highRateEffectView = new RLottieImageView(context);
        highRateEffectView.setAnimation(R.raw.high_rate_star_effect, HIGH_RATE_EFFECT_SIZE_DP, HIGH_RATE_EFFECT_SIZE_DP);

        setVisibility(GONE);
    }


    public void setOnRateChangeListener(@Nullable OnRateChangeListener onRateChangeListener) {
        this.onRateChangeListener = onRateChangeListener;
    }

    public void show() {
        if (getVisibility() == VISIBLE) {
            return;
        }

        setAlpha(0f);
        setScaleX(0.25f);
        setScaleY(0.25f);
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(CubicBezierInterpolator.DEFAULT)
            .setDuration(400)
            .withStartAction(() -> setVisibility(VISIBLE));

        for (int i = 0; i < STAR_COUNT; i++) {
            View starView = starsLayout.getChildAt(i);
            if (starView != null) {
                starView.setAlpha(0.25f);
                starView.setScaleX(0.25f);
                starView.setScaleY(0.25f);
                starView.setTranslationY(starView.getMeasuredHeight() + getPaddingBottom());
                starView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setDuration(250)
                    .setStartDelay(150 / STAR_COUNT * i);
            }
        }
    }

    private void handleClick(int position) {
        if (position < 0 || position >= STAR_COUNT) {
            return;
        }

        if (rate != position + 1) {
            if (rate < position + 1) {
                int lastUncheckedPosition = 0;
                for (int i = 0; i < STAR_COUNT; i++) {
                    if (!starViews[i].isChecked()) {
                        lastUncheckedPosition = i;
                        break;
                    }
                }
                for (int i = lastUncheckedPosition; i <= position; i++) {
                    setStarChecked(i, true, 30 * i);
                }
            } else {
                int lastCheckedPosition = STAR_COUNT - 1;
                for (int i = STAR_COUNT - 1; i >= 0; i--) {
                    if (starViews[i].isChecked()) {
                        lastCheckedPosition = i;
                        break;
                    }
                }
                for (int i = lastCheckedPosition; i > position; i--) {
                    setStarChecked(i, false, 150 - 30 * i);
                }
            }
            if (rate > position + 1 || position < STAR_COUNT - 2) {
                shakeStar(position, 150);
            } else {
                showHighRateEffectOnStar(position);
            }
        } else {
            for (int i = 0; i <= position; i++) {
                shakeStar(i, 30 * i);
            }
        }

        rate = position + 1;

        if (onRateChangeListener != null) {
            onRateChangeListener.onRateChange(rate);
        }
    }

    private void setStarChecked(int position, boolean checked, int delay) {
        AndroidUtilities.runOnUIThread(() -> starViews[position].setChecked(checked), delay);
    }

    private void shakeStar(int position, int startDelay) {
        CheckableRLottieImageView starView = starViews[position];
        SpringAnimation shakeAnimation = new SpringAnimation(starView, DynamicAnimation.TRANSLATION_Y, 0f);
        shakeAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        shakeAnimation.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);
        starView.animate()
            .translationY(-starView.getMeasuredHeight() / 2f)
            .setStartDelay(startDelay)
            .withEndAction(shakeAnimation::start);
    }

    private void showHighRateEffectOnStar(int position) {
        AndroidUtilities.cancelRunOnUIThread(removeHighRateEffect);
        highRateEffectView.stopAnimation();
        getOverlay().remove(highRateEffectView);

        if (highRateEffectView.getMeasuredWidth() == 0) {
            highRateEffectView.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HIGH_RATE_EFFECT_SIZE_DP), MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HIGH_RATE_EFFECT_SIZE_DP), MeasureSpec.EXACTLY)
            );
        }

        if (lastHighRatePosition != position) {
            int[] thisViewLocation = new int[2];
            getLocationInWindow(thisViewLocation);

            CheckableRLottieImageView starView = starViews[position];
            int[] starViewLocation = new int[2];
            starView.getLocationInWindow(starViewLocation);

            int starViewCenterX = starViewLocation[0] - thisViewLocation[0] + starView.getMeasuredWidth() / 2;
            int starViewCenterY = starViewLocation[1] - thisViewLocation[1] + starView.getMeasuredHeight() / 2;

            int left = starViewCenterX - highRateEffectView.getMeasuredWidth() / 2;
            int top = starViewCenterY - highRateEffectView.getMeasuredHeight() / 2;
            int right = left + highRateEffectView.getMeasuredWidth();
            int bottom = top + highRateEffectView.getMeasuredHeight();
            highRateEffectView.layout(left, top, right, bottom);
        }

        getOverlay().add(highRateEffectView);
        highRateEffectView.setProgress(0f);
        highRateEffectView.playAnimation();

        AndroidUtilities.runOnUIThread(removeHighRateEffect, highRateEffectView.getDuration());

        lastHighRatePosition = position;
    }


    private static class CheckableRLottieImageView extends RLottieImageView implements Checkable {

        private boolean isChecked;

        public CheckableRLottieImageView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setChecked(boolean checked) {
            if (checked == isChecked) {
                return;
            }

            stopAnimation();
            setReverse(!checked);
            // check_star lottie animation 2 seconds of non-animated state.
            // When reverse (uncheck), go to 16/180 frame to start from it.
            setProgress(checked ? 0f : 0.09f);
            playAnimation();

            startScaleAnimation();

            isChecked = checked;
        }

        @Override
        public boolean isChecked() {
            return isChecked;
        }

        @Override
        public void toggle() {
            setChecked(!isChecked);
        }

        private void startScaleAnimation() {
            SpringAnimation scaleXAnimation = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
            scaleXAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            scaleXAnimation.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
            SpringAnimation scaleYAnimation = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);
            scaleYAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            scaleYAnimation.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
            scaleXAnimation.addUpdateListener((animation, value, velocity) -> scaleYAnimation.animateToFinalPosition(value));
            animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .withEndAction(scaleXAnimation::start);
        }

    }

    public interface OnRateChangeListener {
        void onRateChange(int rate);
    }

}
