package kim.biryeong.dantashader.rgbutils.api;

import kim.biryeong.dantashader.rgbutils.impl.RgbMapValidation;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Palette lookup for the 128 colors used by the RGB map encoding.
 */
public final class RgbMapPalette {
    /** Fixed palette size for RGB map indexes. */
    public static final int LOOKUP_SIZE = 128;

    private static final int[] DEFAULT_LOOKUP = {
            0x597D27,
            0x6D9930,
            0x7FB238,
            0x435E1D,
            0xAEA473,
            0xD5C98C,
            0xF7E9A3,
            0x827B56,
            0x8C8C8C,
            0xABABAB,
            0xC7C7C7,
            0x696969,
            0xB40000,
            0xDC0000,
            0xFF0000,
            0x870000,
            0x7070B4,
            0x8A8ADC,
            0xA0A0FF,
            0x545487,
            0x757575,
            0x909090,
            0xA7A7A7,
            0x585858,
            0x005700,
            0x006A00,
            0x007C00,
            0x004100,
            0xB4B4B4,
            0xDCDCDC,
            0xFFFFFF,
            0x878787,
            0x737681,
            0x8D909E,
            0xA4A8B8,
            0x565861,
            0x6A4C36,
            0x825E42,
            0x976D4D,
            0x4F3928,
            0x4F4F4F,
            0x606060,
            0x707070,
            0x3B3B3B,
            0x2D2DB4,
            0x3737DC,
            0x4040FF,
            0x212187,
            0x645432,
            0x7B663E,
            0x8F7748,
            0x4B3F26,
            0xB4B1AC,
            0xDCD9D3,
            0xFFFCF5,
            0x878581,
            0x985924,
            0xBA6D2C,
            0xD87F33,
            0x72431B,
            0x7D3598,
            0x9941BA,
            0xB24CD8,
            0x5E2872,
            0x486C98,
            0x5884BA,
            0x6699D8,
            0x365172,
            0xA1A124,
            0xC5C52C,
            0xE5E533,
            0x79791B,
            0x599011,
            0x6DB015,
            0x7FCC19,
            0x436C0D,
            0xAA5974,
            0xD06D8E,
            0xF27FA5,
            0x804357,
            0x353535,
            0x414141,
            0x4C4C4C,
            0x282828,
            0x6C6C6C,
            0x848484,
            0x999999,
            0x515151,
            0x35596C,
            0x416D84,
            0x4C7F99,
            0x284351,
            0x592C7D,
            0x6D3699,
            0x7F3FB2,
            0x43215E,
            0x24357D,
            0x2C4199,
            0x334CB2,
            0x1B285E,
            0x483524,
            0x58412C,
            0x664C33,
            0x36281B,
            0x485924,
            0x586D2C,
            0x667F33,
            0x36431B,
            0x6C2424,
            0x842C2C,
            0x993333,
            0x511B1B,
            0x111111,
            0x151515,
            0x191919,
            0x0D0D0D,
            0xB0A836,
            0xD7CD42,
            0xFAEE4D,
            0x847E28,
            0x409A96,
            0x4FBCB7,
            0x5CDBD5,
            0x307370,
            0x345AB4,
            0x3F6EDC,
            0x4A80FF,
            0x274387,
    };

    private static final RgbMapPalette RGB_MAPS = new RgbMapPalette(DEFAULT_LOOKUP);

    private final int[] rgbByIndex;
    private final Map<Integer, Integer> indexByRgb;

    /**
     * Creates a palette from a 128-entry RGB lookup table.
     *
     * @param rgbLookup128 lookup values in {@code 0xRRGGBB} format, length must be {@link #LOOKUP_SIZE}
     */
    public RgbMapPalette(int[] rgbLookup128) {
        if (rgbLookup128 == null) {
            throw new IllegalArgumentException("rgbLookup128 must not be null");
        }
        if (rgbLookup128.length != LOOKUP_SIZE) {
            throw new IllegalArgumentException("rgbLookup128 must contain exactly 128 colors, but was " + rgbLookup128.length);
        }

        this.rgbByIndex = Arrays.copyOf(rgbLookup128, LOOKUP_SIZE);
        this.indexByRgb = new HashMap<>(LOOKUP_SIZE * 2);

        for (int i = 0; i < LOOKUP_SIZE; i++) {
            int rgb = this.rgbByIndex[i] & 0x00FFFFFF;
            this.rgbByIndex[i] = rgb;
            this.indexByRgb.put(rgb, i);
        }
    }

    /**
     * Returns the built-in {@code rgb_maps} palette.
     *
     * @return singleton palette instance
     */
    public static RgbMapPalette rgbMaps() {
        return RGB_MAPS;
    }

    /**
     * @return number of colors in this palette (always {@value #LOOKUP_SIZE})
     */
    public int size() {
        return LOOKUP_SIZE;
    }

    /**
     * Returns the RGB value for a palette index.
     *
     * @param index0to127 palette index in {@code 0..127}
     * @return color in {@code 0xRRGGBB} format
     */
    public int rgbAt(int index0to127) {
        if (index0to127 < 0 || index0to127 >= LOOKUP_SIZE) {
            throw new IllegalArgumentException("index0to127 must be in range 0..127, but was " + index0to127);
        }
        return this.rgbByIndex[index0to127];
    }

    /**
     * Returns the ARGB value for a palette index with alpha forced to {@code 0xFF}.
     *
     * @param index0to127 palette index in {@code 0..127}
     * @return color in {@code 0xAARRGGBB} format
     */
    public int argbAt(int index0to127) {
        return 0xFF000000 | rgbAt(index0to127);
    }

    /**
     * Finds the exact palette index for an RGB value.
     *
     * @param rgb color in {@code 0xRRGGBB} format
     * @return index in {@code 0..127}, or {@code -1} if not present
     */
    public int findIndexExact(int rgb) {
        Integer index = this.indexByRgb.get(rgb & 0x00FFFFFF);
        return index == null ? -1 : index;
    }

    /**
     * Renders map indexes to a debug image using this palette.
     *
     * @param mapIndexes128x128 map index array, length must be {@link RgbMapCodec#MAP_INDEX_COUNT}
     * @return debug image with size {@code 128x128}
     */
    public BufferedImage toDebugImage(int[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        BufferedImage image = new BufferedImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i], x, y, "mapIndexes128x128");
                image.setRGB(x, y, rgbAt(index));
                i++;
            }
        }

        return image;
    }
}
