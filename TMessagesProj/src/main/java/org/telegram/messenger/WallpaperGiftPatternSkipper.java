package org.telegram.messenger;

import android.graphics.Canvas;

import org.xml.sax.Attributes;

public class WallpaperGiftPatternSkipper implements SvgHelper.ElementInterceptor {

    private boolean isGiftPatternsBlock;

    @Override
    public boolean onElementStart(Canvas canvas, String localName, Attributes attrs) {
        switch (localName) {
            case "g":
                if ("GiftPatterns".equals(SvgHelper.getStringAttr("id", attrs))) {
                    isGiftPatternsBlock = true;
                    onGiftPatternBlockDetected(attrs);
                }
                break;
            case "rect":
                if (isGiftPatternsBlock) {
                    onGiftPatternDetected(attrs);
                    return true;
                }
                break;
        }
        return false;
    }

    protected void onGiftPatternBlockDetected(Attributes attrs) { }

    protected void onGiftPatternDetected(Attributes attrs) { }

    @Override
    public boolean onElementEnd(String localName) {
        if (localName.equals("g")) {
            isGiftPatternsBlock = false;
        }
        return false;
    }

}
