package kim.biryeong.dantashader.rgbutils.impl;

import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import kim.biryeong.dantashader.rgbutils.api.RgbMapCodec;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

public final class RgbMapValidation {
    private RgbMapValidation() {
    }

    public static int[] requireRgbArray(int @NotNull [] rgb64x64, String argumentName) {
        if (rgb64x64.length != RgbMapCodec.RGB_PIXEL_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 4096 values (64x64), but was " + rgb64x64.length);
        }
        return rgb64x64;
    }

    public static int[] requireMapIndexArray(int @NotNull [] mapIndexes128x128, String argumentName) {
        if (mapIndexes128x128.length != RgbMapCodec.MAP_INDEX_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 16384 values (128x128), but was " + mapIndexes128x128.length);
        }
        return mapIndexes128x128;
    }

    public static byte[] requireMapIndexByteArray(byte @NotNull [] mapIndexes128x128, String argumentName) {
        if (mapIndexes128x128.length != RgbMapCodec.MAP_INDEX_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 16384 values (128x128), but was " + mapIndexes128x128.length);
        }
        return mapIndexes128x128;
    }

    public static BufferedImage requireImageSize(@NotNull BufferedImage image, int expectedWidth, int expectedHeight, String argumentName) {
        if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(argumentName + " must be " + expectedWidth + "x" + expectedHeight + ", but was " + image.getWidth() + "x" + image.getHeight());
        }
        return image;
    }

    public static DrawableCanvas requireCanvasSize(@NotNull DrawableCanvas canvas, int expectedWidth, int expectedHeight, String argumentName) {
        if (canvas.getWidth() != expectedWidth || canvas.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(argumentName + " must be " + expectedWidth + "x" + expectedHeight + ", but was " + canvas.getWidth() + "x" + canvas.getHeight());
        }
        return canvas;
    }

    public static int requireMapIndexRange(int value, int x, int y, String sourceName) {
        if (value < 0 || value > 127) {
            throw new IllegalArgumentException(sourceName + " contains out-of-range value " + value + " at (x=" + x + ", y=" + y + "); expected 0..127");
        }
        return value;
    }
}
