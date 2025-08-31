package org.telegram.messenger;

import android.graphics.Canvas;

import org.xml.sax.Attributes;

import java.util.ArrayList;

public class WallpaperGiftPatternDetector extends WallpaperGiftPatternSkipper {

    private final WallpaperGiftPatternInfo info = new WallpaperGiftPatternInfo();

    public WallpaperGiftPatternInfo getInfo() {
        return info;
    }

    @Override
    public boolean onElementStart(Canvas canvas, String localName, Attributes attrs) {
        if ("svg".equals(localName)) {
            Float w = SvgHelper.getFloatAttr("width", attrs, null);
            Float h = SvgHelper.getFloatAttr("height", attrs, null);
            if (w == null || h == null) {
                String viewBox = SvgHelper.getStringAttr("viewBox", attrs);
                if (viewBox != null) {
                    String[] args = viewBox.split(" ");
                    w = Float.parseFloat(args[2]);
                    h = Float.parseFloat(args[3]);
                }
            }
            if (w == null || h == null) {
                w = 0f;
                h = 0f;
            }
            info.originalWidth = (int) Math.ceil(w);
            info.originalHeight = (int) Math.ceil(h);
        }
        return super.onElementStart(canvas, localName, attrs);
    }

    @Override
    protected void onGiftPatternBlockDetected(Attributes attrs) {
        info.patterns = null;
    }

    @Override
    public void onGiftPatternDetected(Attributes attrs) {
        if (info.patterns == null) {
            info.patterns = new ArrayList<>();
        }

        WallpaperGiftPattern pattern = new WallpaperGiftPattern();
        pattern.x = getFloatValue("x", attrs);
        pattern.y = getFloatValue("y", attrs);
        pattern.width = getFloatValue("width", attrs);
        pattern.height = getFloatValue("height", attrs);
        String transformAttr = SvgHelper.getStringAttr("transform", attrs);
        if (transformAttr != null) {
            pattern.transform = SvgHelper.parseTransform(transformAttr);
        }
        info.patterns.add(pattern);
    }

    private float getFloatValue(String name, Attributes attrs) {
        Float value = SvgHelper.getFloatAttr(name, attrs, 0f);
        return value != null ? value : 0f;
    }

}
