package kim.biryeong.dantashader.rgbutils.api;

import kim.biryeong.dantashader.rgbutils.impl.RgbMapCodecImpl;
import kim.biryeong.dantashader.rgbutils.impl.RgbMapValidation;

import java.awt.image.BufferedImage;

/**
 * Codec API that converts between:
 * <ul>
 *   <li>a 64x64 RGB image (or RGB array), and</li>
 *   <li>a 128x128 map index grid where each entry is in range {@code 0..127}.</li>
 * </ul>
 */
public interface RgbMapCodec {
    /** Width of the source RGB image in pixels. */
    int RGB_WIDTH = 64;
    /** Height of the source RGB image in pixels. */
    int RGB_HEIGHT = 64;
    /** Total RGB pixel count for a single source image ({@code 64 * 64}). */
    int RGB_PIXEL_COUNT = RGB_WIDTH * RGB_HEIGHT;

    /** Width of the encoded map index grid in pixels. */
    int MAP_WIDTH = 128;
    /** Height of the encoded map index grid in pixels. */
    int MAP_HEIGHT = 128;
    /** Total encoded map index count ({@code 128 * 128}). */
    int MAP_INDEX_COUNT = MAP_WIDTH * MAP_HEIGHT;

    /**
     * Creates the default codec implementation.
     *
     * @return default {@link RgbMapCodec} implementation
     */
    static RgbMapCodec createDefault() {
        return RgbMapCodecImpl.INSTANCE;
    }

    /**
     * Encodes a 64x64 RGB array into a 128x128 map index array.
     *
     * @param rgb64x64 RGB values in {@code 0xRRGGBB} format, length must be {@link #RGB_PIXEL_COUNT}
     * @return encoded map indexes, length {@link #MAP_INDEX_COUNT}, values in {@code 0..127}
     */
    int[] encodeRgbToMapIndexes(int[] rgb64x64);

    /**
     * Decodes a 128x128 map index array back into a 64x64 RGB array.
     *
     * @param mapIndexes128x128 map indexes, length must be {@link #MAP_INDEX_COUNT}, values in {@code 0..127}
     * @return decoded RGB values in {@code 0xRRGGBB} format, length {@link #RGB_PIXEL_COUNT}
     */
    int[] decodeMapIndexesToRgb(int[] mapIndexes128x128);

    /**
     * Encodes RGB pixels to map indexes and writes each index into a byte.
     *
     * @param rgb64x64 RGB values in {@code 0xRRGGBB} format, length must be {@link #RGB_PIXEL_COUNT}
     * @return encoded map index bytes, length {@link #MAP_INDEX_COUNT}
     */
    default byte[] encodeRgbToMapIndexBytes(int[] rgb64x64) {
        int[] mapIndexes = encodeRgbToMapIndexes(rgb64x64);
        byte[] out = new byte[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            out[i] = (byte) mapIndexes[i];
        }
        return out;
    }

    /**
     * Decodes map index bytes into RGB pixels.
     *
     * @param mapIndexes128x128 unsigned map indexes stored in bytes, length must be {@link #MAP_INDEX_COUNT}
     * @return decoded RGB values in {@code 0xRRGGBB} format, length {@link #RGB_PIXEL_COUNT}
     */
    default int[] decodeMapIndexBytesToRgb(byte[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexByteArray(mapIndexes128x128, "mapIndexes128x128");
        int[] mapIndexes = new int[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            mapIndexes[i] = Byte.toUnsignedInt(mapIndexes128x128[i]);
        }
        return decodeMapIndexesToRgb(mapIndexes);
    }

    /**
     * Encodes a 64x64 image into a 128x128 map index array.
     *
     * @param image64x64 source image, must be {@code 64x64}
     * @return encoded map indexes, length {@link #MAP_INDEX_COUNT}, values in {@code 0..127}
     */
    default int[] encodeImageToMapIndexes(BufferedImage image64x64) {
        RgbMapValidation.requireImageSize(image64x64, RGB_WIDTH, RGB_HEIGHT, "image64x64");
        int[] rgb64x64 = new int[RGB_PIXEL_COUNT];

        int i = 0;
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                rgb64x64[i++] = image64x64.getRGB(x, y) & 0x00FFFFFF;
            }
        }

        return encodeRgbToMapIndexes(rgb64x64);
    }

    /**
     * Encodes a 64x64 image and returns map indexes as bytes.
     *
     * @param image64x64 source image, must be {@code 64x64}
     * @return encoded map index bytes, length {@link #MAP_INDEX_COUNT}
     */
    default byte[] encodeImageToMapIndexBytes(BufferedImage image64x64) {
        int[] mapIndexes = encodeImageToMapIndexes(image64x64);
        byte[] out = new byte[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            out[i] = (byte) mapIndexes[i];
        }
        return out;
    }

    /**
     * Decodes map indexes into a 64x64 RGB image.
     *
     * @param mapIndexes128x128 map indexes, length must be {@link #MAP_INDEX_COUNT}, values in {@code 0..127}
     * @return decoded {@code 64x64} image in {@link BufferedImage#TYPE_INT_RGB} format
     */
    default BufferedImage decodeMapIndexesToImage(int[] mapIndexes128x128) {
        int[] rgb64x64 = decodeMapIndexesToRgb(mapIndexes128x128);
        BufferedImage image = new BufferedImage(RGB_WIDTH, RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);

        int i = 0;
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                image.setRGB(x, y, rgb64x64[i++]);
            }
        }

        return image;
    }
}
