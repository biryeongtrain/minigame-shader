package kim.biryeong.minigamelib.cinematic.dialogue;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogue text with per-character Minecraft styles. Speaker names and lines accept MiniMessage tags
 * ({@code <b>}, {@code <i>}, {@code <u>}, {@code <st>}, {@code <obf>}, {@code <#ff8800>}, {@code <red>},
 * {@code <gradient:#f00:#00f>}, {@code <font:minecraft:uniform>}, ...); text without {@code <} is taken literally,
 * and {@code \<} writes a literal {@code <}. Unknown tags stay as text.
 *
 * <p>Widths come from {@link DialogueFonts#face(FontDescription)} plus one font pixel per bold character, the same
 * advance rule the client uses for TrueType and bitmap fonts.
 */
public final class DialogueText {
    private final int[] codepoints;
    private final Style[] styles;

    private DialogueText(int[] codepoints, Style[] styles) { this.codepoints = codepoints; this.styles = styles; }

    /** Parses {@code text} over {@code base}; markup styles override the base where set. */
    public static DialogueText parse(String text, Style base) {
        var cps = new ArrayList<Integer>(); var styles = new ArrayList<Style>();
        if (text.indexOf('<') < 0) {
            text.codePoints().forEach(cp -> { cps.add(cp); styles.add(base); });
        } else {
            walk(MiniMessage.miniMessage().deserialize(text), base, cps, styles);
        }
        int[] out = new int[cps.size()];
        for (int i = 0; i < out.length; i++) out[i] = cps.get(i);
        return new DialogueText(out, styles.toArray(Style[]::new));
    }
    /** Text without markup, e.g. for matching speaker names. */
    public static String plain(String text) {
        if (text == null) return "";
        if (text.indexOf('<') < 0) return text;
        var out = new StringBuilder();
        collect(MiniMessage.miniMessage().deserialize(text), out);
        return out.toString();
    }

    private static void walk(net.kyori.adventure.text.Component node, Style inherited, List<Integer> cps, List<Style> styles) {
        var style = merge(inherited, node.style());
        String content = content(node);
        content.codePoints().forEach(cp -> { cps.add(cp); styles.add(style); });
        for (var child : node.children()) walk(child, style, cps, styles);
    }
    private static void collect(net.kyori.adventure.text.Component node, StringBuilder out) {
        out.append(content(node));
        for (var child : node.children()) collect(child, out);
    }
    /** Own text of a node; translations and keybinds show their fallback or key (the server has no client language). */
    private static String content(net.kyori.adventure.text.Component node) {
        return switch (node) {
            case TextComponent t -> t.content();
            case net.kyori.adventure.text.TranslatableComponent t -> t.fallback() != null ? t.fallback() : t.key();
            case net.kyori.adventure.text.KeybindComponent k -> k.keybind();
            default -> "";
        };
    }
    private static Style merge(Style base, net.kyori.adventure.text.format.Style over) {
        var style = base;
        if (over.color() != null) style = style.withColor(over.color().value());
        if (over.font() != null) style = style.withFont(new FontDescription.Resource(Identifier.parse(over.font().asString())));
        style = decoration(style, over, TextDecoration.BOLD);
        style = decoration(style, over, TextDecoration.ITALIC);
        style = decoration(style, over, TextDecoration.UNDERLINED);
        style = decoration(style, over, TextDecoration.STRIKETHROUGH);
        style = decoration(style, over, TextDecoration.OBFUSCATED);
        if (over.shadowColor() != null) style = style.withShadowColor(over.shadowColor().value());
        return style;
    }
    private static Style decoration(Style style, net.kyori.adventure.text.format.Style over, TextDecoration decoration) {
        var state = over.decoration(decoration);
        if (state == TextDecoration.State.NOT_SET) return style;
        boolean on = state == TextDecoration.State.TRUE;
        return switch (decoration) {
            case BOLD -> style.withBold(on);
            case ITALIC -> style.withItalic(on);
            case UNDERLINED -> style.withUnderlined(on);
            case STRIKETHROUGH -> style.withStrikethrough(on);
            case OBFUSCATED -> style.withObfuscated(on);
        };
    }

    public int length() { return codepoints.length; }
    public int codepoint(int i) { return codepoints[i]; }
    public Style style(int i) { return styles[i]; }
    public String string() { return new String(codepoints, 0, codepoints.length); }

    public static float advance(int codepoint, Style style) {
        return DialogueFonts.face(style.getFont()).advance(codepoint) + (style.isBold() ? 1 : 0);
    }
    public float width() {
        float w = 0;
        for (int i = 0; i < codepoints.length; i++) w += advance(codepoints[i], styles[i]);
        return w;
    }
    public DialogueText slice(int from, int to) {
        return new DialogueText(java.util.Arrays.copyOfRange(codepoints, from, to), java.util.Arrays.copyOfRange(styles, from, to));
    }

    /** Rows no wider than {@code maxWidth}, breaking at spaces when possible and at every {@code \n}. */
    public List<DialogueText> wrap(float maxWidth) {
        var rows = new ArrayList<DialogueText>();
        int start = 0;
        while (start <= codepoints.length) {
            int end = start; float width = 0; int lastSpace = -1;
            while (end < codepoints.length && codepoints[end] != '\n') {
                float w = advance(codepoints[end], styles[end]);
                if (width + w > maxWidth && end > start) break;
                if (codepoints[end] == ' ') lastSpace = end;
                width += w; end++;
            }
            if (end >= codepoints.length || codepoints[end] == '\n') {
                rows.add(slice(start, end));
                start = end + 1; // skip the line break (or finish)
                if (end >= codepoints.length) break;
                continue;
            }
            if (codepoints[end] == ' ') { rows.add(slice(start, end)); start = end + 1; }
            else if (lastSpace > start) { rows.add(slice(start, lastSpace)); start = lastSpace + 1; }
            else { rows.add(slice(start, end)); start = end; }
        }
        return rows;
    }

    /** The whole text as one component (runs of equal style merged). */
    public Component component() { return component(0, codepoints.length, null); }
    /** Characters {@code [from, to)}; {@code color} (nullable) recolours characters per index. */
    public MutableComponent component(int from, int to, java.util.function.IntFunction<Style> styleAt) {
        MutableComponent out = Component.empty();
        var run = new StringBuilder(); Style runStyle = null;
        for (int i = from; i < to; i++) {
            var style = styleAt == null ? styles[i] : styleAt.apply(i);
            if (!style.equals(runStyle) && !run.isEmpty()) { out.append(Component.literal(run.toString()).withStyle(runStyle)); run.setLength(0); }
            runStyle = style; run.appendCodePoint(codepoints[i]);
        }
        if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(runStyle));
        return out;
    }
}
