package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.EmojiThemes;

import java.util.ArrayList;
import java.util.List;

public class GiftThemeController {

    private static final String PREFS_THEMES = "gift_themes_";
    private static final String PREFS_THEMES_EXT = "gift_themes_ext_";

    private List<EmojiThemes> cached = new ArrayList<>();
    private List<EmojiThemes> cachedExternal = new ArrayList<>();

    private volatile long hash;


    public void preload(int account) {
        try {
            hash = getSharedPreferences(account, PREFS_THEMES).getLong("hash", 0);
        } catch (Exception e) {
            FileLog.e(e);
        }

        cached = loadCachedThemes(account, PREFS_THEMES, "count", "theme_");
        cachedExternal = loadCachedThemes(account, PREFS_THEMES_EXT, "count_ext", "theme_ext_");

        for (EmojiThemes theme : cached) {
            theme.preloadGift();
        }

        for (EmojiThemes theme : cachedExternal) {
            theme.preloadGift();
        }
    }

    public void getGiftThemes(int account, ResultCallback<List<EmojiThemes>> callback) {
        if (hash == 0) {
            preload(account);
        }

        TL_account.Tl_getUniqueGiftChatThemes request = new TL_account.Tl_getUniqueGiftChatThemes();
        request.limit = 10;
        request.offset = 0;
        ConnectionsManager.getInstance(UserConfig.selectedAccount)
            .sendRequest(
                request,
                (resp, err) -> ChatThemeController.chatThemeQueue.postRunnable(() -> {
                    final List<EmojiThemes> giftThemes;
                    boolean isError = false;
                    if (resp instanceof TL_account.Tl_chatThemes) {
                        hash = ((TL_account.Tl_chatThemes) resp).hash;
                        giftThemes = new ArrayList<>();
                        List<TLRPC.ChatTheme> themes = ((TL_account.Tl_chatThemes) resp).themes;
                        for (int i = 0; i < themes.size(); i++) {
                            TLRPC.TL_chatThemeUniqueGift giftTheme = (TLRPC.TL_chatThemeUniqueGift) themes.get(i);
                            EmojiThemes emojiTheme = EmojiThemes.createFromGiftTheme(account, giftTheme);
                            emojiTheme.preloadWallpaper();
                            giftThemes.add(emojiTheme);
                        }
                        cacheThemes(account, hash, themes);
                    } else if (resp instanceof TL_account.TL_chatThemesNotModified) {
                        giftThemes = loadCachedThemes(account, PREFS_THEMES, "count", "theme_");
                    } else {
                        isError = true;
                        giftThemes = null;
                        AndroidUtilities.runOnUIThread(() -> callback.onError(err));
                    }
                    if (!isError) {
                        for (EmojiThemes theme : giftThemes) {
                            theme.initColors();
                        }

                        cached = new ArrayList<>(giftThemes);
                        AndroidUtilities.runOnUIThread(() -> callback.onComplete(giftThemes));
                    }
                })
            );

        if (cached != null && !cached.isEmpty()) {
            callback.onComplete(cached);
        }
    }

    public EmojiThemes getTheme(String slug) {
        if (slug != null) {
            for (EmojiThemes theme : cached) {
                if (slug.equals(theme.getSymbol())) {
                    return theme;
                }
            }
            for (EmojiThemes theme : cachedExternal) {
                if (slug.equals(theme.getSymbol())) {
                    return theme;
                }
            }
        }
        return null;
    }

    public EmojiThemes getOrCreateExternalTheme(int account, TLRPC.TL_chatThemeUniqueGift giftTheme) {
        for (EmojiThemes theme : cachedExternal) {
            if (giftTheme.gift.slug.equals(theme.getSymbol())) {
                return theme;
            }
        }

        SharedPreferences prefs = getSharedPreferences(account, PREFS_THEMES_EXT);
        int count = prefs.getInt("count_ext", 0);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putInt("count_ext", ++count);
        prefsEditor.putString("theme_ext_" + (count - 1), convertToHex(giftTheme));
        prefsEditor.apply();

        EmojiThemes theme = EmojiThemes.createFromGiftTheme(UserConfig.selectedAccount, giftTheme);
        theme.initColors();
        theme.preloadWallpaper();
        cachedExternal.add(theme);
        return theme;
    }

    private void cacheThemes(int account, long hash, List<TLRPC.ChatTheme> themes) {
        SharedPreferences prefs = getSharedPreferences(account, PREFS_THEMES);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.clear();
        prefsEditor.putLong("hash", hash);
        prefsEditor.putInt("count", themes.size());
        for (int i = 0; i < themes.size(); i++) {
            TLRPC.TL_chatThemeUniqueGift giftTheme = (TLRPC.TL_chatThemeUniqueGift) themes.get(i);
            prefsEditor.putString("theme_" + i, convertToHex(giftTheme));
        }
        prefsEditor.apply();
    }

    private String convertToHex(TLRPC.TL_chatThemeUniqueGift theme) {
        SerializedData serialized = new SerializedData(theme.getObjectSize());
        theme.serializeToStream(serialized);
        return Utilities.bytesToHex(serialized.toByteArray());
    }

    private List<EmojiThemes> loadCachedThemes(int account, String prefsName, String countKey, String themeKeyPrefix) {
        List<EmojiThemes> cached = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences(account, prefsName);
        int count = prefs.getInt(countKey, 0);
        if (count == 0) {
            return cached;
        }

        for (int i = 0; i < count; i++) {
            String themeStr = prefs.getString(themeKeyPrefix + i, "");
            SerializedData serialized = new SerializedData(Utilities.hexToBytes(themeStr));
            try {
                TLRPC.TL_chatThemeUniqueGift giftTheme =
                    (TLRPC.TL_chatThemeUniqueGift) TLRPC.TL_chatThemeUniqueGift
                        .TLdeserialize(serialized, serialized.readInt32(true), true);
                if (giftTheme != null) {
                    EmojiThemes emojiTheme = EmojiThemes.createFromGiftTheme(account, giftTheme);
                    cached.add(emojiTheme);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return cached;
    }

    private SharedPreferences getSharedPreferences(int account, String name) {
        return ApplicationLoader.applicationContext.getSharedPreferences(name + account, Context.MODE_PRIVATE);
    }

}
