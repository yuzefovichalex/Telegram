package org.telegram.ui.Components.share;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public abstract class DialogShareViewController implements ShareView.Controller {

    private final ArrayList<TLRPC.Dialog> recentDialogs = new ArrayList<TLRPC.Dialog>();
    private final ArrayList<AvatarDrawable> avatars = new ArrayList<>();


    public DialogShareViewController(int currentAccount, int dialogCount) {
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
            AvatarDrawable d;
            if (i < recentDialogs.size()) {
                TLRPC.Dialog dialog = recentDialogs.get(i);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog.id);
                d = new AvatarDrawable();
                d.setInfo(currentAccount, user);
                if (UserObject.isUserSelf(user)) {
                    d.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                }
            } else {
                d = new AvatarDrawable();
            }
            avatars.add(d);
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

}
