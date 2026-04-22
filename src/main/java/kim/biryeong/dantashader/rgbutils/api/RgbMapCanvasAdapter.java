package kim.biryeong.dantashader.rgbutils.api;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import kim.biryeong.dantashader.rgbutils.impl.RgbMapValidation;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Adapter utilities for converting between RGB-map index data, {@link DrawableCanvas},
 * and {@link BufferedImage}.
 * <p>
 * This class handles single-map conversions (64x64 RGB <-> 128x128 map index canvas).
 * Multi-map top-level workflows are available in {@link RgbMapCombinedCanvasAdapter}.
 */
public final class RgbMapCanvasAdapter {
    private static final int MAP_COLOR_OFFSET = 4;

    private RgbMapCanvasAdapter() {
    }

    /**
     * Encodes a {@code 64x64} image into a map-index {@link CanvasImage} using the default codec.
     *
     * @param image64x64 source image, must be {@code 64x64}
     * @return encoded canvas image with size {@code 128x128}
     */
    public static CanvasImage encodeImageToRgbMapCanvas(BufferedImage image64x64) {
        return encodeImageToRgbMapCanvas(image64x64, RgbMapCodec.createDefault());
    }

    /**
     * Encodes a {@code 64x64} image into a map-index {@link CanvasImage}.
     *
     * @param image64x64 source image, must be {@code 64x64}
     * @param codec codec implementation to use
     * @return encoded canvas image with size {@code 128x128}
     */
    public static CanvasImage encodeImageToRgbMapCanvas(BufferedImage image64x64, RgbMapCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        RgbMapValidation.requireImageSize(image64x64, RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, "image64x64");
        int[] mapIndexes = codec.encodeImageToMapIndexes(image64x64);
        return mapIndexesToDrawableCanvas(mapIndexes);
    }

    /**
     * Encodes a {@code 64x64} canvas into a map-index {@link CanvasImage} using the default codec.
     *
     * @param canvas64x64 source canvas, must be {@code 64x64}
     * @return encoded canvas image with size {@code 128x128}
     */
    public static CanvasImage encodeCanvasToRgbMapCanvas(DrawableCanvas canvas64x64) {
        return encodeCanvasToRgbMapCanvas(canvas64x64, RgbMapCodec.createDefault());
    }

    /**
     * Encodes a {@code 64x64} canvas into a map-index {@link CanvasImage}.
     *
     * @param canvas64x64 source canvas, must be {@code 64x64}
     * @param codec codec implementation to use
     * @return encoded canvas image with size {@code 128x128}
     */
    public static CanvasImage encodeCanvasToRgbMapCanvas(DrawableCanvas canvas64x64, RgbMapCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        RgbMapValidation.requireCanvasSize(canvas64x64, RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, "canvas64x64");
        return encodeImageToRgbMapCanvas(drawableCanvasToBufferedImage(canvas64x64), codec);
    }

    /**
     * Converts map indexes ({@code 0..127}) to a drawable {@code 128x128} canvas.
     * <p>
     * Output uses Minecraft raw map color values where index {@code 0..127} is stored as {@code index + 4}.
     *
     * @param mapIndexes128x128 map index array, length must be {@link RgbMapCodec#MAP_INDEX_COUNT}
     * @return canvas image with size {@code 128x128}
     */
    public static CanvasImage mapIndexesToDrawableCanvas(int[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        CanvasImage canvas = new CanvasImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i], x, y, "mapIndexes128x128");
                canvas.setRaw(x, y, (byte) (index + MAP_COLOR_OFFSET));
                i++;
            }
        }
        return canvas;
    }

    /**
     * Converts a drawable {@code 128x128} canvas into map indexes ({@code 0..127}).
     * <p>
     * Input is expected to use Minecraft raw map color values where effective map indexes are {@code raw - 4}.
     *
     * @param canvas128x128 source canvas, must be {@code 128x128}
     * @return map index array, length {@link RgbMapCodec#MAP_INDEX_COUNT}
     */
    public static int[] drawableCanvasToMapIndexes(DrawableCanvas canvas128x128) {
        RgbMapValidation.requireCanvasSize(canvas128x128, RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, "canvas128x128");

        int[] out = new int[RgbMapCodec.MAP_INDEX_COUNT];
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int rawValue = Byte.toUnsignedInt(canvas128x128.getRaw(x, y));
                int mapIndex = rawValue - MAP_COLOR_OFFSET;
                out[i++] = RgbMapValidation.requireMapIndexRange(mapIndex, x, y, "canvas128x128");
            }
        }

        return out;
    }

    /**
     * Converts a map index array to a palette-colored debug image ({@code 128x128}).
     *
     * @param mapIndexes128x128 map index array, length must be {@link RgbMapCodec#MAP_INDEX_COUNT}
     * @param palette palette used to resolve each index to RGB
     * @return palette-colored image in {@link BufferedImage#TYPE_INT_RGB} format
     */
    public static BufferedImage mapIndexesToPaletteImage(int[] mapIndexes128x128, RgbMapPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        BufferedImage image = new BufferedImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i++], x, y, "mapIndexes128x128");
                image.setRGB(x, y, palette.rgbAt(index));
            }
        }
        return image;
    }

    /**
     * Converts a palette image ({@code 128x128}) back to map indexes using exact RGB matching.
     *
     * @param image128x128 source image, must be {@code 128x128}
     * @param palette palette used for exact lookup
     * @return map index array, length {@link RgbMapCodec#MAP_INDEX_COUNT}
     */
    public static int[] paletteImageToMapIndexes(BufferedImage image128x128, RgbMapPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        RgbMapValidation.requireImageSize(image128x128, RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, "image128x128");

        int[] mapIndexes = new int[RgbMapCodec.MAP_INDEX_COUNT];
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int rgb = image128x128.getRGB(x, y) & 0x00FFFFFF;
                int index = palette.findIndexExact(rgb);
                if (index < 0) {
                    throw new IllegalArgumentException(
                            "image128x128 contains color " + toHex(rgb) + " at (x=" + x + ", y=" + y + ") that is not in the 128-color rgb_maps lookup"
                    );
                }
                mapIndexes[i++] = index;
            }
        }

        return mapIndexes;
    }

    /**
     * Wraps a {@link BufferedImage} as a {@link CanvasImage}.
     *
     * @param image source image
     * @return canvas image representation of the source
     */
    public static CanvasImage bufferedImageToDrawableCanvas(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        return CanvasImage.from(image);
    }

    /**
     * Renders a {@link DrawableCanvas} into a {@link BufferedImage}.
     *
     * @param canvas source canvas
     * @return rendered image in {@link BufferedImage#TYPE_INT_ARGB} compatible data produced by MapCanvas
     */
    public static BufferedImage drawableCanvasToBufferedImage(DrawableCanvas canvas) {
        if (canvas == null) {
            throw new IllegalArgumentException("canvas must not be null");
        }
        return CanvasUtils.toImage(canvas);
    }

    private static String toHex(int rgb) {
        return String.format(Locale.ROOT, "0x%06X", rgb & 0x00FFFFFF);
    }
}
