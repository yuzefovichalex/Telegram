package org.telegram.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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

    private static final int DEFAULT_BACKGROUND_COLOR = 0x1AFFFFFF;

    protected int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    protected int contentColor = Color.WHITE;

    @NonNull
    private final GradientDrawable backgroundContentDrawable;

    @NonNull
    private final Drawable background;

    private final int padding = dp(6f);

    @Nullable
    private Drawable icon;

    private final int iconSize = dp(24f);

    private int iconPaddingLeft, iconPaddingTop, iconPaddingRight, iconPaddingBottom;

    @Nullable
    private String label;

    @NonNull
    private final TextPaint labelPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

    @Nullable
    private StaticLayout labelLayout;

    private boolean isTapObserving;

    @Nullable
    private OnClickListener clickListener;


    public ProfileActionButton() {
        backgroundContentDrawable = new GradientDrawable();
        backgroundContentDrawable.setColor(backgroundColor);
        backgroundContentDrawable.setCornerRadius(dp(12f));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable maskDrawable = new GradientDrawable();
            maskDrawable.setColor(Color.WHITE);
            maskDrawable.setCornerRadius(dp(12f));
            background = new RippleDrawable(
                ColorStateList.valueOf(0x1AFFFFFF),
                backgroundContentDrawable,
                maskDrawable
            );
        } else {
            background = backgroundContentDrawable;
        }
        background.setCallback(this);

        labelPaint.setColor(contentColor);
        labelPaint.setTextSize(dp(11f));
    }


    public int getContentColor() {
        return contentColor;
    }

    public void setContentColor(int color) {
        if (contentColor == color) {
            return;
        }

        contentColor = color;

        if (icon != null) {
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }

        setLabelColor(color);
    }

    protected void setLabelColor(@ColorInt int color) {
        if (labelPaint.getColor() == color) {
            return;
        }

        labelPaint.setColor(color);
        invalidateSelf();
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(@ColorInt int color) {
        if (backgroundColor == color) {
            return;
        }

        backgroundColor = color;
        backgroundContentDrawable.setColor(color);
    }

    @Nullable
    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(@Nullable Drawable icon) {
        if (this.icon == icon) {
            return;
        }

        if (this.icon != null) {
            this.icon.setCallback(null);
        }

        this.icon = icon;

        if (icon != null) {
            icon.setCallback(this);
        }

        invalidateSelf();
    }

    public void setIconPadding(int padding) {
        setIconPadding(padding, padding, padding, padding);
    }

    public void setIconPadding(int left, int top, int right, int bottom) {
        iconPaddingLeft = left;
        iconPaddingTop = top;
        iconPaddingRight = right;
        iconPaddingBottom = bottom;
        invalidateSelf();
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public void setLabel(@Nullable String label) {
        if (TextUtils.equals(this.label, label)) {
            return;
        }

        this.label = label;
        invalidateLabelLayout();
    }

    public void setVisible(boolean isVisible) {
        setVisible(isVisible, false);
    }

    public void cancelRipple() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            background instanceof RippleDrawable
        ) {
            background.setState(STATE_ENABLED);
            background.jumpToCurrentState();
        }
    }

    public void setClickListener(@Nullable OnClickListener clickListener) {
        this.clickListener = clickListener;
    }

    private void invalidateLabelLayout() {
        int availableWidth;
        if (label == null || (availableWidth = getAvailableWidth()) == 0f) {
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
        if (!isVisible()) {
            return false;
        }

        boolean handled = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                float y = event.getY();
                if (getBounds().contains((int) x, (int) y)) {
                    isTapObserving = true;
                    handled = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        background.setHotspot(x, y);
                        background.setState(STATE_PRESSED);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isTapObserving) {
                    handled = true;
                    background.setState(STATE_ENABLED);
                    if (clickListener != null) {
                        clickListener.onClick(event.getX(), event.getY());
                    }
                }
                isTapObserving = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                if (isTapObserving) {
                    handled = true;
                    background.setState(STATE_ENABLED);
                }
                isTapObserving = false;
                break;

            default:
                if (isTapObserving) {
                    if (getBounds().contains((int) event.getX(), (int) event.getY())) {
                        handled = true;
                    } else {
                        isTapObserving = false;
                        background.setState(STATE_ENABLED);
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
        if (!isVisible() || getBounds().width() == 0 || getBounds().height() == 0) {
            return;
        }

        int availableWidth = getAvailableWidth();
        int availableHeight = getAvailableHeight();
        int left = getBounds().left + padding;
        int top = getBounds().top + padding;
        int textHeight = 0;
        if (labelLayout != null) {
            textHeight = labelLayout.getHeight();
        }

        background.setBounds(getBounds());
        background.draw(canvas);

        int currentIconSize = Math.min(
            iconSize,
            Math.min(availableWidth, Math.max(availableHeight - textHeight, 0))
        );
        float iconScale = (float) currentIconSize / iconSize;
        float textScale = Math.min((float) currentIconSize / dp(8f), 1f);

        Drawable icon = this.icon;
        if (icon != null && icon.getAlpha() > 0) {
            icon.setBounds(
                iconPaddingLeft,
                iconPaddingTop,
                iconSize - iconPaddingRight,
                iconSize - iconPaddingBottom
            );
            canvas.save();
            canvas.translate(
                left + getCenteredOffset(availableWidth, currentIconSize),
                top +
                    getCenteredOffset(
                        (int) ((availableHeight - textHeight) / textScale),
                        currentIconSize
                    ) -
                    dp(2f)
            );
            canvas.scale(iconScale, iconScale);
            icon.draw(canvas);
            canvas.restore();
        }

        StaticLayout labelLayout = this.labelLayout;
        if (labelLayout != null && labelPaint.getAlpha() > 0) {
            canvas.save();
            canvas.translate(
                left + getCenteredOffset(availableWidth, labelLayout.getWidth()),
                top + availableHeight - textHeight
            );
            canvas.scale(
                lerp(.85f, 1f, textScale), textScale,
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
        background.setAlpha(alpha);
        if (icon != null) {
            icon.setAlpha(alpha);
        }
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
