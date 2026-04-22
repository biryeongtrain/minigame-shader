package kim.biryeong.dantashader.shaderfx.api;

import kim.biryeong.dantashader.shaderfx.impl.RPHandler;
import net.minecraft.util.ARGB;

import java.awt.image.BufferedImage;

public class ShaderUtil {
    public static void enableAssets() {
        RPHandler.enableAssets();
    }

    public static void enableAnimojiConversion() {
        RPHandler.enableAnimojiConversion();
    }

    public static BufferedImage toARGB(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    public static BufferedImage tintEdges(BufferedImage bufferedImage, int color) {
        bufferedImage = toARGB(bufferedImage);

        int w = bufferedImage.getWidth() - 1;
        int h = bufferedImage.getHeight() - 1;

        bufferedImage.setRGB(0, 0, color);
        bufferedImage.setRGB(w, 0, color);
        bufferedImage.setRGB(0, h, color);
        bufferedImage.setRGB(w, h, color);

        return bufferedImage;
    }

    public static BufferedImage addAnimatedEmojiMarker(BufferedImage bufferedImage, int fps) {
        bufferedImage = toARGB(bufferedImage);

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        int frames = Math.max(1, height / Math.max(1, width));
        int frameheight = Math.max(1, height / frames);

        BufferedImage res = new BufferedImage(width, height + frameheight + 1, BufferedImage.TYPE_INT_ARGB);

        int[] srcPixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
        res.setRGB(0, frameheight, width, height, srcPixels, 0, width);

        int metaColor = ARGB.color(252, frames + 1, fps, frameheight);
        return tintEdges(res, metaColor);
    }
}
