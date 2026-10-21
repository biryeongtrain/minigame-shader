package kim.biryeong.minigamelib.displayhud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;

public class TextDisplayHud extends DisplayHud {
    private final Display.TextDisplay textDisplay;
    private Display.TextDisplay.Align textAlignment = Display.TextDisplay.Align.CENTER;
    private Component rawText = Component.empty();

    public TextDisplayHud() {
        this(new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, DisplayHudManager.level()));
    }

    private TextDisplayHud(Display.TextDisplay textDisplay) {
        super(textDisplay);
        this.textDisplay = textDisplay;
        // 26.3 draws the text background with the text shader, so the vanilla default (0x40000000)
        // would show up as a dark panel behind every HUD. Off by default; see setBackgroundColor.
        textDisplay.setBackgroundColor(0);
        textDisplay.setText(withSpacer(rawText));
    }

    public Display.TextDisplay getTextDisplay() {
        return textDisplay;
    }

    public void setText(String text) {
        setText(Component.literal(text));
    }

    public void setText(Component component) {
        rawText = component;
        textDisplay.setText(withSpacer(component));
        setLocation(location);
        changed();
    }

    public String getText() {
        return rawText.getString();
    }

    public Component getTextComponent() {
        return rawText;
    }

    public void setShadowToggle(boolean toggle) {
        setFlag(Display.TextDisplay.FLAG_SHADOW, toggle);
    }

    public void setTextAlignment(Display.TextDisplay.Align alignment) {
        byte flags = textDisplay.getFlags();
        flags &= (byte) ~(Display.TextDisplay.FLAG_ALIGN_LEFT | Display.TextDisplay.FLAG_ALIGN_RIGHT);
        switch (alignment) {
            case LEFT -> flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
            case RIGHT -> flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
            default -> { }
        }
        textDisplay.setFlags(flags);
        textAlignment = alignment;
        setLocation(location);
        changed();
    }

    public Display.TextDisplay.Align getTextAlignment() {
        return textAlignment;
    }

    /** Background colour (ARGB). 0 = none (default). Visible on 26.3+ only. */
    public void setBackgroundColor(int argb) {
        textDisplay.setBackgroundColor(argb);
        changed();
    }

    public int getBackgroundColor() {
        return textDisplay.getBackgroundColor();
    }

    public void setLineWidth(int width) {
        textDisplay.setLineWidth(width);
        textDisplay.setText(withSpacer(rawText));
        setLocation(location);
        changed();
    }

    public int getLineWidth() {
        return textDisplay.getLineWidth();
    }

    public void setOpacity(int opacity) {
        setOpacity(opacity, 0);
    }

    /** Text opacity 0..255, interpolated over {@code time} ticks (DisplayHud 1.7). */
    public void setOpacity(int opacity, int time) {
        int value = Math.clamp(opacity, -128, 255);
        if (value > 127) value -= 256;
        textDisplay.setTextOpacity((byte) value);
        textDisplay.setTransformationInterpolationDelay(0);
        textDisplay.setTransformationInterpolationDuration(time);
        changed();
    }

    @Override
    public void setLeftRotation(Vector3f vector, int time) {
        super.setLeftRotation(new Vector3f(vector).add(0, 180, 0), time);
    }

    @Override
    public Vector3f getLocationVector() {
        Vector3f scale = new Vector3f(this.scale);
        double offsetX = 0;
        double offsetY = -scale.y / 2;
        offsetY += (scale.y / 4.0) * (countLines(getText()) - 1.5);
        int lineWidth = textDisplay.getLineWidth();
        switch (textAlignment) {
            case LEFT -> offsetX = scale.x * ((lineWidth * 0.0125) - 0.5);
            case RIGHT -> offsetX = scale.x * ((lineWidth * -0.0125) - 0.55);
            default -> scale.x = 0;
        }
        offsetX += scale.x / 2;
        offsetY += scale.y / 2;
        return screenTranslation(offsetX, offsetY, 0);
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = textDisplay.getFlags();
        textDisplay.setFlags((byte) (on ? (flags | flag) : (flags & ~flag)));
        changed();
    }

    /** The first line is a blank spacer of {@code lineWidth} spaces so every HUD keeps a constant width. */
    private Component withSpacer(Component component) {
        return Component.literal(" ".repeat(Math.max(0, textDisplay.getLineWidth())) + "\n").append(component);
    }

    private static int countLines(String text) {
        return text == null || text.isEmpty() ? 1 : text.split("\n").length;
    }
}
