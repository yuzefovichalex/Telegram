package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProfileActionButton extends Drawable implements Drawable.Callback {

    private static final int[] STATE_PRESSED =
        new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled };
    private static final int[] STATE_ENABLED = new int[] { android.R.attr.state_enabled };

    @Nullable
    private Drawable background;

    private final int padding = dp(6f);

    @ColorInt
    private int backgroundColor = 0x14FFFFFF;

    @NonNull
    private final Drawable icon;

    @NonNull
    private final String label;

    @NonNull
    private final TextPaint labelPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

    @Nullable
    private StaticLayout labelLayout;

    private boolean isTapObserving;

    @Nullable
    private OnClickListener clickListener;


    public ProfileActionButton(@NonNull Drawable icon, @NonNull String label) {
        this.icon = icon;
        this.label = label;

        icon.setCallback(this);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(11f));

        invalidateBackground();
        invalidateLabelLayout();
    }


    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        invalidateBackground();
    }

    public void setClickListener(@Nullable OnClickListener clickListener) {
        this.clickListener = clickListener;
    }

    private void invalidateBackground() {
        if (background != null) {
            background.setCallback(null);
        }
        background = createBackgroundDrawable();
        background.setCallback(this);
        invalidateSelf();
    }

    @NonNull
    private Drawable createBackgroundDrawable() {
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setColor(backgroundColor);
        backgroundDrawable.setCornerRadius(dp(12f));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return backgroundDrawable;
        }

        GradientDrawable maskDrawable = new GradientDrawable();
        maskDrawable.setColor(Color.WHITE);
        maskDrawable.setCornerRadius(dp(12f));
        return new RippleDrawable(
            ColorStateList.valueOf(0x1AFFFFFF),
            backgroundDrawable,
            maskDrawable
        );
    }

    private void invalidateLabelLayout() {
        int availableWidth = getAvailableWidth();
        if (availableWidth == 0f) {
            labelLayout = null;
            return;
        }

        float desiredWidth = labelPaint.measureText(label);
        float finalWidth = Math.min(desiredWidth, availableWidth);
        CharSequence finalLabel = label;
        if (desiredWidth > availableWidth) {
            finalLabel =
                TextUtils.ellipsize(label, labelPaint, availableWidth, TextUtils.TruncateAt.END);
        }
        labelLayout = new StaticLayout(
            finalLabel,
            labelPaint,
            (int) finalWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1f, 0f, false
        );
        invalidateSelf();
    }

    public boolean onTouchEvent(@NonNull MotionEvent event) {
        boolean handled = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                float y = event.getY();
                if (getBounds().contains((int) x, (int) y)) {
                    isTapObserving = true;
                    handled = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                        background != null
                    ) {
                        background.setHotspot(x, y);
                        background.setState(STATE_PRESSED);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isTapObserving) {
                    handled = true;
                    if (background != null) {
                        background.setState(STATE_ENABLED);
                    }
                    if (clickListener != null) {
                        clickListener.onClick(event.getX(), event.getY());
                    }
                }
                isTapObserving = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                if (isTapObserving) {
                    handled = true;
                    if (background != null) {
                        background.setState(STATE_ENABLED);
                    }
                }
                isTapObserving = false;
                break;

            default:
                if (isTapObserving) {
                    if (getBounds().contains((int) event.getX(), (int) event.getY())) {
                        handled = true;
                    } else {
                        isTapObserving = false;
                        if (background != null) {
                            background.setState(STATE_ENABLED);
                        }
                    }
                }
                break;
        }

        return handled;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        if (labelLayout == null || getAvailableWidth() < labelLayout.getWidth()) {
            invalidateLabelLayout();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int availableWidth = getAvailableWidth();
        int availableHeight = getAvailableHeight();
        int left = getBounds().left + padding;
        int top = getBounds().top + padding;
        int textHeight = 0;
        if (labelLayout != null) {
            textHeight = labelLayout.getHeight();
        }

        if (background != null) {
            background.setBounds(getBounds());
            background.draw(canvas);
        }

        int iconSize = Math.min(
            dp(24f),
            Math.min(availableWidth, Math.max(availableHeight - textHeight, 0))
        );
        float textScale = Math.min((float) iconSize / dp(16f), 1f);
        icon.setBounds(0, 0, iconSize, iconSize);
        canvas.save();
        canvas.translate(
            left + getCenteredOffset(availableWidth, iconSize),
            top +
                getCenteredOffset((int) ((availableHeight - textHeight) / textScale), iconSize) -
                dp(2f)
        );
        icon.draw(canvas);
        canvas.restore();

        StaticLayout labelLayout = this.labelLayout;
        if (labelLayout != null) {
            canvas.save();
            canvas.translate(
                left + getCenteredOffset(availableWidth, labelLayout.getWidth()),
                top + availableHeight - textHeight
            );
            canvas.scale(
                textScale, textScale,
                labelLayout.getWidth() / 2f, labelLayout.getHeight() / 2f
            );
            labelLayout.draw(canvas);
            canvas.restore();
        }
    }

    private int getAvailableWidth() {
        return Math.max(0, getBounds().width() - padding * 2);
    }

    private int getAvailableHeight() {
        return Math.max(0, getBounds().height() - padding * 2);
    }

    private int getCenteredOffset(int parentSize, int childSize) {
        return (parentSize - childSize) / 2;
    }

    public void setAlpha(float alpha) {
        setAlpha((int) (255 * alpha));
    }

    @Override
    public void setAlpha(int alpha) {
        if (background != null) {
            background.setAlpha(alpha);
        }
        icon.setAlpha(alpha);
        labelPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // No-op
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }


    public interface OnClickListener {
        void onClick(float x, float y);
    }

}
