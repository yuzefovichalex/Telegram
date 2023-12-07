package org.telegram.ui.Components.DeleteEffect;

import android.graphics.Color;

import androidx.annotation.NonNull;

public class ColorUtils {

    public static void blendColors(
        @NonNull int[] chunkColors,
        @NonNull float[] resultColor
    ) {
        int resultRed = 0;
        int resultGreen = 0;
        int resultBlue = 0;
        int resultAlpha = 0;

        for (int color : chunkColors) {
            resultRed += Color.red(color);
            resultGreen += Color.green(color);
            resultBlue += Color.blue(color);
            resultAlpha += Color.alpha(color);
        }

        resultColor[0] = resultRed / 255f / chunkColors.length;
        resultColor[1] = resultGreen / 255f / chunkColors.length;
        resultColor[2] = resultBlue / 255f / chunkColors.length;
        resultColor[3] = resultAlpha / 255f / chunkColors.length;
    }

}