package org.telegram.ui.Components.share;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public abstract class DialogShareViewController implements ShareView.Controller {

    private final int currentAccount;

    private final ArrayList<TLRPC.Dialog> recentDialogs = new ArrayList<TLRPC.Dialog>();
    private final ArrayList<ShareDrawable> avatars = new ArrayList<>();

    public DialogShareViewController(int currentAccount, int dialogCount) {
        this.currentAccount = currentAccount;

        int remain = dialogCount;
        if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
            recentDialogs.add(dialog);
            remain--;
        }


        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
        if (allDialogs.isEmpty()) {
            return;
        }

        long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
        int idx = 0;
        while (remain > 0 || idx < allDialogs.size()) {
            TLRPC.Dialog dialog = allDialogs.get(idx);
            if (!(dialog instanceof TLRPC.TL_dialog)) {
                idx++;
                continue;
            }
            if (dialog.id == selfUserId) {
                idx++;
                continue;
            }
            if (!DialogObject.isEncryptedDialog(dialog.id)) {
                if (DialogObject.isUserDialog(dialog.id)) {
                    if (dialog.folder_id != 1) {
                        recentDialogs.add(dialog);
                        remain--;
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                    if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                        if (dialog.folder_id != 1) {
                            recentDialogs.add(dialog);
                            remain--;
                        }
                    }
                }

            }
            idx++;
        }

        for (int i = 0; i < dialogCount; i++) {
            ShareDrawable d;
            if (i < recentDialogs.size()) {
                TLRPC.Dialog dialog = recentDialogs.get(i);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog.id);
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                d = new ShareDrawable();

                if (UserObject.isUserSelf(user)) {
                    d.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                } else if (user != null) {
                    d.setInfo(currentAccount, user);
                    d.setForUserOrChat((TLRPC.User) user);
                } else if (chat != null) {
                    d.setInfo(currentAccount, chat);
                    d.setForUserOrChat((TLRPC.Chat) chat);
                }
            } else {
                d = new ShareDrawable();
            }
            avatars.add(d);
        }
    }

    @Override
    public void onAttachedToWindow(@NonNull ShareView shareView) { }

    @NonNull
    @Override
    public String getItemLabel(int idx) {
        TLRPC.Dialog dialog = recentDialogs.get(idx);
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog.id);
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
        if (UserObject.isUserSelf(user)) {
            return LocaleController.getString(R.string.SavedMessages);
        } else if (user != null) {
            return user.first_name;
        } else if (chat != null) {
            return chat.title;
        } else {
            return "noname";
        }
    }

    @NonNull
    @Override
    public Drawable getItemDrawable(int idx) {
        return avatars.get(idx);
    }

    protected TLRPC.Dialog getDialog(int idx) {
        return recentDialogs.get(idx);
    }

    @Override
    public void onDetachFromWindow(@NonNull ShareView shareView) { }

}
