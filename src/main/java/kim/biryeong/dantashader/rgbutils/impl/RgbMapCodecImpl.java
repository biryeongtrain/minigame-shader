package kim.biryeong.dantashader.rgbutils.impl;

import kim.biryeong.dantashader.rgbutils.api.RgbMapCodec;

public final class RgbMapCodecImpl implements RgbMapCodec {
    public static final RgbMapCodec INSTANCE = new RgbMapCodecImpl();

    public RgbMapCodecImpl() {
    }

    @Override
    public int[] encodeRgbToMapIndexes(int[] rgb64x64) {
        RgbMapValidation.requireRgbArray(rgb64x64, "rgb64x64");

        int[] mapIndexes = new int[MAP_INDEX_COUNT];
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                int rgb = rgb64x64[y * RGB_WIDTH + x] & 0x00FFFFFF;

                int b1 = rgb & 0xFF;
                int b2 = (rgb >> 8) & 0xFF;
                int b3 = (rgb >> 16) & 0xFF;

                int i00 = b1 & 0x7F;
                int i10 = b2 & 0x7F;
                int i01 = b3 & 0x7F;
                int i11 = (((b3 >> 7) & 1) << 2)
                        | (((b2 >> 7) & 1) << 1)
                        | ((b1 >> 7) & 1);

                int mapX = x * 2;
                int mapY = y * 2;

                mapIndexes[mapY * MAP_WIDTH + mapX] = i00;
                mapIndexes[mapY * MAP_WIDTH + (mapX + 1)] = i10;
                mapIndexes[(mapY + 1) * MAP_WIDTH + mapX] = i01;
                mapIndexes[(mapY + 1) * MAP_WIDTH + (mapX + 1)] = i11;
            }
        }

        return mapIndexes;
    }

    @Override
    public int[] decodeMapIndexesToRgb(int[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        int[] rgb64x64 = new int[RGB_PIXEL_COUNT];
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                int mapX = x * 2;
                int mapY = y * 2;

                int i00 = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[mapY * MAP_WIDTH + mapX], mapX, mapY, "mapIndexes128x128");
                int i10 = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[mapY * MAP_WIDTH + (mapX + 1)], mapX + 1, mapY, "mapIndexes128x128");
                int i01 = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[(mapY + 1) * MAP_WIDTH + mapX], mapX, mapY + 1, "mapIndexes128x128");
                int i11 = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[(mapY + 1) * MAP_WIDTH + (mapX + 1)], mapX + 1, mapY + 1, "mapIndexes128x128");

                int b1 = i00 | ((i11 & 1) << 7);
                int b2 = i10 | ((i11 & 2) << 6);
                int b3 = i01 | ((i11 & 4) << 5);

                rgb64x64[y * RGB_WIDTH + x] = (b3 << 16) | (b2 << 8) | b1;
            }
        }

        return rgb64x64;
    }
}
