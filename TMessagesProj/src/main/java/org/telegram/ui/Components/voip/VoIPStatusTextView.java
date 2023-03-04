package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPStatusTextView extends FrameLayout {

    LoadingLayout[] statusLayouts = new LoadingLayout[2];
    LoadingLayout reconnectLayout;
    VoIPTimerView timerView;
    private final TextView weakSignalView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    ValueAnimator animator;
    boolean timerShowing;

    public VoIPStatusTextView(@NonNull Context context) {
        super(context);

        for (int i = 0; i < 2; i++) {
            statusLayouts[i] = new LoadingLayout(context);
            addView(statusLayouts[i]);
        }

        reconnectLayout = new LoadingLayout(context);
        reconnectLayout.setText(LocaleController.getString("VoipReconnecting", R.string.VoipReconnecting));
        reconnectLayout.setVisibility(View.GONE);
        addView(reconnectLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));

        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        weakSignalView = new TextView(context);
        weakSignalView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        weakSignalView.setTextColor(Color.WHITE);
        weakSignalView.setText(LocaleController.getString(R.string.VoipWeakSignal));
        weakSignalView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.1f))));
        weakSignalView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        weakSignalView.setVisibility(View.GONE);
        addView(weakSignalView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));
    }

    public void setText(String text, boolean ellipsis, boolean animated) {
        if (TextUtils.isEmpty(statusLayouts[0].getText())) {
            animated = false;
        }

        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            statusLayouts[0].setText(text);
            statusLayouts[0].setVisibility(View.VISIBLE);
            statusLayouts[1].setVisibility(View.GONE);
            timerView.setVisibility(View.GONE);

        } else {
            if (animationInProgress) {
                nextTextToSet = text;
                return;
            }

            if (timerShowing) {
                statusLayouts[0].setText(text);
                statusLayouts[0].setLoadingVisible(ellipsis);
                replaceViews(timerView, statusLayouts[0], null);
            } else {
                if (!statusLayouts[0].getText().equals(text)) {
                    statusLayouts[1].setText(text);
                    statusLayouts[1].setLoadingVisible(ellipsis);
                    replaceViews(statusLayouts[0], statusLayouts[1], () -> {
                        LoadingLayout temp = statusLayouts[0];
                        statusLayouts[0] = statusLayouts[1];
                        statusLayouts[1] = temp;
                    });
                }
            }
        }
    }

    public void showTimer(boolean animated) {
        if (TextUtils.isEmpty(statusLayouts[0].getText())) {
            animated = false;
        }
        if (timerShowing) {
            return;
        }
        timerView.updateTimer();
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            timerShowing = true;
            animationInProgress = false;
            statusLayouts[0].setVisibility(View.GONE);
            statusLayouts[1].setVisibility(View.GONE);
            timerView.setVisibility(View.VISIBLE);
        } else {
            if (animationInProgress) {
                nextTextToSet = "timer";
                return;
            }
            timerShowing = true;
            replaceViews(statusLayouts[0], timerView, null);
        }
    }


    private void replaceViews(View out, View in, Runnable onEnd) {
        out.setVisibility(View.VISIBLE);
        in.setVisibility(View.VISIBLE);

        in.setTranslationY(AndroidUtilities.dp(15));
        in.setAlpha(0f);
        animationInProgress = true;
        animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            float inScale = 0.4f + 0.6f * v;
            float outScale = 0.4f + 0.6f * (1f - v);
            in.setTranslationY(AndroidUtilities.dp(10) * (1f - v));
            in.setAlpha(v);
            in.setScaleX(inScale);
            in.setScaleY(inScale);

            out.setTranslationY(-AndroidUtilities.dp(10) * v);
            out.setAlpha(1f - v);
            out.setScaleX(outScale);
            out.setScaleY(outScale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                out.setVisibility(View.GONE);
                out.setAlpha(1f);
                out.setTranslationY(0);
                out.setScaleY(1f);
                out.setScaleX(1f);

                in.setAlpha(1f);
                in.setTranslationY(0);
                in.setVisibility(View.VISIBLE);
                in.setScaleY(1f);
                in.setScaleX(1f);

                if (onEnd != null) {
                    onEnd.run();
                }
                animationInProgress = false;
                if (nextTextToSet != null) {
                    if (nextTextToSet.equals("timer")) {
                        showTimer(true);
                    } else {
                        statusLayouts[1].setText(nextTextToSet);
                        replaceViews(statusLayouts[0], statusLayouts[1], () -> {
                            LoadingLayout temp = statusLayouts[0];
                            statusLayouts[0] = statusLayouts[1];
                            statusLayouts[1] = temp;
                        });
                    }
                    nextTextToSet = null;
                }
            }
        });
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void setSignalBarCount(int count) {
        timerView.setSignalBarCount(count);
    }

    public void setCallEnded() {
        timerView.setCallEnded();
    }

    public void showReconnect(boolean showReconnecting, boolean animated) {
        if (weakSignalView.getVisibility() == VISIBLE) {
            showWeakSignal(false);
        }

        if (!animated) {
            reconnectLayout.animate().setListener(null).cancel();
            reconnectLayout.setVisibility(showReconnecting ? View.VISIBLE : View.GONE);
        } else {
            if (showReconnecting) {
                if (reconnectLayout.getVisibility() != View.VISIBLE) {
                    reconnectLayout.setVisibility(View.VISIBLE);
                    reconnectLayout.setAlpha(0);
                }
                reconnectLayout.animate().setListener(null).cancel();
                reconnectLayout.animate().alpha(1f).setDuration(150).start();
            } else {
                reconnectLayout.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        reconnectLayout.setVisibility(View.GONE);
                    }
                }).setDuration(150).start();
            }
        }
    }
    
    public void showWeakSignal(boolean showWeakSignal) {
        if (reconnectLayout.getVisibility() == VISIBLE) {
            showReconnect(false, true);
        }

        if (showWeakSignal) {
            if (weakSignalView.getVisibility() != View.VISIBLE) {
                weakSignalView.setVisibility(View.VISIBLE);
                weakSignalView.setAlpha(0);
                weakSignalView.setScaleX(0f);
                weakSignalView.setScaleY(0f);
            }
            weakSignalView.animate().setListener(null).cancel();
            weakSignalView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .start();
        } else {
            weakSignalView.animate()
                .alpha(0)
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .withEndAction(() -> weakSignalView.setVisibility(View.GONE))
                .start();
        }
    }
    
    
    private static class LoadingLayout extends LinearLayout {
        
        private final TextView labelView;
        
        private final View loadingView;
        
        public LoadingLayout(@NonNull Context context) {
            super(context);

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setLayoutTransition(new LayoutTransition());
            
            labelView = new TextView(context);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            labelView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
            labelView.setTextColor(Color.WHITE);
            labelView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(labelView);

            loadingView = new View(context);
            LoadingDrawable loadingDrawable = new LoadingDrawable();
            loadingView.setBackground(loadingDrawable);
            LayoutParams loadingViewLP = new LayoutParams(
                loadingDrawable.getIntrinsicWidth(),
                loadingDrawable.getIntrinsicHeight()
            );
            loadingViewLP.setMargins(
                AndroidUtilities.dp(4),
                AndroidUtilities.dp(2),
                0,
                0
            );
            addView(loadingView, loadingViewLP);
        }
        
        @NonNull
        public CharSequence getText() {
            CharSequence text = labelView.getText();
            return text != null ? text : "";
        }
        
        public void setText(@Nullable CharSequence text) {
            labelView.setText(text);
        }
        
        public void setLoadingVisible(boolean isVisible) {
            loadingView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
        
    }

}
