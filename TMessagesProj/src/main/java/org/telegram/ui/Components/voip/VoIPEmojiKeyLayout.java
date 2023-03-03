package org.telegram.ui.Components.voip;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CorrectlyMeasuringTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPEmojiKeyLayout extends LinearLayout {

    private static final int BACKGROUND_COLOR = 0x20000000;
    private static final int EMOJI_ITEM_SIZE_DP = 40;
    public static final long EXPAND_ANIMATION_DURATION = 300;
    public static final long COLLAPSE_ANIMATION_DURATION = 200;

    private final LinearLayout emojiLayout;
    private final BackupImageView[] emojiViews = new BackupImageView[4];
    private final Emoji.EmojiDrawable[] staticEmojiDrawables = new Emoji.EmojiDrawable[4];
    private final AnimatedEmojiDrawable[] animatedEmojiDrawables = new AnimatedEmojiDrawable[4];
    private boolean areEmojiLoaded;

    private final HintView hint;
    private boolean shouldShowHint = true;

    private final TextView title;
    private final TextView description;

    private final ValueAnimator stateAnimator = new ValueAnimator();
    private float animatedStateValue;

    private boolean isExpanded = true;


    public VoIPEmojiKeyLayout(@NonNull Context context) {
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

        emojiLayout = new LinearLayout(context);
        emojiLayout.setOrientation(HORIZONTAL);
        emojiLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            BackupImageView emojiView = new BackupImageView(context);
            emojiViews[i] = emojiView;
            emojiLayout.addView(
                emojiView,
                LayoutHelper.createLinear(EMOJI_ITEM_SIZE_DP, EMOJI_ITEM_SIZE_DP, i == 0 ? 0 : 16, 0, 0, 0)
            );
        }
        addView(
            emojiLayout,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16)
        );

        hint = new HintView(context);

        title = new TextView(context);
        title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.WHITE);
        title.setText("This call is end-to-end encrypted.");
        addView(
            title,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4)
        );

        description = new TextView(context);
        description.setGravity(Gravity.CENTER);
        description.setTextColor(Color.WHITE);
        addView(
            description,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
        );

        stateAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            setStateValue(animatedValue);
        });

        collapse(false);
    }


    public boolean areEmojiLoaded() {
        return areEmojiLoaded;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setOnEmojiKeyClickListener(@Nullable OnClickListener l) {
        emojiLayout.setOnClickListener(l);
    }

    public void setCallingUserNameForRationale(@NonNull String userName) {
        description.setText(
            LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, userName)
        );
    }


    public void loadEmojis(int account, @NonNull byte[] encryptedKeySha256) {
        String[] emoji = EncryptionKeyEmojifier.emojifyForCall(encryptedKeySha256);
        for (int i = 0; i < 4; i++) {
            TLRPC.Document document = MediaDataController.getInstance(account).getEmojiAnimatedSticker(emoji[i]);
            if (document != null) {
                AnimatedEmojiDrawable drawable = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, document);
                animatedEmojiDrawables[i] = drawable;
            }

            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable emojiDrawable = Emoji.getEmojiDrawable(emoji[i]);
            if (emojiDrawable != null) {
                emojiDrawable.setBounds(0, 0, AndroidUtilities.dp(EMOJI_ITEM_SIZE_DP), AndroidUtilities.dp(EMOJI_ITEM_SIZE_DP));
                emojiDrawable.preload();

                emojiViews[i].setImageDrawable(emojiDrawable);
                emojiViews[i].setContentDescription(emoji[i]);
                emojiViews[i].setVisibility(View.GONE);
                staticEmojiDrawables[i] = emojiDrawable;
            }
        }
        updateAnimatedEmojiState(false);
        checkEmojiLoaded();
    }

    private void checkEmojiLoaded() {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            Drawable emojiDrawable = staticEmojiDrawables[i];
            if (emojiDrawable != null) {
                count++;
            }
        }

        if (count == 4) {
            areEmojiLoaded = true;
            for (int i = 0; i < 4; i++) {
                if (emojiViews[i].getVisibility() != View.VISIBLE) {
                    emojiViews[i].setVisibility(View.VISIBLE);
                    emojiViews[i].setAlpha(0f);
                    emojiViews[i].setTranslationY(AndroidUtilities.dp(30));
                    emojiViews[i].animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setStartDelay(20 * i);
                }
            }
            showHint();
        }
    }

    public void expand(boolean animated) {
        if (isExpanded || !areEmojiLoaded) {
            return;
        }

        hideHint();

        stateAnimator.cancel();
        if (animated) {
            stateAnimator.setFloatValues(animatedStateValue, 1f);
            stateAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            stateAnimator.setDuration(EXPAND_ANIMATION_DURATION);
            stateAnimator.start();
        } else {
            setStateValue(1f);
        }

        updateAnimatedEmojiState(true);

        isExpanded = true;
    }

    public void collapse(boolean animated) {
        if (!isExpanded) {
            return;
        }

        stateAnimator.cancel();
        if (animated) {
            stateAnimator.setFloatValues(animatedStateValue, 0f);
            stateAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            stateAnimator.setDuration(COLLAPSE_ANIMATION_DURATION);
            stateAnimator.start();
        } else {
            setStateValue(0f);
        }

        updateAnimatedEmojiState(false);

        isExpanded = false;
    }

    private void updateAnimatedEmojiState(boolean animate) {
        // TODO add check if all emoji are animated
        for (int i = 0; i < 4; i++) {
            Emoji.EmojiDrawable staticEmojiDrawable = staticEmojiDrawables[i];
            if (animate) {
                AnimatedEmojiDrawable animatedEmojiDrawable = animatedEmojiDrawables[i];
                if (animatedEmojiDrawable != null) {
                    emojiViews[i].setImageDrawable(null);
                    emojiViews[i].setAnimatedEmojiDrawable(animatedEmojiDrawable);
                } else {
                    emojiViews[i].setImageDrawable(staticEmojiDrawable);
                }
            } else {
                emojiViews[i].setAnimatedEmojiDrawable(null);
                emojiViews[i].setImageDrawable(staticEmojiDrawable);
            }
        }
    }

    private void setStateValue(float stateValue) {
        if (getMeasuredWidth() == 0) {
            getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        setStateValue(stateValue);
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            );
        } else {
            getBackground().setAlpha((int) (stateValue * 255));
            title.setAlpha(stateValue);
            description.setAlpha(stateValue);

            float absoluteCurrentScale = calculateAbsoluteScale(stateValue);
            setScaleX(absoluteCurrentScale);
            setScaleY(absoluteCurrentScale);

            animatedStateValue = stateValue;
        }
    }

    private float calculateAbsoluteScale(@FloatRange(from = 0f, to = 1f) float stateValue) {
        float relativeMinScale = (float) AndroidUtilities.dp(124) / emojiLayout.getMeasuredWidth();
        return relativeMinScale + (1f - relativeMinScale) * stateValue;
    }

    private void showHint() {
        if (!shouldShowHint) {
            return;
        }

        emojiLayout.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    hint.measure(
                        View.MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST)
                    );
                    int left = (getMeasuredWidth() - hint.getMeasuredWidth()) / 2;
                    int top = emojiLayout.getBottom() + AndroidUtilities.dp(32);
                    int right = left + hint.getMeasuredWidth();
                    int bottom = top + hint.getMeasuredHeight();
                    hint.layout(left, top, right, bottom);
                    hint.setCompensateScale(calculateAbsoluteScale(0f));
                    getOverlay().add(hint);
                    hint.show();

                    emojiLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        );
    }

    private void hideHint() {
        if (!shouldShowHint) {
            return;
        }

        shouldShowHint = false;
        hint.hide(() -> getOverlay().remove(hint));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setPivotX(getMeasuredWidth() / 2f);
        setPivotY(0f);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setVisibleToUser(areEmojiLoaded);
    }


    private static class HintView extends FrameLayout {

        private float compensateScale;

        public HintView(@NonNull Context context) {
            super(context);

            ImageView arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.tooltip_arrow_up);
            arrow.setColorFilter(
                new PorterDuffColorFilter(
                    BACKGROUND_COLOR,
                    PorterDuff.Mode.MULTIPLY
                )
            );
            addView(
                arrow,
                LayoutHelper.createFrame(14, 6, Gravity.CENTER_HORIZONTAL)
            );

            CorrectlyMeasuringTextView hint = new CorrectlyMeasuringTextView(context);
            hint.setMaxWidth(AndroidUtilities.dp(310));
            hint.setText(LocaleController.getString(R.string.VoipEncryptionKey));
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hint.setMaxLines(2);
            hint.setGravity(Gravity.CENTER);
            hint.setBackground(
                Theme.createRoundRectDrawable(AndroidUtilities.dp(8), BACKGROUND_COLOR)
            );
            hint.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
            addView(
                hint,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0)
            );
        }


        public void setCompensateScale(float compensateScale) {
            this.compensateScale = compensateScale;
        }

        public void show() {
            setAlpha(0f);
            setScaleX(0f);
            setScaleY(0f);
            animate()
                .alpha(1f)
                .scaleX(1f + compensateScale)
                .scaleY(1f + compensateScale)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .withStartAction(() -> setVisibility(VISIBLE));
        }

        public void hide(@Nullable Runnable onHide) {
            animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .withEndAction(() -> {
                    setVisibility(GONE);
                    if (onHide != null) {
                        onHide.run();
                    }
                });
        }

    }

}
