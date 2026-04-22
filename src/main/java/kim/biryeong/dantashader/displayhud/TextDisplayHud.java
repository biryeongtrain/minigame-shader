package kim.biryeong.dantashader.displayhud;

import com.mojang.math.Transformation;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TextDisplayHud extends DisplayHud {
    private final TextDisplayElement textDisplay;
    private Display.TextDisplay.Align textAlignment = Display.TextDisplay.Align.CENTER;
    private Component rawText = Component.empty();

    public TextDisplayHud() {
        this(new TextDisplayElement());
    }

    private TextDisplayHud(TextDisplayElement textDisplay) {
        super(textDisplay);
        this.textDisplay = textDisplay;
    }

    public TextDisplayElement getTextDisplay() {
        return this.textDisplay;
    }

    public void setText(String text) {
        setText(Component.literal(text));
    }

    public void setText(Component component) {
        this.rawText = component;
        this.textDisplay.setText(withSpacer(component));
        setLocation(this.location);
        update();
    }

    public String getText() {
        return this.rawText.getString();
    }

    public Component getTextComponent() {
        return this.rawText;
    }

    public void setShadowToggle(boolean toggle) {
        this.textDisplay.setShadow(toggle);
        update();
    }

    public void setTextAlignment(Display.TextDisplay.Align textAlignment) {
        this.textAlignment = textAlignment;
        this.textDisplay.setTextAlignment(textAlignment);
        update();
    }

    public Display.TextDisplay.Align getTextAlignment() {
        return this.textAlignment;
    }

    public void setLineWidth(int width) {
        this.textDisplay.setLineWidth(width);
        this.textDisplay.setText(withSpacer(this.rawText));
        update();
    }

    public int getLineWidth() {
        return this.textDisplay.getLineWidth();
    }

    public void setOpacity(int opacity) {
        if (opacity < 0) {
            opacity = 0;
        }
        if (opacity > 255) {
            opacity = 255;
        }
        this.textDisplay.setTextOpacity((byte) (opacity - 256));
        update();
    }

    @Override
    public void setLeftRotation(Vector3f vector, Integer time) {
        Vector3f adjusted = new Vector3f(vector).add(0, 180, 0);
        Quaternionf quat = vecToQuat(adjusted);
        this.display.setLeftRotation(quat);
        this.display.setStartInterpolation(0);
        this.display.setInterpolationDuration(time);
        update();
    }

    @Override
    public Vector3f getLocationVector() {
        Vector3f scale = new Vector3f(this.scale);
        double offsetX = 0;
        double offsetY;
        double offsetZ = 0;

        String text = getText();
        offsetY = -scale.y / 2;
        int lines = countLinesNl(text);
        offsetY += (scale.y / 4.0) * (lines - 1.5);
        int lineWidth = this.textDisplay.getLineWidth();
        switch (this.textAlignment) {
            case LEFT -> offsetX = scale.x * ((lineWidth * 0.0125) - 0.5);
            case RIGHT -> offsetX = scale.x * ((lineWidth * -0.0125) - 0.55);
            case CENTER -> scale.x = 0;
        }
        offsetX += scale.x / 2;
        offsetY += scale.y / 2;

        double unitX = DisplayHudManager.unitX;
        double unitY = DisplayHudManager.unitY;
        double screenX = DisplayHudManager.screenX;
        double screenY = DisplayHudManager.screenY;
        int alignmentGap = DisplayHudManager.alignmentGap;
        double newX = ((screenX / 2) - this.location.x - offsetX) * unitX;
        double newY = -1.5 * alignmentGap - 0.178 + (((screenY / 2) - this.location.y - offsetY) * unitY);
        double newZ = (unitX * offsetZ) - (this.location.z * 100);
        return new Vector3f((float) newX, (float) newY, (float) newZ);
    }

    @Override
    public void setTransformation(Transformation transformation) {
        super.setTransformation(transformation);
    }

    private Component withSpacer(Component component) {
        return Component.literal(" ".repeat(Math.max(0, getLineWidth())) + "\n").append(component);
    }

    private static int countLinesNl(String text) {
        return text == null || text.isEmpty() ? 1 : text.split("\n").length;
    }
}
