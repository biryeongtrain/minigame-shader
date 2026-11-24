package kim.biryeong.minigamelib.cinematic.dialogue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a chest-up, three-quarter view of a Minecraft skin (64x64, classic or slim) into a transparent image.
 * Orthographic software rasteriser with a depth buffer: no client or GPU needed, so the portrait can be baked into
 * the resource pack on the server.
 */
public final class SkinBustRenderer {
    private SkinBustRenderer() {}

    /** Settings for {@link #render}. Yaw turns the character to its right (towards screen left) for positive values. */
    public record Options(float yawDegrees, float pitchDegrees, int pixelsPerTexel, int outline, int rimColor) {
        public static final Options DEFAULT = new Options(-28f, 12f, 8, 2, 0xFFE9A15A);
    }

    /** True when the skin uses 3-pixel arms (transparent column at x=54..55 of the right arm front). */
    public static boolean isSlim(BufferedImage skin) {
        if (skin.getHeight() < 64) return false;
        return ((skin.getRGB(54, 20) >>> 24) == 0) && ((skin.getRGB(55, 31) >>> 24) == 0);
    }

    public static BufferedImage render(BufferedImage skin, boolean slim) { return render(skin, slim, Options.DEFAULT); }

    public static BufferedImage render(BufferedImage skin, boolean slim, Options options) {
        if (skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32))
            throw new IllegalArgumentException("Skin must be 64x64 or 64x32");
        boolean legacy = skin.getHeight() == 32;
        int arm = slim ? 3 : 4;
        List<Box> boxes = new ArrayList<>();
        // Model space in skin pixels: +X = character's left, +Y up, +Z towards the character's front.
        boxes.add(new Box(-4, 12, -4, 8, 8, 8, 0, 0, 0));                 // head
        boxes.add(new Box(-4, 0, -2, 8, 12, 4, 16, 16, 0));               // body
        boxes.add(new Box(-4 - arm, 0, -2, arm, 12, 4, 40, 16, 0));       // right arm
        boxes.add(legacy ? new Box(4, 0, -2, arm, 12, 4, 40, 16, 0).mirrored() : new Box(4, 0, -2, arm, 12, 4, 32, 48, 0)); // left arm
        boxes.add(new Box(-4, 12, -4, 8, 8, 8, 32, 0, 0.5f));             // hat
        if (!legacy) {
            boxes.add(new Box(-4, 0, -2, 8, 12, 4, 16, 32, 0.25f));       // jacket
            boxes.add(new Box(-4 - arm, 0, -2, arm, 12, 4, 40, 32, 0.25f)); // right sleeve
            boxes.add(new Box(4, 0, -2, arm, 12, 4, 48, 48, 0.25f));      // left sleeve
        }

        double yaw = Math.toRadians(options.yawDegrees()), pitch = Math.toRadians(options.pitchDegrees());
        int s = options.pixelsPerTexel();
        // Visible window: chest (y >= 3) up to above the hat, wide enough for shoulders while turned.
        double minX = -10.5, maxX = 10.5, minY = 1.2, maxY = 23.5;
        int pad = options.outline() + 2;
        int width = (int) Math.ceil((maxX - minX) * s) + pad * 2, height = (int) Math.ceil((maxY - minY) * s) + pad * 2;
        int[] color = new int[width * height];
        double[] depth = new double[width * height];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        var view = new View(yaw, pitch, s, minX, maxY, pad);
        for (var box : boxes) for (var face : box.faces()) raster(face, skin, view, width, height, color, depth, options.rimColor());

        // Crop the bottom with a soft fade so the bust does not end in a hard line.
        int fade = Math.max(1, s * 2);
        for (int y = height - fade - pad; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x; if ((color[i] >>> 24) == 0) continue;
            double t = Math.clamp((double) (height - pad - y) / fade, 0, 1);
            color[i] = (safeAlpha((int) Math.round(((color[i] >>> 24)) * t)) << 24) | (color[i] & 0xFFFFFF);
        }
        var out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] outline = color.clone();
        if (options.outline() > 0) {
            int r = options.outline();
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if ((color[y * width + x] >>> 24) != 0) continue;
                int best = 0;
                for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height || dx * dx + dy * dy > r * r) continue;
                    best = Math.max(best, color[ny * width + nx] >>> 24);
                }
                if (best > 0) outline[y * width + x] = (safeAlpha(best * 150 / 255) << 24) | 0x0A0806;
            }
        }
        out.setRGB(0, 0, width, height, outline, 0, width);
        return out;
    }

    /** Alpha 251-253 are ShaderFX markers in minigame-lib's text shader. */
    static int safeAlpha(int alpha) { return alpha >= 251 && alpha <= 253 ? 250 : Math.clamp(alpha, 0, 255); }

    private record View(double yaw, double pitch, int scale, double minX, double maxY, int pad) {
        /** Returns {screenX, screenY, depth} for a model point. */
        double[] project(double x, double y, double z) {
            y -= 12; // rotate around the neck
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
            double rx = x * cy + z * sy, rz = -x * sy + z * cy;
            double ry = y * cp + rz * sp, rz2 = -y * sp + rz * cp;
            ry += 12;
            return new double[]{(rx - minX) * scale + pad, (maxY - ry) * scale + pad, rz2};
        }
        double[] normal(double x, double y, double z) {
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
            double rx = x * cy + z * sy, rz = -x * sy + z * cy;
            return new double[]{rx, y * cp + rz * sp, -y * sp + rz * cp};
        }
    }

    /** A textured quad: origin, u axis (width texels), v axis (height texels), texture rect. */
    private record Face(double[] o, double[] u, double[] v, double[] n, int tu, int tv, int tw, int th, boolean flipU) {}

    private record Box(float x, float y, float z, int w, int h, int d, int u0, int v0, float inflate, boolean mirror) {
        Box(float x, float y, float z, int w, int h, int d, int u0, int v0, float inflate) { this(x, y, z, w, h, d, u0, v0, inflate, false); }
        Box mirrored() { return new Box(x, y, z, w, h, d, u0, v0, inflate, true); }
        List<Face> faces() {
            double x0 = x - inflate, y0 = y - inflate, z0 = z - inflate;
            double x1 = x + w + inflate, y1 = y + h + inflate, z1 = z + d + inflate;
            double W = x1 - x0, H = y1 - y0, D = z1 - z0;
            var faces = new ArrayList<Face>();
            // Skin layout per box: [right d][front w][left d][back w] below [top w][bottom w].
            faces.add(new Face(new double[]{x0, y1, z1}, new double[]{W, 0, 0}, new double[]{0, -H, 0}, new double[]{0, 0, 1}, u0 + d, v0 + d, w, h, mirror));   // front
            faces.add(new Face(new double[]{x0, y1, z0}, new double[]{0, 0, D}, new double[]{0, -H, 0}, new double[]{-1, 0, 0}, mirror ? u0 + d + w : u0, v0 + d, d, h, mirror)); // right side
            faces.add(new Face(new double[]{x1, y1, z1}, new double[]{0, 0, -D}, new double[]{0, -H, 0}, new double[]{1, 0, 0}, mirror ? u0 : u0 + d + w, v0 + d, d, h, mirror)); // left side
            faces.add(new Face(new double[]{x1, y1, z0}, new double[]{-W, 0, 0}, new double[]{0, -H, 0}, new double[]{0, 0, -1}, u0 + 2 * d + w, v0 + d, w, h, mirror)); // back
            faces.add(new Face(new double[]{x0, y1, z0}, new double[]{W, 0, 0}, new double[]{0, 0, D}, new double[]{0, 1, 0}, u0 + d, v0, w, d, mirror));       // top
            faces.add(new Face(new double[]{x0, y0, z1}, new double[]{W, 0, 0}, new double[]{0, 0, -D}, new double[]{0, -1, 0}, u0 + d + w, v0, w, d, mirror)); // bottom
            return faces;
        }
    }

    private static void raster(Face f, BufferedImage skin, View view, int width, int height, int[] color, double[] depth, int rim) {
        double[] n = view.normal(f.n[0], f.n[1], f.n[2]);
        if (n[2] <= 1e-6) return; // back face
        double[] p0 = view.project(f.o[0], f.o[1], f.o[2]);
        double[] pu = view.project(f.o[0] + f.u[0], f.o[1] + f.u[1], f.o[2] + f.u[2]);
        double[] pv = view.project(f.o[0] + f.v[0], f.o[1] + f.v[1], f.o[2] + f.v[2]);
        double ax = pu[0] - p0[0], ay = pu[1] - p0[1], bx = pv[0] - p0[0], by = pv[1] - p0[1];
        double det = ax * by - ay * bx;
        if (Math.abs(det) < 1e-9) return;
        int minX = (int) Math.floor(Math.min(Math.min(p0[0], pu[0]), Math.min(pv[0], pu[0] + bx)));
        int maxX = (int) Math.ceil(Math.max(Math.max(p0[0], pu[0]), Math.max(pv[0], pu[0] + bx)));
        int minY = (int) Math.floor(Math.min(Math.min(p0[1], pu[1]), Math.min(pv[1], pu[1] + by)));
        int maxY = (int) Math.ceil(Math.max(Math.max(p0[1], pu[1]), Math.max(pv[1], pu[1] + by)));
        // Light from the upper left front, plus a warm rim from the right.
        double light = 0.62 + 0.38 * Math.max(0, n[0] * -0.35 + n[1] * 0.55 + n[2] * 0.76);
        double rimAmount = Math.max(0, n[0]) * 0.35;
        for (int py = Math.max(0, minY); py < Math.min(height, maxY); py++) for (int px = Math.max(0, minX); px < Math.min(width, maxX); px++) {
            double dx = px + 0.5 - p0[0], dy = py + 0.5 - p0[1];
            double s = (dx * by - dy * bx) / det, t = (ax * dy - ay * dx) / det;
            if (s < 0 || s >= 1 || t < 0 || t >= 1) continue;
            double z = p0[2] + s * (pu[2] - p0[2]) + t * (pv[2] - p0[2]);
            int i = py * width + px;
            if (z <= depth[i]) continue;
            double su = f.flipU ? 1 - s : s;
            int tx = f.tu + Math.min(f.tw - 1, (int) (su * f.tw)), ty = f.tv + Math.min(f.th - 1, (int) (t * f.th));
            int argb = skin.getRGB(tx, ty);
            int a = argb >>> 24;
            if (a < 26) continue;
            depth[i] = z;
            int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            r = (int) Math.min(255, r * light + ((rim >> 16) & 255) * rimAmount);
            g = (int) Math.min(255, g * light + ((rim >> 8) & 255) * rimAmount);
            b = (int) Math.min(255, b * light + (rim & 255) * rimAmount);
            color[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }
}
