package kim.biryeong.minigamelib.cinematic.dialogue;

import java.util.ArrayList;
import java.util.List;

/** Approximate advances of Minecraft's default font (font pixels) for server-side line wrapping. */
public final class TextMetrics {
    private TextMetrics() {}
    private static volatile Object font; // eu.pb4.mapcanvas CanvasFont when the Fabric environment provides it

    public static int width(int codepoint, boolean bold) {
        int width = fallback(codepoint);
        try {
            if (font == null) font = eu.pb4.mapcanvas.api.font.DefaultFonts.VANILLA;
            var canvas = (eu.pb4.mapcanvas.api.font.CanvasFont) font;
            if (canvas.containsGlyph(codepoint)) width = canvas.getGlyphWidth(codepoint, 8, 2);
        } catch (Throwable ignored) { font = Boolean.FALSE; }
        return width + (bold ? 1 : 0);
    }
    public static int width(String text, boolean bold) {
        int width = 0;
        for (int cp : text.codePoints().toArray()) width += width(cp, bold);
        return width;
    }

    /** Splits into lines of at most {@code maxWidth}; prefers spaces, keeps explicit line breaks. */
    public static List<String> wrap(String text, int maxWidth, boolean bold) {
        return wrap(text, maxWidth, cp -> width(cp, bold));
    }
    /** Like {@link #wrap(String, int, boolean)} with the advances of {@code face}. */
    public static List<String> wrap(String text, float maxWidth, DialogueFonts.Face face) {
        return wrap(text, maxWidth, face::advance);
    }
    public static List<String> wrap(String text, double maxWidth, java.util.function.IntToDoubleFunction advance) {
        var lines = new ArrayList<String>();
        for (var paragraph : text.split("\n", -1)) {
            var line = new StringBuilder(); double width = 0; int lastSpace = -1;
            for (int cp : paragraph.codePoints().toArray()) {
                double w = advance.applyAsDouble(cp);
                if (width + w > maxWidth && !line.isEmpty()) {
                    if (cp == ' ') { lines.add(line.toString()); line.setLength(0); width = 0; lastSpace = -1; continue; }
                    if (lastSpace > 0) {
                        String head = line.substring(0, lastSpace), tail = line.substring(lastSpace + 1);
                        lines.add(head); line.setLength(0); line.append(tail);
                        width = tail.codePoints().mapToDouble(advance::applyAsDouble).sum();
                    } else { lines.add(line.toString()); line.setLength(0); width = 0; }
                    lastSpace = -1;
                }
                if (cp == ' ') lastSpace = line.length();
                line.appendCodePoint(cp); width += w;
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static int fallback(int cp) {
        if (cp >= 0x1100 && (cp <= 0x11FF || (cp >= 0x2E80 && cp <= 0x9FFF) || (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0xFF00 && cp <= 0xFF60))) return 9;
        return switch (cp) {
            case ' ', 'I', 't', '[', ']', '"', '*' -> 4;
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'f', 'k', '<', '>', '(', ')', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
