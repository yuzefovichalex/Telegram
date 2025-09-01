package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class ChatThemeBottomSheetAlertHeader extends LinearLayout {

    private final int currentAccount;
    private final FrameLayout giftContainer;
    private final BackupImageView giftImageView;
    private final AvatarDrawable avatarDrawable;
    private final BackupImageView avatarImageView;
    private final TextView messageView;

    public ChatThemeBottomSheetAlertHeader(@NonNull Context context, int currentAccount, boolean isDark) {
        super(context);
        this.currentAccount = currentAccount;
        setOrientation(VERTICAL);
        setPadding(dp(24), dp(4), dp(24), dp(8));
        setGravity(Gravity.CENTER_HORIZONTAL);
        setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        addView(ll, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        giftContainer = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            giftContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), dp(12));
                }
            });
            giftContainer.setClipToOutline(true);
        }
        ll.addView(giftContainer, LayoutHelper.createLinear(56, 56));
        giftImageView = new BackupImageView(context);
        giftImageView.getImageReceiver().setCrossfadeWithOldImage(true);
        giftImageView.getImageReceiver().setAllowStartLottieAnimation(false);
        giftImageView.getImageReceiver().setAutoRepeat(0);

        giftContainer.addView(giftImageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.chats_undo);
        iconView.setColorFilter(new PorterDuffColorFilter(isDark ? Color.WHITE : Color.BLACK, PorterDuff.Mode.SRC_IN));
        iconView.setAlpha(.5f);
        ll.addView(iconView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 0));

        avatarDrawable = new AvatarDrawable();
        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(dp(28));
        ll.addView(avatarImageView, LayoutHelper.createLinear(56, 56));

        messageView = new TextView(context);
        messageView.setTextColor(isDark ? Color.WHITE : Color.BLACK);
        messageView.setAlpha(.81f);
        addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));
    }

    public void setGift(TL_stars.StarGift gift) {
        final TL_stars.starGiftAttributePattern pattern = findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);
        final TL_stars.starGiftAttributeModel model = findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class);
        final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
        giftContainer.setBackgroundColor(backdrop.edge_color | 0xFF000000);
        TLRPC.Document document = gift.getDocument();
        Drawable thumb = null;
        if (document != null) {
            thumb = DocumentObject.getSvgThumb(document, Theme.key_emptyListPlaceholder, 0.2f);
        }
        giftImageView.setImage(ImageLocation.getForDocument(document), "50_50", thumb, null);
    }

    public void setUser(TLRPC.User user) {
        avatarDrawable.setScaleSize(1f);
        avatarDrawable.setInfo(currentAccount, user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);

        String name = user != null ? user.first_name : "Unknown";
        messageView.setText(
            AndroidUtilities.replaceTags("This gift is already your them in the chat with **" + name + "**. Remove it there and use it here instead?")
        );
    }

}
