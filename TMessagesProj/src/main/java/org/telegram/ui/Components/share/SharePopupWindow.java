package org.telegram.ui.Components.share;

import android.content.Context;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;

public class SharePopupWindow extends PopupWindow {

    private final ShareView shareView;

    public SharePopupWindow(@NonNull Context context) {
        super(null, ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );

        shareView = new ShareView(context);
        shareView.setLayoutParams(
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        );
        shareView.setOnHideListener(super::dismiss);
        setContentView(shareView);
        setAnimationStyle(android.R.style.Animation);
        setFocusable(true);
    }


    public void setController(ShareView.Controller controller) {
        shareView.setController(controller);
    }

    public void setColor(int color) {
        shareView.setColor(color);
    }

    public void show(View parent, float x, float y, int size, Shader shader) {
        showAsDropDown(parent,0, 0);
        shareView.setAnchor(x, y, size, shader);
        shareView.show();
    }

    public void dismiss(float x, float y) {
        shareView.hide(x, y);
    }

    @Override
    public void dismiss() {
        shareView.hide();
    }

}
