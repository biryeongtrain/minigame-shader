package kim.biryeong.minigamelib.cinematic.dialogue;

import kim.biryeong.minigamelib.cinematic.CinematicData.DialogueLine;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Look of a dialogue line: colours of the speaker name, the dotted line under it, the settled text and the freshly
 * typed text, plus Minecraft text formatting (font, bold, italic, underline, strikethrough, obfuscation, shadow) of
 * the name and the body. Every field is optional; unset fields fall through, in order, to the line's style, the
 * line's {@code color} (accent), {@linkplain #forSpeaker the speaker's style}, {@linkplain #setDefault the global
 * default} and the built-in look.
 *
 * <p>Immutable; build with {@code DialogueStyle.of().nameColor(0xE05555).name(TextFormat.of().bold(true))}.
 * Stored as JSON in scene files (null fields are omitted).
 */
public record DialogueStyle(Integer nameColor, Integer lineColor, Integer textColor, Integer highlightColor,
                            TextFormat name, TextFormat text, Boolean showLine) {
    public static final DialogueStyle EMPTY = new DialogueStyle(null, null, null, null, null, null, null);
    public static final int DEFAULT_TEXT = 0xF4F1EC;

    public static DialogueStyle of() { return EMPTY; }

    /** Speaker name colour. Also the default line and highlight colour. */
    public DialogueStyle nameColor(int rgb) { return new DialogueStyle(rgb & 0xFFFFFF, lineColor, textColor, highlightColor, name, text, showLine); }
    /** Colour of the dotted line under the name. */
    public DialogueStyle lineColor(int rgb) { return new DialogueStyle(nameColor, rgb & 0xFFFFFF, textColor, highlightColor, name, text, showLine); }
    /** Colour of typed text once settled (characters with their own colour keep it). */
    public DialogueStyle textColor(int rgb) { return new DialogueStyle(nameColor, lineColor, rgb & 0xFFFFFF, highlightColor, name, text, showLine); }
    /** Colour newly typed characters fade from. */
    public DialogueStyle highlightColor(int rgb) { return new DialogueStyle(nameColor, lineColor, textColor, rgb & 0xFFFFFF, name, text, showLine); }
    /** Sets name, line and highlight colour at once (what {@code DialogueLine.color} does). */
    public DialogueStyle accent(int rgb) { return nameColor(rgb).lineColor(rgb).highlightColor(rgb); }
    public DialogueStyle name(TextFormat format) { return new DialogueStyle(nameColor, lineColor, textColor, highlightColor, format, text, showLine); }
    public DialogueStyle text(TextFormat format) { return new DialogueStyle(nameColor, lineColor, textColor, highlightColor, name, format, showLine); }
    public DialogueStyle showLine(boolean show) { return new DialogueStyle(nameColor, lineColor, textColor, highlightColor, name, text, show); }
    public DialogueStyle hideLine() { return showLine(false); }

    /** {@code over}'s set fields win. */
    public DialogueStyle merge(DialogueStyle over) {
        if (over == null || over.equals(EMPTY)) return this;
        return new DialogueStyle(or(over.nameColor, nameColor), or(over.lineColor, lineColor), or(over.textColor, textColor),
                or(over.highlightColor, highlightColor), TextFormat.merge(name, over.name), TextFormat.merge(text, over.text), or(over.showLine, showLine));
    }
    private static <T> T or(T a, T b) { return a != null ? a : b; }

    /**
     * Minecraft text formatting. {@code font} is a font ID ({@code minecraft:default}, {@code minecraft:uniform},
     * {@code minigamelib:dialogue_text}, a font of your own pack); null keeps the dialogue font.
     */
    public record TextFormat(String font, Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough,
                             Boolean obfuscated, Boolean shadow) {
        public static final TextFormat EMPTY = new TextFormat(null, null, null, null, null, null, null);
        public TextFormat {
            if (font != null && font.isBlank()) font = null;
            if (font != null) Identifier.parse(font.toLowerCase(Locale.ROOT));
        }
        public static TextFormat of() { return EMPTY; }
        public TextFormat font(String id) { return new TextFormat(id, bold, italic, underlined, strikethrough, obfuscated, shadow); }
        public TextFormat font(Identifier id) { return font(id.toString()); }
        public TextFormat bold(boolean on) { return new TextFormat(font, on, italic, underlined, strikethrough, obfuscated, shadow); }
        public TextFormat italic(boolean on) { return new TextFormat(font, bold, on, underlined, strikethrough, obfuscated, shadow); }
        public TextFormat underlined(boolean on) { return new TextFormat(font, bold, italic, on, strikethrough, obfuscated, shadow); }
        public TextFormat strikethrough(boolean on) { return new TextFormat(font, bold, italic, underlined, on, obfuscated, shadow); }
        public TextFormat obfuscated(boolean on) { return new TextFormat(font, bold, italic, underlined, strikethrough, on, shadow); }
        /** Drop shadow behind the glyphs (on by default). */
        public TextFormat shadow(boolean on) { return new TextFormat(font, bold, italic, underlined, strikethrough, obfuscated, on); }

        static TextFormat merge(TextFormat under, TextFormat over) {
            if (under == null) return over;
            if (over == null) return under;
            return new TextFormat(or(over.font, under.font), or(over.bold, under.bold), or(over.italic, under.italic), or(over.underlined, under.underlined),
                    or(over.strikethrough, under.strikethrough), or(over.obfuscated, under.obfuscated), or(over.shadow, under.shadow));
        }
        /** Applies this format over {@code base} (whose font is the fallback). */
        public Style apply(Style base) {
            var style = base;
            if (font != null) style = style.withFont(new FontDescription.Resource(Identifier.parse(font.toLowerCase(Locale.ROOT))));
            if (bold != null) style = style.withBold(bold);
            if (italic != null) style = style.withItalic(italic);
            if (underlined != null) style = style.withUnderlined(underlined);
            if (strikethrough != null) style = style.withStrikethrough(strikethrough);
            if (obfuscated != null) style = style.withObfuscated(obfuscated);
            return style;
        }
    }

    // ---- defaults -----------------------------------------------------------------------------------------------

    private static volatile DialogueStyle global = EMPTY;
    private static final Map<String, DialogueStyle> SPEAKERS = new ConcurrentHashMap<>();

    /** Style every line starts from (call from your mod initializer or on server start). */
    public static void setDefault(DialogueStyle style) { global = style == null ? EMPTY : style; }
    public static DialogueStyle getDefault() { return global; }
    /** Style of every line whose speaker (plain text, case-insensitive) is {@code speaker}; null removes it. */
    public static void forSpeaker(String speaker, DialogueStyle style) {
        var key = DialogueText.plain(speaker).toLowerCase(Locale.ROOT);
        if (style == null) SPEAKERS.remove(key); else SPEAKERS.put(key, style);
    }
    public static DialogueStyle speaker(String speaker) { return speaker == null ? EMPTY : SPEAKERS.getOrDefault(DialogueText.plain(speaker).toLowerCase(Locale.ROOT), EMPTY); }

    /** Final values for one line. */
    public record Resolved(int nameColor, int lineColor, int textColor, int highlightColor, Style nameStyle, Style textStyle,
                           boolean nameShadow, boolean textShadow, boolean showLine) {}

    public static Resolved resolve(DialogueLine line) {
        var style = global.merge(speaker(line.speaker()));
        if (line.color() != null) style = style.merge(EMPTY.accent(line.color()));
        style = style.merge(line.style());
        int name = style.nameColor != null ? style.nameColor : DialogueLine.DEFAULT_COLOR;
        var nameFormat = style.name == null ? TextFormat.EMPTY : style.name;
        var textFormat = style.text == null ? TextFormat.EMPTY : style.text;
        return new Resolved(name, or(style.lineColor, name), or(style.textColor, DEFAULT_TEXT), or(style.highlightColor, name),
                nameFormat.apply(DialogueFonts.nameStyle()), textFormat.apply(DialogueFonts.textStyle()),
                !Boolean.FALSE.equals(nameFormat.shadow), !Boolean.FALSE.equals(textFormat.shadow), !Boolean.FALSE.equals(style.showLine));
    }
}
