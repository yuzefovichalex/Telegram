package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.profile.AvatarImageView;
import org.telegram.ui.profile.LiquidAvatarRenderer;
import org.telegram.ui.profile.ProfileActionButton;
import org.telegram.ui.profile.ProfileGiftsController;
import org.telegram.ui.profile.ProfileStarGiftsPattern;
import org.telegram.ui.profile.ShadingView;

import java.util.ArrayList;
import java.util.List;

// TODO:
//  - Maybe migrate from padding to separate collapsed/expanded offset (padding is too problematic)
//  - Check stories integration
//  - Another avatar position on screen open/close
//  - Add action button animation support (add/remove buttons)
//  - Invite links for restricted calls
public class ProfileHeader extends FrameLayout {

    private static final int SHADING_COLOR = 0x42000000;
    private static final int ON_PHOTO_ACTION_BUTTON_CONTENT_COLOR = Color.WHITE;
    private static final int ON_PHOTO_ACTION_BUTTON_BG_COLOR = 0x21000000;

    @NonNull
    private final Rect tmpRect = new Rect();

    @NonNull
    private final RectF tmpRectF = new RectF();

    @NonNull
    private final int[] tmpIntArr1 = new int[2];

    @NonNull
    private final int[] tmpIntArr2 = new int[2];

    @NonNull
    private final AvatarImageView avatarImageView;

    private final int defaultCollapsedAvatarSize = AndroidUtilities.dp(42f);
    private int collapsedAvatarSize = defaultCollapsedAvatarSize;
    private final int avatarSize = AndroidUtilities.dp(92f);

    private int collapsedAvatarOffsetLeft, collapsedAvatarTop;

    @NonNull
    private final SimpleTextView nameTextView;

    @NonNull
    private final TextView statusTextView;

    @NonNull
    private final ShadingView topShadingView;

    @NonNull
    private final ShadingView bottomShadingView;

    private int leftActionButtonsOffset, rightActionButtonsOffset;

    private float expandCollapseProgress;
    private float avatarExpandCollapseProgress;

    private int offsetLeft, offsetRight;

    private final int linesOffset = dp(1.3f);
    private final int actionButtonHeight = dp(56f);
    private final int actionButtonSpacing = dp(8f);
    private final int contentSpacing = dp(12f);

    private int collapsedHeight = AndroidUtilities.dp(56f);
    private int expandedHeight = AndroidUtilities.dp(144f);
    private int linesHeight;
    private int buttonGroupHeight;

    private int actionBarColor = Color.WHITE;
    private int onActionBarColor = Color.BLACK;
    private int actionButtonIconColor = Color.WHITE;
    private int lastActionButtonContentColor = actionButtonIconColor;
    private int actionButtonBackgroundColor = Color.TRANSPARENT;
    private int lastActionButtonBackgroundColor = actionButtonBackgroundColor;
    private boolean isActionBarColorPeer;

    @NonNull
    private final ProfileStarGiftsPattern starGiftsPattern = new ProfileStarGiftsPattern(this);

    @NonNull
    private final List<ProfileActionButton> actionButtons = new ArrayList<>();

    private boolean shouldDrawAvatarLiquidBackground;

    @NonNull
    private final LiquidAvatarRenderer liquidAvatarRenderer;

    @Nullable
    private Bitmap avatarLiquidBackground;

    @Nullable
    private Bitmap blurredAvatarBitmap;

    @NonNull
    private final Paint blurredAvatarPaint = new Paint();

    private int avatarBlurRounds;

    private final Path avatarBackgroundPath = new Path();
    private final Paint avatarBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private Path catchedCutoutPath;

    @NonNull
    private Rect catchedCutoutBoundingRect = new Rect();

    @Nullable
    private Path cutoutPath;

    @NonNull
    private final Rect cutoutRect = new Rect();

    private boolean isCutoutCalculated;

    @Nullable
    private Callback callback;

    @Nullable
    private ProfileGiftsController giftsController;


    public ProfileHeader(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClipToPadding(false);

        avatarImageView = new AvatarImageView(context) {
            @Override
            public void draw(@NonNull Canvas canvas) {
                super.draw(canvas);
                Canvas blurCanvas = beginBottomBlurRecording();
                if (blurCanvas != null) {
                    int[] avatarLocation = tmpIntArr1;
                    int[] blurLocation = tmpIntArr2;
                    getLocationInWindow(avatarLocation);
                    bottomShadingView.getLocationInWindow(blurLocation);

                    float drawX = avatarLocation[0] - blurLocation[0];
                    float drawY = avatarLocation[1] - blurLocation[1];

                    blurCanvas.save();
                    blurCanvas.translate(drawX, drawY);
                    blurCanvas.scale(getScaleX(), getScaleY());
                    super.draw(blurCanvas);
                    blurCanvas.restore();

                    endBottomBlurRecording();
                }
            }
        };
        avatarImageView.setRoundRadius(dp(48f));
        addView(avatarImageView, new LayoutParams(avatarSize, avatarSize));

        topShadingView = new ShadingView(context);
        topShadingView.setOrientation(ShadingView.TOP_BOTTOM);
        topShadingView.setColor(SHADING_COLOR);
        topShadingView.setGradientHeight(ActionBar.getCurrentActionBarHeight() + dp(16f));
        topShadingView.setAlpha(0f);
        addView(topShadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        bottomShadingView = new ShadingView(context);
        bottomShadingView.setOrientation(ShadingView.BOTTOM_TOP);
        bottomShadingView.setColor(SHADING_COLOR);
        bottomShadingView.setAlpha(0f);
        bottomShadingView.setBlurEnabled(true);
        addView(bottomShadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        nameTextView = new SimpleTextView(context);
        nameTextView.setWidthWrapContent(true);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setTextSize(18);
        nameTextView.setTextColor(Color.WHITE);
        nameTextView.setScrollNonFitText(true);
        nameTextView.setEllipsizeByGradient(true);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        statusTextView = new TextView(context);
        //statusTextView.setWidthWrapContent(true);
        statusTextView.setTextSize(14);
        statusTextView.setTextColor(Color.WHITE);
        statusTextView.setMaxLines(1);
        statusTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        starGiftsPattern.setDefaultAvatarSize(avatarSize);
        starGiftsPattern.setCallback(gift ->
            Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug)
        );

        liquidAvatarRenderer = new LiquidAvatarRenderer(context);
        liquidAvatarRenderer.setCallback(bmp -> {
            if (shouldDrawAvatarLiquidBackground()) {
                avatarLiquidBackground = bmp;
                invalidate();
            }
        });

        avatarBackgroundPath.addCircle(avatarSize / 2f, avatarSize / 2f, avatarSize / 2f, Path.Direction.CW);
        avatarBackgroundPaint.setColor(Color.BLACK);

        invalidateActionButtonsColors(true);
    }


    public void initGiftsController(int currentAccount, long dialogId) {
        if (giftsController != null) {
            giftsController.detach();
        }
        giftsController = new ProfileGiftsController(this, currentAccount, dialogId);
        giftsController.setCallback(() -> {
            starGiftsPattern.setGifts(giftsController.gifts);
        });
        invalidate();
    }

    public void updateGifts() {
        if (giftsController != null) {
            giftsController.update();
        }
    }

    @Nullable
    public Canvas beginBottomBlurRecording() {
        return bottomShadingView.beginBlurRecording();
    }

    public void endBottomBlurRecording() {
        bottomShadingView.endBlurRecording();
    }

    public float getBottomShadingY() {
        return bottomShadingView.getY();
    }

    @Nullable
    public ImageReceiver getAvatarImageReceiver() {
        return avatarImageView.getImageReceiver();
    }

    public void getAvatarLocation(int[] loc) {
        avatarImageView.getLocationInWindow(loc);
    }

    public void setOccupyStatusBar(boolean occupy) {
        topShadingView.setSolidHeight(occupy ? AndroidUtilities.statusBarHeight : 0);
    }

    public void setCollapsedHeight(int collapsedHeight) {
        if (this.collapsedHeight != collapsedHeight) {
            this.collapsedHeight = collapsedHeight;
            requestLayout();
        }
    }

    public void setExpandedHeight(int expandedHeight) {
        if (this.expandedHeight != expandedHeight) {
            this.expandedHeight = expandedHeight;
            requestLayout();
        }
    }

    // TODO try to not use outside
    @NonNull
    public AvatarImageView getAvatarImageView() {
        return avatarImageView;
    }

    public void setAvatarVisibility(int visibility) {
        avatarImageView.setVisibility(visibility);
    }

    public void setDrawAvatarForeground(boolean draw) {
        avatarImageView.drawForeground(draw);
    }

    public void setAvatarsViewPager(@NonNull ProfileGalleryView avatarsViewPager) {
        avatarImageView.setAvatarsViewPager(avatarsViewPager);
    }

    public void setAvatarImageDrawable(@Nullable Drawable drawable) {
        avatarImageView.setImageDrawable(drawable);
    }

    public void setAvatarImage(
        @Nullable ImageLocation mediaLocation,
        @Nullable String mediaFilter,
        @Nullable Drawable thumb,
        @Nullable Object parentObject
    ) {
        avatarImageView.setImage(mediaLocation, mediaFilter, thumb, parentObject);
    }

    public void setAvatarImage(
        @Nullable ImageLocation mediaLocation,
        @Nullable String mediaFilter,
        @Nullable ImageLocation imageLocation,
        @Nullable String imageFilter,
        @Nullable Drawable thumb,
        @Nullable Object parentObject
    ) {
        avatarImageView.setImage(
            mediaLocation,
            mediaFilter,
            imageLocation,
            imageFilter,
            thumb,
            parentObject
        );
    }

    public void setAvatarForegroundImage(
        @Nullable ImageLocation location,
        @Nullable String filter,
        @Nullable Drawable thumb
    ) {
        avatarImageView.setForegroundImage(location, filter, thumb);
    }

    public void setAvatarForegroundImage(
        @Nullable ImageReceiver.BitmapHolder holder
    ) {
        avatarImageView.setForegroundImageDrawable(holder);
    }

    public void setLeftActionButtonsOffset(int offset) {
        if (leftActionButtonsOffset == offset) {
            return;
        }

        leftActionButtonsOffset = offset;
        requestLayout();
    }

    public void setRightActionButtonsOffset(int offset) {
        if (rightActionButtonsOffset == offset) {
            return;
        }

        rightActionButtonsOffset = offset;
        requestLayout();
    }

    // TODO do not use outside
    @NonNull
    public SimpleTextView getNameTextView() {
        return nameTextView;
    }

    public boolean setName(@NonNull CharSequence name) {
        boolean changed = nameTextView.setText(name);
        if (changed) {
            requestLayout();
        }
        return changed;
    }

    public void setNameAlpha(float alpha) {
        nameTextView.setAlpha(alpha);
    }

    public void setNameVisibility(int visibility) {
        nameTextView.setVisibility(visibility);
    }

    public void setNameColor(@ColorInt int color) {
        nameTextView.setTextColor(color);
    }

    public boolean setRightDrawable(@Nullable Drawable drawable) {
        return nameTextView.setRightDrawable(drawable);
    }

    public void setRightDrawableOutside(boolean outside) {
        nameTextView.setRightDrawableOutside(outside);
    }

    public void setRightDrawableOnClick(@Nullable OnClickListener clickListener) {
        nameTextView.setRightDrawableOnClick(clickListener);
    }

    public boolean setRightDrawable2(@Nullable Drawable drawable) {
        return nameTextView.setRightDrawable2(drawable);
    }

    public void setLeftDrawable(@Nullable Drawable drawable) {
        nameTextView.setLeftDrawable(drawable);
    }

    public void setLeftDrawableOutside(boolean outside) {
        nameTextView.setLeftDrawableOutside(outside);
    }

    @Nullable
    public CharSequence getStatus() {
        return statusTextView.getText();
    }

    public boolean setStatus(@NonNull CharSequence status) {
        statusTextView.setText(status);
        if (false) {
            requestLayout();
        }
        return false;
    }

    public void setStatusColor(@ColorInt int color) {
        statusTextView.setTextColor(color);
    }

    @Keep
    public void setStatusAlpha(float alpha) {
        statusTextView.setAlpha(alpha);
    }

    public void setStatusVisibility(int visibility) {
        statusTextView.setVisibility(visibility);
    }

    public void setStatusClickListener(@Nullable OnClickListener listener) {
        Drawable background = null;
        if (listener != null) {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(actionButtonBackgroundColor);
            gradientDrawable.setCornerRadius(dp(8f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                GradientDrawable maskDrawable = new GradientDrawable();
                maskDrawable.setColor(Color.WHITE);
                maskDrawable.setCornerRadius(dp(12f));
                background = new RippleDrawable(
                    ColorStateList.valueOf(0x1AFFFFFF),
                    gradientDrawable,
                    maskDrawable
                );
            } else {
                background = gradientDrawable;
            }
            statusTextView.setPadding(dp(8), 2, dp(8), 2);
        } else {
            statusTextView.setPadding(0, 0, 0, 0);
        }
        statusTextView.setOnClickListener(listener);
        statusTextView.setBackground(background);
    }

    public void setStarGiftsPattern(long emojiId, boolean animated) {
        starGiftsPattern.setEmoji(emojiId, animated);
    }

    public void setStarGiftsPatternColor(@ColorInt int color) {
        starGiftsPattern.setEmojiColor(color);
    }

    public void setStarGiftsBackgroundColor(@ColorInt int color) {
        starGiftsPattern.setBackgroundColor(color);
    }

    public void setActionBarColor(@ColorInt int color, boolean isPeer) {
        if (actionBarColor == color && isActionBarColorPeer == isPeer) {
            return;
        }

        actionBarColor = color;
        isActionBarColorPeer = isPeer;
        invalidateActionButtonsColors(true);
    }

    public void setOnActionBarColor(@ColorInt int color) {
        if (onActionBarColor == color) {
            return;
        }

        onActionBarColor = color;
        invalidateActionButtonsColors(true);
    }

    private void invalidateActionButtonsColors(boolean recalculate) {
        if (recalculate) {
            int overlayColor;
            float blendRatio;
            int alpha = 59;
            if (Theme.isCurrentThemeDark() && !isActionBarColorPeer) {
                actionButtonIconColor = Color.WHITE;
                overlayColor = Color.WHITE;
                blendRatio = .3f;
            } else {
                if (ColorUtils.calculateLuminance(actionBarColor) > .9f) {
                    actionButtonIconColor = onActionBarColor;
                    overlayColor = onActionBarColor;
                    blendRatio = 1f;
                    alpha = 29;
                } else {
                    actionButtonIconColor = Color.WHITE;
                    overlayColor = Color.BLACK;
                    blendRatio = .5f;
                }
            }
            int blendedColor = ColorUtils.blendARGB(actionBarColor, overlayColor, blendRatio);
            actionButtonBackgroundColor = ColorUtils.setAlphaComponent(blendedColor, alpha);
        }

        if (actionButtons.isEmpty()) {
            return;
        }

        lastActionButtonContentColor = ColorUtils.blendARGB(
            actionButtonIconColor,
            ON_PHOTO_ACTION_BUTTON_CONTENT_COLOR,
            avatarExpandCollapseProgress
        );
        lastActionButtonBackgroundColor = ColorUtils.blendARGB(
            actionButtonBackgroundColor,
            ON_PHOTO_ACTION_BUTTON_BG_COLOR,
            avatarExpandCollapseProgress
        );
        for (int i = 0; i < actionButtons.size(); i++) {
            ProfileActionButton button = actionButtons.get(i);
            button.setContentColor(lastActionButtonContentColor);
            button.setBackgroundColor(lastActionButtonBackgroundColor);
        }
    }

    @NonNull
    public ProfileActionButton addAction(
        @DrawableRes int iconResId,
        @NonNull String label,
        @Nullable ProfileActionButton.OnClickListener clickListener
    ) {
        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        if (icon != null) {
            icon = icon.mutate();
        } else {
            icon = new ColorDrawable(Color.WHITE);
        }
        return addAction(icon, label, clickListener);
    }

    @NonNull
    public ProfileActionButton addAction(
        @NonNull Drawable icon,
        @NonNull String label,
        @Nullable ProfileActionButton.OnClickListener clickListener
    ) {
        ProfileActionButton button = new ProfileActionButton();
        button.setIcon(icon);
        button.setLabel(label);
        button.setClickListener(clickListener);
        return addAction(button);
    }

    @NonNull
    public ProfileActionButton addAction(@NonNull ProfileActionButton button) {
        button.setContentColor(lastActionButtonContentColor);
        button.setBackgroundColor(lastActionButtonBackgroundColor);
        button.setCallback(this);
        boolean needLayout = actionButtons.isEmpty();
        actionButtons.add(button);
        if (needLayout) {
            buttonGroupHeight = actionButtonHeight + contentSpacing;
            requestLayout();
        }
        invalidate();
        return button;
    }

    public void clearAllActions(boolean invalidate) {
        actionButtons.clear();
        buttonGroupHeight = 0;
        if (invalidate) {
            requestLayout();
            invalidate();
        }
    }

    public void setAvatarExpandCollapseProgress(float progress) {
        if (avatarExpandCollapseProgress == progress) {
            return;
        }

        avatarExpandCollapseProgress = progress;

        topShadingView.setAlpha(progress);
        topShadingView.setVisibility(progress > 0f ? VISIBLE : GONE);
        bottomShadingView.setAlpha(progress);
        bottomShadingView.setVisibility(progress > 0f ? VISIBLE : GONE);

        int avatarRadius = lerp(avatarImageView.getMeasuredWidth() / 2, 0, progress);
        avatarImageView.setRoundRadius(avatarRadius);
        // TODO recheck with open/close animation
        //avatarImageView.setForegroundAlpha(progress);

        float namePivotX = lerp(nameTextView.getMeasuredWidth() / 2f, 0f, progress);
        float namePivotY = lerp(
            nameTextView.getMeasuredHeight() / 2f,
            nameTextView.getMeasuredHeight(),
            progress
        );
        nameTextView.setPivotX(namePivotX);
        nameTextView.setPivotY(namePivotY);

        starGiftsPattern.setAlpha(1f - progress);

        invalidateActionButtonsColors(false);

        requestLayout();
        invalidate();
    }

    public float getExpandCollapseProgress() {
        return expandCollapseProgress;
    }

    public void setExpandCollapseProgress(float progress) {
        if (expandCollapseProgress == progress) {
            return;
        }

        expandCollapseProgress = progress;

        int avatarSize = lerp(
            collapsedAvatarSize,
            this.avatarSize,
            Math.min(progress, 1.33f)
        );
        ViewGroup.LayoutParams lp = avatarImageView.getLayoutParams();
        lp.width = avatarSize;
        lp.height = avatarSize;
        avatarImageView.setLayoutParams(lp);
        avatarImageView.setRoundRadius((int) (avatarSize / 2f * (1f - avatarExpandCollapseProgress)));

        float avatarAlpha = shouldDrawAvatarLiquidBackground && progress <= 1f
            ? thresholdRemap(.5f, .75f, progress)
            : 1f;
        avatarImageView.setAlpha(avatarAlpha);

        float nameScale = progress <= 1f
            ? lerp(1f, 1.12f, progress)
            : lerp(1.12f, 1.67f, Math.min(progress - 1f, 1f));
        nameTextView.setScaleX(nameScale);
        nameTextView.setScaleY(nameScale);

        if (progress <= 1f) {
            starGiftsPattern.setExpandCollapseProgress(progress);

            float buttonsAlpha = Math.max(progress - .33f, 0f) / .67f;
            for (int i = 0; i < actionButtons.size(); i++) {
                actionButtons.get(i).setAlpha(buttonsAlpha);
            }
        }

        requestLayout();
        invalidate();
    }

    private float thresholdRemap(float n, float m, float x) {
        return clamp((x - n) / (m - n), 1f, 0f);
    }

    public void setDrawAvatarLiquidBackground(boolean draw) {
        if (shouldDrawAvatarLiquidBackground != draw) {
            shouldDrawAvatarLiquidBackground = draw;
        }
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        boolean verified = false;
        for (int i = 0; i < actionButtons.size(); i++) {
            verified = who == actionButtons.get(i);
            if (verified) {
                break;
            }
        }
        return verified || super.verifyDrawable(who);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        liquidAvatarRenderer.init();

        // TODO unfortunately the path is not correct on most devices (e.g. samsung, oneplus).
        //  Works on pixel
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            DisplayCutout cutout = getRootWindowInsets().getDisplayCutout();
//            if (cutout != null) {
//                catchedCutoutPath = cutout.getCutoutPath();
//                catchedCutoutBoundingRect.set(cutout.getBoundingRectTop());
//            }
//        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        liquidAvatarRenderer.release();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int allowedHeight = MeasureSpec.getSize(heightMeasureSpec);
        int contentHeight = expandCollapseProgress <= 1f
            ? lerp(collapsedHeight, expandedHeight, expandCollapseProgress)
            : lerp(expandedHeight, width - verticalPadding, expandCollapseProgress - 1f);
        int desiredHeight = verticalPadding + contentHeight;
        int finalHeight = Math.min(allowedHeight, desiredHeight);
        int heightSpec = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY);

        super.onMeasure(widthMeasureSpec, heightSpec);

        invalidateAvatarScale();

        offsetLeft = calculateMinOffset(leftActionButtonsOffset, getPaddingLeft());
        offsetRight = calculateMinOffset(rightActionButtonsOffset, getPaddingRight());
        int horizontalPadding = getPaddingLeft() + getPaddingRight();
        // Minus horizontal padding since it's already included in measureChildWithMargins method
        int usedWidth = offsetLeft + offsetRight - horizontalPadding;

        int scaledWidth = Math.round(width / nameTextView.getScaleX());
        int scaledWidthSpec = MeasureSpec.makeMeasureSpec(scaledWidth, MeasureSpec.AT_MOST);
        measureChildWithMargins(
            nameTextView,
            scaledWidthSpec,
            usedWidth,
            heightMeasureSpec,
            0
        );
        measureChildWithMargins(statusTextView, widthMeasureSpec, usedWidth, heightMeasureSpec, 0);
        linesHeight = nameTextView.getMeasuredHeight() + linesOffset + statusTextView.getMeasuredHeight();

        if (topShadingView.getVisibility() == VISIBLE) {
            measureChildWithMargins(
                topShadingView,
                widthMeasureSpec,
                -horizontalPadding, // Do not include padding
                heightMeasureSpec,
                0
            );
        }

        if (bottomShadingView.getVisibility() == VISIBLE) {
            bottomShadingView.setSolidHeight((int) (linesHeight * .4f) + contentSpacing + buttonGroupHeight);
            bottomShadingView.setGradientHeight((int) (linesHeight * 1.35f));
            measureChildWithMargins(
                bottomShadingView,
                widthMeasureSpec,
                -horizontalPadding, // Do not include padding
                heightMeasureSpec,
                0
            );
            if (buttonGroupHeight > 0) {
                tmpRect.set(
                    0,
                    bottomShadingView.getMeasuredHeight() - buttonGroupHeight - contentSpacing * 2,
                    bottomShadingView.getMeasuredWidth(),
                    bottomShadingView.getMeasuredHeight()
                );
            } else {
                tmpRect.set(0, 0, 0, 0);
            }
            bottomShadingView.setBlurRect(tmpRect);
        }

        tmpRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        starGiftsPattern.setBounds(tmpRect);

        if (!isCutoutCalculated) {
            calculateCutoutPath();
            if (cutoutPath != null) {
                liquidAvatarRenderer.setShape(cutoutPath, cutoutRect);
            }
            isCutoutCalculated = true;
        }

        liquidAvatarRenderer.setSize(getMeasuredWidth(), getMeasuredWidth());
    }

    private void calculateCutoutPath() {
        if (catchedCutoutPath != null && !catchedCutoutPath.isEmpty()) {
            cutoutPath = catchedCutoutPath;
            if (cutoutPath != null) {
                cutoutRect.set(intersectRectAndPath(catchedCutoutBoundingRect, cutoutPath));
            }
        }

        int defaultCollapseLeft = (getMeasuredWidth() - defaultCollapsedAvatarSize) / 2;
        int defaultCollapseRight = defaultCollapseLeft + defaultCollapsedAvatarSize;
        if (cutoutPath == null || cutoutRect.isEmpty() ||
            cutoutRect.right < defaultCollapseLeft ||
            cutoutRect.left > defaultCollapseRight
        ) {
            cutoutRect.set(0, 0, getMeasuredWidth(), 1);
            cutoutPath = new Path();
            tmpRectF.set(cutoutRect);
            cutoutPath.addRect(tmpRectF, Path.Direction.CW);
            collapsedAvatarSize = dp(42f);
            collapsedAvatarTop = -collapsedAvatarSize - dp(12f);
        } else {
            collapsedAvatarSize = 0;
            collapsedAvatarOffsetLeft = cutoutRect.centerX() - getMeasuredWidth() / 2;
            collapsedAvatarTop = cutoutRect.centerY() - collapsedAvatarSize / 2;
        }
    }

    @NonNull
    public Rect intersectRectAndPath(@NonNull Rect rect, @NonNull Path path) {
        Region clipRegion = new Region(rect);
        Region pathRegion = new Region();
        pathRegion.setPath(path, clipRegion);
        Region intersectionRegion = new Region(pathRegion);
        intersectionRegion.op(rect, Region.Op.INTERSECT);
        if (!intersectionRegion.isEmpty()) {
            return intersectionRegion.getBounds();
        } else {
            return new Rect();
        }
    }

    private int calculateMinOffset(int actionsOffset, int minOffset) {
        return lerp(
            Math.max(actionsOffset, minOffset),
            minOffset,
            Math.min(expandCollapseProgress, 1f)
        );
    }

    private void invalidateAvatarScale() {
        float avatarWidth = avatarImageView.getMeasuredWidth();
        if (avatarWidth == 0) {
            return;
        }

        float avatarScale = lerp(
            1f,
            (float) Math.max(getMeasuredWidth(), getMeasuredHeight()) / avatarWidth,
            avatarExpandCollapseProgress
        );
        avatarImageView.setScaleX(avatarScale);
        avatarImageView.setScaleY(avatarScale);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (topShadingView.getVisibility() == VISIBLE) {
            topShadingView.layout(
                0, 0,
                topShadingView.getMeasuredWidth(), topShadingView.getMeasuredHeight()
            );
        }

        if (bottomShadingView.getVisibility() == VISIBLE) {
            int bsvTop = getMeasuredHeight() - bottomShadingView.getMeasuredHeight();
            int bsvBottom = bsvTop + bottomShadingView.getMeasuredHeight();
            bottomShadingView.layout(0, bsvTop, bottomShadingView.getMeasuredWidth(), bsvBottom);
        }

        int nameWidth = nameTextView.getMeasuredWidth();
        int nameHeight = nameTextView.getMeasuredHeight();
        int statusWidth = statusTextView.getMeasuredWidth();
        int statusHeight = statusTextView.getMeasuredHeight();
        int availableParentWidth = getMeasuredWidth() - offsetLeft - offsetRight;

        float factor;
        if (expandCollapseProgress <= 1f) {
            factor = expandCollapseProgress;
        } else if (expandCollapseProgress > 1.33f || avatarExpandCollapseProgress > 0f) {
            factor = 1f - avatarExpandCollapseProgress;
        } else {
            factor = 1f;
        }

        int nameLeft = offsetLeft +
            (int) (getCenteredOffset(availableParentWidth, nameWidth) * factor);
        int nameTop = expandCollapseProgress <= 1f
            ? lerp(
                getPaddingTop() + getCenteredOffset(collapsedHeight, linesHeight),
                getPaddingTop() + expandedHeight - linesHeight - contentSpacing - buttonGroupHeight,
                Math.min(expandCollapseProgress, 1f)
            )
            : getMeasuredHeight() - linesHeight - contentSpacing - buttonGroupHeight;
        int nameRight = nameLeft + nameWidth;
        int nameBottom = nameTop + nameHeight;
        nameTextView.layout(nameLeft, nameTop, nameRight, nameBottom);

        int statusLeft = offsetLeft +
            (int) (getCenteredOffset(availableParentWidth, statusWidth) * factor);
        int statusTop = nameBottom + linesOffset;
        int statusRight = statusLeft + statusWidth;
        int statusBottom = statusTop + statusHeight;
        statusTextView.layout(statusLeft, statusTop, statusRight, statusBottom);
        notifyStatusPositionChanged(statusLeft, statusTop);

        int avatarTopOffset = lerp(0, getPaddingTop(), factor);
        // Center in full container height (factor = 0) or in half expanded space.
        int expandedAvatarTop = getCenteredOffset(
            lerp(
                getMeasuredHeight(),
                expandedHeight - linesHeight - contentSpacing - buttonGroupHeight,
                factor
            ),
            avatarImageView.getMeasuredHeight()
        );
        int avatarLeft = lerp(collapsedAvatarOffsetLeft, 0, Math.min(expandCollapseProgress, 1f)) +
            getCenteredOffset(getMeasuredWidth(), avatarImageView.getMeasuredWidth());
        int avatarTop = lerp(
            collapsedAvatarTop,
            avatarTopOffset + expandedAvatarTop,
            Math.min(1f + .33f * factor, expandCollapseProgress)
        );
        int avatarRight = avatarLeft + avatarImageView.getMeasuredWidth();
        int avatarBottom = avatarTop + avatarImageView.getMeasuredHeight();
        avatarImageView.layout(avatarLeft, avatarTop, avatarRight, avatarBottom);
        dispatchAvatarRectChanged();
    }

    private int getCenteredOffset(int parentSize, int childSize) {
        return (parentSize - childSize) / 2;
    }

    private void notifyStatusPositionChanged(float x, float y) {
        if (callback != null) {
            callback.onStatusPositionChanged(x, y);
        }
    }

    private void dispatchAvatarRectChanged() {
        tmpRectF.set(0f, 0f, avatarImageView.getMeasuredWidth(), avatarImageView.getMeasuredHeight());
        avatarImageView.getMatrix().mapRect(tmpRectF);
        tmpRectF.offset(avatarImageView.getLeft(), avatarImageView.getTop());

        if (shouldDrawAvatarLiquidBackground()) {
            liquidAvatarRenderer.requestRender(
                tmpRectF.centerX(),
                tmpRectF.centerY(),
                (tmpRectF.width() - dp(1f)) / 2f
            );
        }

        starGiftsPattern.setAvatarBounds(tmpRectF);

        if (callback != null) {
            callback.onAvatarRectChanged(tmpRectF);
        }
    }

    private boolean shouldDrawAvatarLiquidBackground() {
        return shouldDrawAvatarLiquidBackground &&
            expandCollapseProgress < 1f &&
            avatarImageView.getTop() - cutoutRect.bottom < dp(20f);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;
        for (int i = 0; i < actionButtons.size(); i++) {
            handled = actionButtons.get(i).onTouchEvent(event);
            if (handled) {
                break;
            }
        }

        if (!handled) {
            handled = starGiftsPattern.onTouchEvent(event);
        }

        return handled || super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        starGiftsPattern.draw(canvas);

        if (shouldDrawAvatarLiquidBackground() &&
            expandCollapseProgress > 0 &&
            avatarLiquidBackground != null
        ) {
            canvas.drawBitmap(avatarLiquidBackground, 0, 0, null);
        } else {
            avatarLiquidBackground = null;
        }

        if (blurredAvatarBitmap == null) {
            blurredAvatarBitmap = Bitmap.createBitmap(avatarSize, avatarSize, Bitmap.Config.ARGB_8888);

        }
        ImageReceiver ir = avatarImageView.getImageReceiver();
        if (avatarBlurRounds < 3 && (ir.hasImageLoaded() || ir.isThumbOnly()) && expandCollapseProgress == 1f) {
            Canvas c = new Canvas(blurredAvatarBitmap);
            int r = avatarImageView.getImageReceiver().getRoundRadius()[0];
            avatarImageView.getImageReceiver().setRoundRadius(0);
            avatarImageView.getImageReceiver().draw(c);
            avatarImageView.getImageReceiver().setRoundRadius(r);
            Utilities.stackBlurBitmap(blurredAvatarBitmap, dp(20));
            avatarBlurRounds++;
        }

        // TODO for testing cutout
//        Paint paint = new Paint();
//        paint.setColor(Color.RED);
//        canvas.drawRect(cutoutRect, paint);

        if (shouldDrawAvatarLiquidBackground && expandCollapseProgress <= 1f) {
            int a = (int) (thresholdRemap(.3f, .65f, expandCollapseProgress) * 255);
            blurredAvatarPaint.setAlpha(a);
            canvas.save();
            canvas.translate(avatarImageView.getLeft(), avatarImageView.getTop());
            float avatarScale = (float) avatarImageView.getMeasuredWidth() / avatarSize;
            canvas.scale(avatarScale, avatarScale);
            if (expandCollapseProgress <= .65f) {
                canvas.drawPath(avatarBackgroundPath, avatarBackgroundPaint);
            }
            canvas.clipPath(avatarBackgroundPath);
            canvas.drawBitmap(blurredAvatarBitmap, 0, 0, blurredAvatarPaint);
            canvas.restore();
        }

        super.dispatchDraw(canvas);

        int actionButtonCount = actionButtons.size();
        if (actionButtonCount == 0) {
            return;
        }

        int availableWidth = getMeasuredWidth() - dp(12f) * 2;
        int actionButtonWidth =
            (availableWidth - actionButtonSpacing * (actionButtonCount - 1)) / actionButtonCount;
        int actionButtonHeight =
            (int) (Math.min(expandCollapseProgress, 1f) * this.actionButtonHeight);
        int bottomOffset = lerp(
            contentSpacing / 2,
            contentSpacing,
            Math.min(expandCollapseProgress, 1f)
        );
        int actionButtonTop = getMeasuredHeight() - bottomOffset - actionButtonHeight;
        for (int i = 0; i < actionButtonCount; i++) {
            ProfileActionButton button = actionButtons.get(i);
            int left = dp(12f) + (actionButtonSpacing + actionButtonWidth) * i;
            button.setBounds(
                left, actionButtonTop,
                left + actionButtonWidth, actionButtonTop + actionButtonHeight
            );
            button.draw(canvas);
        }
    }


    public interface Callback {
        void onStatusPositionChanged(float x, float y);
        void onAvatarRectChanged(@NonNull RectF rect);
    }

}
