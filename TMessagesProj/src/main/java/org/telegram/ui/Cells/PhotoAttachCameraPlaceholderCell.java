/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

@SuppressLint("NewApi")
public class PhotoAttachCameraPlaceholderCell extends View {

    private int itemSize;

    public PhotoAttachCameraPlaceholderCell(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5), MeasureSpec.EXACTLY)
        );
    }

    public void setItemSize(int size) {
        itemSize = size;
    }

}
