package kim.biryeong.minigamelib.cinematic.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generated sprites of the cinematic dialogue box, shipped as bitmap glyphs of the font {@link #FONT} in the Polymer
 * pack: letterbox block, vignette, corner shade, the dotted name curve (animation frames), the continue indicator
 * and one chest-up portrait per registered skin.
 *
 * <p>Every sprite glyph has ascent {@code height - 2}, so its bottom sits on the text block's bottom edge
 * (see {@link kim.biryeong.minigamelib.cinematic.ScreenOverlay.Sprite#place}).
 */
public final class DialogueAssets {
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("minigamelib", "dialogue");
    public static final int CURVE_FRAMES = 24;

    /** One bitmap glyph. {@code aspect} = image width / height. */
    /**
     * One sprite: {@code segments} consecutive codepoints from {@code codepoint}, each a slice of at most 256 px
     * (the client's font atlas page size), joined by a -1 advance so the strip lays out like one glyph.
     */
    public record Glyph(String id, int codepoint, int height, float aspect, int segments) {
        public Glyph(String id, int codepoint, int height, float aspect) { this(id, codepoint, height, aspect, 1); }
        public String string() {
            var out = new StringBuilder();
            for (int i = 0; i < segments; i++) { if (i > 0) out.append(JOIN); out.appendCodePoint(codepoint + i); }
            return out.toString();
        }
        public Component component() { return Component.literal(string()).withStyle(style()); }
        public Component component(int rgb) { return Component.literal(string()).withStyle(style().withColor(rgb)); }
    }
    public static Style style() { return Style.EMPTY.withFont(new FontDescription.Resource(FONT)); }

    private record Source(Glyph glyph, java.util.function.Supplier<BufferedImage> image, int ascent) {}
    private static final Map<String, Source> STATIC = new LinkedHashMap<>();
    private static final Map<String, Glyph> PORTRAITS = new ConcurrentHashMap<>();
    private static final Map<String, Skin> SKINS = new ConcurrentHashMap<>();
    private record Skin(BufferedImage portrait) {}

    /** Largest bitmap glyph the client font atlas accepts. */
    public static final int MAX_GLYPH = 256;
    private static final char JOIN = '\uE0F1';
    public static final Glyph SOLID = add("solid", 0xE000, 16, 16, 16, DialogueAssets::solid);
    public static final Glyph VIGNETTE = add("vignette", 0xE001, 64, 256, 144, DialogueAssets::vignette);
    public static final Glyph SHADE = add("shade", 0xE002, 64, 256, 128, DialogueAssets::shade);
    /** Inline glyph sized like a regular character (height 8, ascent 7). */
    public static final Glyph INDICATOR = addInline("indicator", 0xE003, DialogueAssets::indicator);
    /** Invisible glyph with an exact advance; a first line made of it fixes the width of a left-aligned block. */
    public static final int SPACER_ADVANCE = 300;
    public static final Glyph SPACER = new Glyph("spacer", 0xE0F0, 8, SPACER_ADVANCE / 8f);
    private static final Glyph[] CURVE = new Glyph[CURVE_FRAMES];
    static {
        for (int i = 0; i < CURVE_FRAMES; i++) { int frame = i; CURVE[i] = add("curve_" + i, 0xE100 + i * 4, 32, 768, 48, () -> curveImage(frame)); }
    }
    private static Path portraitFolder;
    private static boolean initialized;

    private DialogueAssets() {}

    public static Glyph curve(int frame) { return CURVE[Math.floorMod(frame, CURVE_FRAMES)]; }

    /** Registers pack generation and the folder of skins ({@code <id>.png}, 64x64) baked into portraits. Idempotent. */
    public static synchronized void init(Path portraits) {
        portraitFolder = portraits;
        DialogueFonts.init(portraits.resolveSibling("fonts"));
        if (initialized) return;
        initialized = true;
        try { Files.createDirectories(portraits); } catch (IOException e) { throw new IllegalStateException(e); }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            DialogueFonts.addToPack(builder);
            scanFolder();
            kim.biryeong.minigamelib.cinematic.Skins.addToPack(builder);
            var providers = new JsonArray();
            var sources = new ArrayList<>(STATIC.values());
            for (var entry : new TreeMap<>(PORTRAITS).entrySet()) {
                var skin = SKINS.get(entry.getKey());
                if (skin == null) continue;
                var glyph = entry.getValue();
                sources.add(new Source(glyph, skin::portrait, glyph.height() - 2));
            }
            for (var source : sources) {
                var glyph = source.glyph();
                BufferedImage whole = source.image().get();
                int n = glyph.segments(), slice = whole.getWidth() / n;
                if (slice * n != whole.getWidth() || slice > MAX_GLYPH || whole.getHeight() > MAX_GLYPH)
                    throw new IllegalStateException("Dialogue glyph " + glyph.id() + " must split into slices of at most 256x256");
                for (int i = 0; i < n; i++) {
                    var file = "font/dialogue/" + glyph.id() + (n == 1 ? "" : "_" + i) + ".png";
                    BufferedImage image = sanitize(whole.getSubimage(i * slice, 0, slice, whole.getHeight()));
                    var out = new ByteArrayOutputStream();
                    try { ImageIO.write(image, "PNG", out); } catch (IOException e) { throw new IllegalStateException(e); }
                    builder.addData("assets/minigamelib/textures/" + file, out.toByteArray());
                    var provider = new JsonObject();
                    provider.addProperty("type", "bitmap");
                    provider.addProperty("file", "minigamelib:" + file);
                    provider.addProperty("height", glyph.height());
                    provider.addProperty("ascent", source.ascent());
                    var chars = new JsonArray(); chars.add(new String(Character.toChars(glyph.codepoint() + i)));
                    provider.add("chars", chars);
                    providers.add(provider);
                }
            }
            var space = new JsonObject();
            space.addProperty("type", "space");
            var advances = new JsonObject(); advances.addProperty(SPACER.string(), SPACER_ADVANCE); advances.addProperty(String.valueOf(JOIN), -1);
            space.add("advances", advances);
            providers.add(space);
            var font = new JsonObject();
            font.add("providers", providers);
            builder.addData("assets/minigamelib/font/dialogue.json", font.toString().getBytes(StandardCharsets.UTF_8));
        });
    }

    /**
     * Registers a skin (64x64 or legacy 64x32) whose chest-up portrait is baked into the next pack build.
     * Returns the portrait glyph; its codepoint depends only on the ID, so rebuilt packs keep it.
     */
    public static Glyph registerSkin(String id, BufferedImage skin, boolean slim) {
        return registerPortraitImage(id, SkinBustRenderer.render(skin, slim));
    }
    public static Glyph registerSkin(String id, BufferedImage skin) { return registerSkin(id, skin, SkinBustRenderer.isSlim(skin)); }
    /** Registers an already drawn portrait image (any size, transparent background). */
    public static Glyph registerPortraitImage(String id, BufferedImage image) {
        if (image.getWidth() > MAX_GLYPH || image.getHeight() > MAX_GLYPH) image = fit(image, MAX_GLYPH);
        SKINS.put(id(id), new Skin(image));
        return portraitGlyph(id, (float) image.getWidth() / image.getHeight());
    }
    public static Glyph registerSkin(String id, InputStream png) throws IOException { return registerSkin(id, ImageIO.read(png)); }

    /** The portrait glyph, or empty when no skin with this ID is registered or in the portrait folder. */
    public static Optional<Glyph> portrait(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        var glyph = PORTRAITS.get(id);
        if (glyph == null && portraitFolder != null && Files.isRegularFile(portraitFolder.resolve(id + ".png"))) {
            load(portraitFolder.resolve(id + ".png"));
            glyph = PORTRAITS.get(id);
        }
        return Optional.ofNullable(glyph);
    }
    public static Set<String> portraits() { return Set.copyOf(PORTRAITS.keySet()); }
    public static Path portraitFolder() { return portraitFolder; }

    private static Glyph portraitGlyph(String id, float aspect) {
        return PORTRAITS.compute(id(id), (key, old) -> {
            if (old != null) return new Glyph(old.id(), old.codepoint(), old.height(), aspect);
            // Supplementary private use plane B, stable per ID; probe on the rare collision.
            int base = 0x100000 + Math.floorMod(key.hashCode(), 0xFFF0);
            var used = new HashSet<Integer>(); PORTRAITS.values().forEach(g -> used.add(g.codepoint()));
            while (used.contains(base)) base = 0x100000 + Math.floorMod(base - 0x100000 + 1, 0xFFF0);
            return new Glyph("portrait_" + key, base, 128, aspect);
        });
    }
    private static String id(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Portrait ID must be 1-64 of a-z 0-9 _ . -");
        return id;
    }
    private static void scanFolder() {
        if (portraitFolder == null || !Files.isDirectory(portraitFolder)) return;
        try (var files = Files.list(portraitFolder)) {
            for (var file : files.toList()) if (file.getFileName().toString().endsWith(".png")) load(file);
        } catch (IOException e) { LoggerFactory.getLogger("minigamelib").warn("Cannot list dialogue portraits", e); }
    }
    /** {@code <id>.png}: a 64x64 (or 64x32) skin is rendered, any other size is used as the portrait itself. */
    private static void load(Path file) {
        var name = file.getFileName().toString();
        var id = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
        try {
            var image = ImageIO.read(file.toFile());
            if (image == null) throw new IOException("not a PNG");
            if (image.getWidth() == 64 && image.getHeight() == 64) kim.biryeong.minigamelib.cinematic.Skins.register(id, image);
            else if (image.getWidth() == 64 && image.getHeight() == 32) registerSkin(id, image);
            else registerPortraitImage(id, image);
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("minigamelib").warn("Skipping dialogue portrait {}: {}", file, e.getMessage());
        }
    }

    private static BufferedImage fit(BufferedImage image, int max) {
        double scale = Math.min((double) max / image.getWidth(), (double) max / image.getHeight());
        int w = Math.max(1, (int) (image.getWidth() * scale)), h = Math.max(1, (int) (image.getHeight() * scale));
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(out);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, 0, 0, w, h, null); g.dispose();
        return out;
    }
    private static Glyph add(String id, int codepoint, int height, int width, int imageHeight, java.util.function.Supplier<BufferedImage> image) {
        var glyph = new Glyph(id, codepoint, height, (float) width / imageHeight, (width + MAX_GLYPH - 1) / MAX_GLYPH);
        STATIC.put(id, new Source(glyph, image, height - 2));
        return glyph;
    }
    private static Glyph addInline(String id, int codepoint, java.util.function.Supplier<BufferedImage> image) {
        var glyph = new Glyph(id, codepoint, 8, 1f);
        STATIC.put(id, new Source(glyph, image, 7));
        return glyph;
    }

    /** Glyph width is the rightmost non-transparent column, so keep an invisible pixel in two corners; avoid ShaderFX alphas. */
    static BufferedImage sanitize(BufferedImage source) {
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = source.getRGB(x, y);
            int a = SkinBustRenderer.safeAlpha(argb >>> 24);
            image.setRGB(x, y, a == 0 ? 0 : (a << 24) | (argb & 0xFFFFFF));
        }
        int w = image.getWidth() - 1, h = image.getHeight() - 1;
        if ((image.getRGB(w, h) >>> 24) == 0) image.setRGB(w, h, 0x01000000);
        if ((image.getRGB(w, 0) >>> 24) == 0) image.setRGB(w, 0, 0x01000000);
        return image;
    }

    // ---- generated images ---------------------------------------------------------------------------------------

    private static BufferedImage solid() {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xFFFFFFFF);
        return image;
    }
    private static BufferedImage vignette() {
        int w = 256, h = 144;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double dx = (x + 0.5 - w / 2.0) / (w / 2.0), dy = (y + 0.5 - h / 2.0) / (h / 2.0);
            double d = Math.sqrt(dx * dx * 0.85 + dy * dy);
            double t = smooth(Math.clamp((d - 0.45) / 0.75, 0, 1));
            image.setRGB(x, y, ((int) Math.round(t * 215) << 24) | 0xFFFFFF);
        }
        return image;
    }
    /** Dark band behind the dialogue: fades in from the left and towards the top, white so the text colour tints it. */
    private static BufferedImage shade() {
        int w = 256, h = 128;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double u = (x + 0.5) / w, v = (y + 0.5) / h;
            double horizontal = smooth(Math.clamp(u / 0.38, 0, 1));
            double vertical = smooth(Math.clamp(v / 0.62, 0, 1));
            image.setRGB(x, y, ((int) Math.round(horizontal * vertical * 205) << 24) | 0xFFFFFF);
        }
        return image;
    }
    private static BufferedImage indicator() {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(image);
        g.setColor(new Color(255, 255, 255, 235));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(5, 5, 21, 21));
        g.fill(new Ellipse2D.Double(12, 12, 7, 7));
        g.dispose();
        return image;
    }
    /** Dotted curve rising to the right into a solid underline; a warm glow travels along it over the frames. */
    private static BufferedImage curveImage(int frame) {
        int w = 768, h = 48;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(image);
        double glow = -0.15 + 1.3 * frame / (CURVE_FRAMES - 1.0);
        int steps = 520;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = 4 + t * (w - 8), y = curveY(t, h);
            double underline = smooth(Math.clamp((t - 0.62) / 0.12, 0, 1));
            boolean dot = i % 4 == 0;
            double fadeIn = smooth(Math.clamp(t / 0.25, 0, 1));
            double base = (dot ? 0.85 : 0) * (1 - underline) + 0.95 * underline;
            double light = Math.exp(-Math.pow((t - glow) / 0.06, 2));
            double alpha = Math.min(1, base * fadeIn + light * 0.9 * fadeIn);
            if (alpha <= 0.01) continue;
            int r = (int) lerp(200, 255, Math.max(underline, light)), gg = (int) lerp(196, lerp(150, 215, light), Math.max(underline, light)), b = (int) lerp(190, lerp(70, 160, light), Math.max(underline, light));
            g.setColor(new Color(r, gg, b, (int) Math.round(alpha * 240)));
            double size = (dot ? 3.4 : 2.2) + underline * 0.6 + light * 2.4;
            g.fill(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
            if (light > 0.2) {
                g.setColor(new Color(255, 190, 110, (int) (light * 60)));
                double halo = 6 + light * 6;
                g.fill(new Ellipse2D.Double(x - halo / 2, y - halo / 2, halo, halo));
            }
        }
        g.dispose();
        return image;
    }
    private static double curveY(double t, int h) {
        // Low and flat on the left, rising through the middle, flat again under the name.
        double rise = smooth(Math.clamp((t - 0.18) / 0.5, 0, 1));
        return h - 8 - rise * (h - 22);
    }
    private static Graphics2D graphics(BufferedImage image) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
