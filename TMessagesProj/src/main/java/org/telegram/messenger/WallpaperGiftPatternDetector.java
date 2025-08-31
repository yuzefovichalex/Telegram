package org.telegram.messenger;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;

public class WallpaperGiftPatternDetector extends WallpaperGiftPatternSkipper {

    private final List<WallpaperGiftPattern> foundPatterns = new ArrayList<>();

    public List<WallpaperGiftPattern> getFoundPatterns() {
        return foundPatterns;
    }

    @Override
    protected void onGiftPatternBlockDetected(Attributes attrs) {
        foundPatterns.clear();
    }

    @Override
    public void onGiftPatternDetected(Attributes attrs) {
        WallpaperGiftPattern pattern = new WallpaperGiftPattern();
        pattern.x = getFloatValue("x", attrs);
        pattern.y = getFloatValue("y", attrs);
        pattern.width = getFloatValue("width", attrs);
        pattern.height = getFloatValue("height", attrs);
        String transformAttr = SvgHelper.getStringAttr("transform", attrs);
        if (transformAttr != null) {
            pattern.transform = SvgHelper.parseTransform(transformAttr);
        }
        foundPatterns.add(pattern);
    }

    private float getFloatValue(String name, Attributes attrs) {
        Float value = SvgHelper.getFloatAttr(name, attrs, 0f);
        return value != null ? value : 0f;
    }

}
