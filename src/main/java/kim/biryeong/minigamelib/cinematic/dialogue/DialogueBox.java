package kim.biryeong.minigamelib.cinematic.dialogue;

import kim.biryeong.minigamelib.cinematic.CinematicData.DialogueLine;
import kim.biryeong.minigamelib.cinematic.ScreenOverlay;
import kim.biryeong.minigamelib.displayhud.DisplayHud.HudAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Display;

import java.util.*;

/**
 * Cinematic dialogue box drawn on {@link ScreenOverlay}s: letterbox bars and vignette, a chest-up portrait at the
 * bottom right, the speaker name over a dotted curve, and typewriter text whose newest characters fade from the
 * speaker colour to white. One box can drive several viewers' overlays; call {@link #tick()} once per server tick.
 *
 * <p>Layout uses the 1920x1080 reference of DisplayHud; the box keeps a 16:9 area anchored to the right edge.
 */
public final class DialogueBox {
    // ---- layout (reference pixels) ----
    public static final float BAR = 54;
    static final float TEXT_LEFT = 790, TEXT_TOP = 906, TEXT_PX = 2.75f, LINE_PITCH = 40, TEXT_RIGHT = 1500;
    static final float NAME_RIGHT = 1570, NAME_BASELINE = 846, NAME_PX = 4.0f;
    static final int MAX_LINES = 4;
    static final float PORTRAIT_RIGHT = 1904, PORTRAIT_BOTTOM = 1086, PORTRAIT_HEIGHT = 340;
    static final float CURVE_LEFT = 860, CURVE_TOP = 842, CURVE_WIDTH = 780, CURVE_HEIGHT = 49;
    static final int FADE_CHARS = 7, INTRO_TICKS = 7;
    static final float MAX_TEXT_WIDTH = Math.min((TEXT_RIGHT - TEXT_LEFT) / TEXT_PX, DialogueAssets.SPACER_ADVANCE - 4);

    private final Set<ScreenOverlay> overlays = new LinkedHashSet<>();
    private boolean letterbox;
    private int barColor;
    private DialogueLine line;
    private DialogueStyle.Resolved style;
    private DialogueText speaker;
    private List<DialogueText> lines = List.of();
    private int totalChars;
    private double revealed, head;
    /** Eased per-tick animation state, sent with a one-tick interpolation so the client smooths between ticks. */
    private double bars, presence, entrance = 1;
    private int frame;
    private boolean visible, entering;
    private String shownSpeaker, shownPortrait;

    public void addViewer(ScreenOverlay overlay) { if (overlays.add(overlay)) render(overlay, 0); }
    public void removeViewer(ScreenOverlay overlay) { overlays.remove(overlay); }
    public Set<ScreenOverlay> viewers() { return Collections.unmodifiableSet(overlays); }

    public void letterbox(boolean on, int color) { letterbox = on; barColor = color & 0xFFFFFF; }
    public boolean letterbox() { return letterbox; }

    /** Starts typing {@code line}. A different speaker or portrait plays the entrance animation first. */
    public void show(DialogueLine line) { show(line, 0); }
    /** Like {@link #show(DialogueLine)} but as if {@code elapsedTicks} had already passed (preview from the middle). */
    public void show(DialogueLine line, int elapsedTicks) {
        boolean newSpeaker = !visible || !Objects.equals(shownSpeaker, line.speaker()) || !Objects.equals(shownPortrait, line.portrait());
        this.line = line;
        style = DialogueStyle.resolve(line);
        speaker = DialogueText.parse(line.speaker(), style.nameStyle().withColor(style.nameColor()));
        lines = DialogueText.parse(line.text(), style.textStyle()).wrap(MAX_TEXT_WIDTH);
        if (lines.size() > MAX_LINES) lines = List.copyOf(lines.subList(0, MAX_LINES));
        totalChars = lines.stream().mapToInt(DialogueText::length).sum();
        revealed = 0; head = 0; visible = true;
        shownSpeaker = line.speaker(); shownPortrait = line.portrait();
        if (elapsedTicks > 0) {
            head = elapsedTicks * (double) line.speed() / 20.0; revealed = Math.min(totalChars, head);
            entrance = 1; presence = 1; entering = false;
        } else if (newSpeaker) { entrance = 0; entering = true; }
    }
    /** Fades the box out; the letterbox is controlled separately. */
    public void hide() { visible = false; }
    public boolean visible() { return visible; }
    public DialogueLine line() { return visible ? line : null; }
    /** Shows the whole line at once. */
    public void reveal() { revealed = totalChars; head = Math.max(head, totalChars); entrance = 1; entering = false; }
    public boolean revealed() { return line == null || !visible || revealed >= totalChars; }

    public void tick() {
        frame++;
        bars = approach(bars, letterbox ? 1 : 0, 1 / 12.0);
        presence = approach(presence, visible ? 1 : 0, visible ? 1 / 4.0 : 1 / 6.0);
        if (entering) { entrance = Math.min(1, entrance + 1.0 / INTRO_TICKS); entering = entrance < 1; }
        // Text starts once the speaker is mostly in.
        if (visible && line != null && entrance >= 0.5) { head += line.speed() / 20.0; revealed = Math.min(totalChars, head); }
        if (!visible && presence <= 0) line = null;
        for (var overlay : overlays) render(overlay, 1);
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static double easeOut(double t) { t = Math.clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); }
    private static int alpha(double a) { return (int) Math.round(Math.clamp(a, 0, 1) * 255); }

    // ---- rendering --------------------------------------------------------------------------------------------

    private void render(ScreenOverlay overlay, int t) {
        // Cinema frame: bars slide in from the edges, vignette fades with them.
        double frameIn = easeOut(bars);
        float barOffset = (float) ((1 - frameIn) * (BAR + 4));
        overlay.sprite("dialogue/bar_top").text(DialogueAssets.SOLID.component(barColor))
                .place(-40, -barOffset, 2000, BAR, 20, 16, 1, t);
        overlay.sprite("dialogue/bar_bottom").text(DialogueAssets.SOLID.component(barColor))
                .place(-40, 1080 - BAR + barOffset, 2000, BAR, 20, 16, 1, t);
        overlay.sprite("dialogue/vignette").text(DialogueAssets.VIGNETTE.component(0x000000))
                .place(0, 0, 1920, 1080, 10, 64, DialogueAssets.VIGNETTE.aspect(), t).opacity(alpha(frameIn), t);

        double box = easeOut(presence);
        overlay.sprite("dialogue/shade").alignment(HudAlignment.RIGHT).text(DialogueAssets.SHADE.component(0x000000))
                .place(380, 700, 1560, 400, 30, 64, DialogueAssets.SHADE.aspect(), t).opacity(alpha(box), t);

        var portrait = overlay.sprite("dialogue/portrait").alignment(HudAlignment.RIGHT);
        boolean nameShadow = style == null || style.nameShadow(), textShadow = style == null || style.textShadow();
        var name = overlay.sprite("dialogue/name").alignment(HudAlignment.RIGHT).flags(nameShadow ? Display.TextDisplay.FLAG_SHADOW : 0);
        var curve = overlay.sprite("dialogue/curve").alignment(HudAlignment.RIGHT);
        var text = new ScreenOverlay.Sprite[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) text[i] = overlay.sprite("dialogue/text_" + i).alignment(HudAlignment.RIGHT)
                .flags((byte) ((textShadow ? Display.TextDisplay.FLAG_SHADOW : 0) | Display.TextDisplay.FLAG_ALIGN_LEFT));
        if (line == null) {
            portrait.opacity(0, t); name.opacity(0, t); curve.opacity(0, t);
            for (var sprite : text) sprite.opacity(0, t);
            return;
        }
        // Entrance: the portrait rises in from the lower right, the name slides in from the right.
        double in = easeOut(entrance) * box;
        float slide = (float) (1 - easeOut(entrance));
        var head = head(overlay, line.portrait());
        var glyph = head == null ? DialogueAssets.portrait(line.portrait()) : java.util.Optional.<DialogueAssets.Glyph>empty();
        if (head != null) {
            portrait.text(head)
                    .place(HEAD_RIGHT - HEAD_SIZE + slide * 80, HEAD_BOTTOM - HEAD_SIZE + slide * 40, HEAD_SIZE, HEAD_SIZE, 70, HEAD_GLYPH, 1, t)
                    .opacity(alpha(in), t);
        } else if (glyph.isPresent()) {
            var g = glyph.get();
            float width = PORTRAIT_HEIGHT * g.aspect();
            portrait.text(g.component(0xFFFFFF))
                    .place(PORTRAIT_RIGHT - width + slide * 80, PORTRAIT_BOTTOM - PORTRAIT_HEIGHT + slide * 40, width, PORTRAIT_HEIGHT, 70, g.height(), g.aspect(), t)
                    .opacity(alpha(in), t);
        } else portrait.opacity(0, t);
        boolean hasPortrait = head != null || glyph.isPresent();
        float shift = hasPortrait ? 0 : 290;
        float nameRight = hasPortrait ? NAME_RIGHT : PORTRAIT_RIGHT - 24;
        float nameWidth = speaker.width();
        name.text(speaker.component())
                .at(nameRight - NAME_PX * (1 + nameWidth / 2f) + slide * 40, NAME_BASELINE + 2 * NAME_PX, 60, NAME_PX, t)
                .opacity(alpha(in), t);
        curve.text(DialogueAssets.curve(frame / 2).component(style.lineColor()))
                .place(CURVE_LEFT + shift, CURVE_TOP, nameRight + (CURVE_LEFT + CURVE_WIDTH - NAME_RIGHT) - CURVE_LEFT - shift, CURVE_HEIGHT, 40, DialogueAssets.curve(0).height(), DialogueAssets.curve(0).aspect(), t)
                .opacity(style.showLine() ? alpha(box * 0.92) : 0, t);
        var rows = body();
        for (int i = 0; i < MAX_LINES; i++) {
            // Each row is its own sprite (spacer line + text line) so the line spacing is free of the 10 px font line.
            boolean used = i < rows.size();
            text[i].text(used ? Component.empty().append(DialogueAssets.SPACER.component()).append("\n").append(rows.get(i)) : Component.empty())
                    .at(TEXT_LEFT + TEXT_PX * (DialogueAssets.SPACER_ADVANCE / 2f - 1) + shift,
                            TEXT_TOP + i * LINE_PITCH + TEXT_PX * 9, 50, TEXT_PX, t)
                    .opacity(used ? alpha(box) : 0, t);
        }
    }

    /** One component per wrapped row: revealed characters fading from the highlight colour to their own, then the continue mark. */
    List<Component> body() {
        var rows = new ArrayList<Component>();
        int shown = (int) Math.floor(revealed), index = 0;
        int highlight = style.highlightColor(), settled = style.textColor();
        for (var row : lines) {
            int first = index, visible = Math.clamp(shown - index, 0, row.length());
            rows.add(row.component(0, visible, i -> {
                var s = row.style(i);
                int own = s.getColor() != null ? s.getColor().getValue() : settled;
                double fresh = Math.clamp((head - (first + i)) / FADE_CHARS, 0, 1);
                return s.withColor(fresh >= 1 ? own : mix(highlight, own, fresh));
            }));
            index += row.length();
        }
        if (revealed() && !rows.isEmpty()) {
            boolean on = (frame / 8) % 2 == 0;
            var last = (MutableComponent) rows.getLast();
            last.append(Component.literal(" ").withStyle(DialogueFonts.textStyle()));
            last.append(DialogueAssets.INDICATOR.component(on ? 0xFFFFFF : 0x8A8580));
        }
        return rows;
    }

    /** Portrait IDs {@code head:<player name|uuid|skin:id>} and {@code head:@viewer} draw that player's face (no pack needed). */
    public static final String HEAD = "head:", VIEWER = "@viewer";
    static final float HEAD_SIZE = 230, HEAD_RIGHT = 1880, HEAD_BOTTOM = 1010;
    static final int HEAD_GLYPH = 8;
    private static Component head(ScreenOverlay overlay, String portrait) {
        if (portrait == null || !portrait.startsWith(HEAD)) return null;
        var who = portrait.substring(HEAD.length());
        var player = overlay.view().player();
        var profile = who.equals(VIEWER) ? net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile())
                : kim.biryeong.minigamelib.cinematic.Skins.profile(player.level().getServer(), who);
        return Component.object(new net.minecraft.network.chat.contents.objects.PlayerSprite(profile, true));
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
