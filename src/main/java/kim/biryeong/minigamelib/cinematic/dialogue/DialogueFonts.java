package kim.biryeong.minigamelib.cinematic.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrueType fonts of the dialogue box, shipped in the pack as Minecraft {@code ttf} font providers.
 * Defaults: body text in a Korean/Latin sans (Pretendard SemiBold subset), speaker names in a Latin serif
 * (Libre Baskerville Bold) falling back to the bold sans for Hangul. Both under the SIL OFL 1.1.
 *
 * <p>Override by dropping {@code text.ttf}, {@code name.ttf} or {@code name_fallback.ttf} into
 * {@code config/minigame-lib/dialogue/fonts/} and rebuilding the pack. Widths for wrapping and centring are measured
 * on the server with the same files (AWT), so they match the client to a fraction of a pixel.
 *
 * <p>Other mods can ship more TrueType fonts with {@link #register(Identifier, byte[], float, float)} and use them in
 * {@link DialogueStyle.TextFormat#font} or MiniMessage {@code <font:...>}; fonts shipped some other way can tell the
 * wrapper their widths with {@link #metrics}. Unknown fonts are measured with the default font's widths.
 */
public final class DialogueFonts {
    private DialogueFonts() {}
    public static final Identifier TEXT = Identifier.fromNamespaceAndPath("minigamelib", "dialogue_text");
    public static final Identifier NAME = Identifier.fromNamespaceAndPath("minigamelib", "dialogue_name");
    /** Font size in font pixels (em height) and supersampling of the client raster. */
    public static final float TEXT_SIZE = 9, NAME_SIZE = 11, NAME_FALLBACK_SIZE = 10, OVERSAMPLE = 6;
    /** Vertical shift so the ttf baseline sits like the default font's (font pixels, positive = down). */
    public static final float TEXT_SHIFT = 1, NAME_SHIFT = 1;

    private record Slot(String file, String bundled, float size, float shift) {}
    private static final Slot TEXT_SLOT = new Slot("text.ttf", "dialogue-sans-semibold.ttf", TEXT_SIZE, TEXT_SHIFT);
    private static final Slot NAME_SLOT = new Slot("name.ttf", "dialogue-serif-bold.ttf", NAME_SIZE, NAME_SHIFT);
    private static final Slot NAME_FALLBACK_SLOT = new Slot("name_fallback.ttf", "dialogue-sans-bold.ttf", NAME_FALLBACK_SIZE, NAME_SHIFT);

    /** One font stack: the first face that has a glyph is used, like the client's font set. */
    public static class Face {
        private final List<Font> fonts;
        private final Map<Integer, Float> advances = new ConcurrentHashMap<>();
        private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
        Face(List<Font> fonts) { this.fonts = fonts; }
        /** Advance in font pixels; unknown characters use the default font's widths. */
        public float advance(int codepoint) {
            if (fonts.isEmpty()) return (float) TextMetrics.width(codepoint, false);
            return advances.computeIfAbsent(codepoint, cp -> {
                for (var font : fonts) if (font.canDisplay(cp))
                    return (float) font.createGlyphVector(FRC, new String(Character.toChars(cp))).getGlyphMetrics(0).getAdvanceX();
                return (float) TextMetrics.width(cp, false);
            });
        }
        public float width(String text) {
            float width = 0;
            for (int cp : text.codePoints().toArray()) width += advance(cp);
            return width;
        }
    }

    private static volatile Path folder;
    private static volatile Face text, name;
    private record Custom(byte[] ttf, float size, float shift) {}
    private static final Map<Identifier, Custom> CUSTOM = new ConcurrentHashMap<>();
    private static final Map<Identifier, Face> FACES = new ConcurrentHashMap<>();
    private static final Map<Identifier, java.util.function.IntToDoubleFunction> METRICS = new ConcurrentHashMap<>();
    private static final Face VANILLA = new Face(List.of());

    /**
     * Ships {@code ttf} as font {@code id} ({@code assets/<ns>/font/<path>.json}) in the next pack build and measures
     * it for wrapping. {@code size} is the em height in font pixels (the dialogue body uses 9), {@code shift} moves
     * glyphs down. Register before the pack is built (mod initializer or server start).
     */
    public static void register(Identifier id, byte[] ttf, float size, float shift) {
        Objects.requireNonNull(ttf);
        if (id.equals(TEXT) || id.equals(NAME)) throw new IllegalArgumentException("Replace the built-in dialogue fonts through config/minigame-lib/dialogue/fonts");
        CUSTOM.put(id, new Custom(ttf.clone(), size, shift));
        FACES.remove(id);
    }
    /** Widths (font pixels, without bold) of a font that reaches the client some other way, e.g. your mod's own pack assets. */
    public static void metrics(Identifier id, java.util.function.IntToDoubleFunction advance) { METRICS.put(id, advance); FACES.remove(id); }

    /** Measuring face of a style's font; unknown fonts use the default font's widths. */
    public static Face face(net.minecraft.network.chat.FontDescription font) {
        if (!(font instanceof net.minecraft.network.chat.FontDescription.Resource(Identifier id))) return VANILLA;
        if (id.equals(TEXT)) return text();
        if (id.equals(NAME)) return name();
        return FACES.computeIfAbsent(id, key -> {
            var metrics = METRICS.get(key);
            if (metrics != null) return new Face(List.of()) { @Override public float advance(int cp) { return (float) metrics.applyAsDouble(cp); } };
            var custom = CUSTOM.get(key);
            if (custom != null) {
                try { return new Face(List.of(Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(custom.ttf())).deriveFont(custom.size()))); }
                catch (Exception e) { LoggerFactory.getLogger("minigamelib").warn("Cannot measure dialogue font {}: {}", key, e.toString()); }
            }
            return VANILLA;
        });
    }

    static void init(Path fonts) { folder = fonts; text = null; name = null; }

    public static Style textStyle() { return Style.EMPTY.withFont(new FontDescription.Resource(TEXT)); }
    public static Style nameStyle() { return Style.EMPTY.withFont(new FontDescription.Resource(NAME)); }

    public static Face text() {
        var face = text;
        if (face == null) text = face = new Face(java.util.stream.Stream.of(load(TEXT_SLOT)).filter(Objects::nonNull).toList());
        return face;
    }
    public static Face name() {
        var face = name;
        if (face == null) name = face = new Face(java.util.stream.Stream.of(load(NAME_SLOT), load(NAME_FALLBACK_SLOT)).filter(Objects::nonNull).toList());
        return face;
    }

    /** Adds the ttf files and font definitions to the pack. */
    static void addToPack(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder) {
        text = null; name = null; // pick up replaced files
        builder.addData("assets/minigamelib/font/dialogue_text.json", definition(TEXT_SLOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        builder.addData("assets/minigamelib/font/dialogue_name.json", definition(NAME_SLOT, NAME_FALLBACK_SLOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (var slot : List.of(TEXT_SLOT, NAME_SLOT, NAME_FALLBACK_SLOT)) {
            var bytes = bytes(slot);
            if (bytes != null) builder.addData("assets/minigamelib/font/dialogue/" + slot.file(), bytes);
        }
        for (var entry : CUSTOM.entrySet()) {
            var id = entry.getKey(); var custom = entry.getValue();
            var file = id.getNamespace() + ":" + id.getPath() + ".ttf";
            builder.addData("assets/" + id.getNamespace() + "/font/" + id.getPath() + ".ttf", custom.ttf());
            builder.addData("assets/" + id.getNamespace() + "/font/" + id.getPath() + ".json",
                    definition(file, custom.size(), custom.shift()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String definition(Slot... slots) {
        var providers = new JsonArray();
        for (var slot : slots) providers.add(provider("minigamelib:dialogue/" + slot.file(), slot.size(), slot.shift()));
        var font = new JsonObject();
        font.add("providers", providers);
        return font.toString();
    }
    private static String definition(String file, float size, float shift) {
        var providers = new JsonArray();
        providers.add(provider(file, size, shift));
        var font = new JsonObject();
        font.add("providers", providers);
        return font.toString();
    }
    private static JsonObject provider(String file, float size, float shiftY) {
        var p = new JsonObject();
        p.addProperty("type", "ttf");
        p.addProperty("file", file);
        p.addProperty("size", size);
        p.addProperty("oversample", OVERSAMPLE);
        var shift = new JsonArray(); shift.add(0); shift.add(shiftY);
        p.add("shift", shift);
        return p;
    }

    private static byte[] bytes(Slot slot) {
        var dir = folder;
        if (dir != null) {
            var file = dir.resolve(slot.file());
            if (Files.isRegularFile(file)) {
                try { return Files.readAllBytes(file); }
                catch (IOException e) { LoggerFactory.getLogger("minigamelib").warn("Cannot read dialogue font {}", file, e); }
            }
        }
        try (InputStream in = DialogueFonts.class.getResourceAsStream("/minigamelib/fonts/" + slot.bundled())) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static Font load(Slot slot) {
        var bytes = bytes(slot);
        if (bytes == null) return null;
        try { return Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(bytes)).deriveFont(slot.size()); }
        catch (Exception e) {
            LoggerFactory.getLogger("minigamelib").warn("Cannot load dialogue font {}: {}", slot.file(), e.toString());
            return null;
        }
    }
}
