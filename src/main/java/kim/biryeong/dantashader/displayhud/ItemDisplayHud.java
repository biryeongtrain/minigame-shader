package kim.biryeong.dantashader.displayhud;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

public final class ItemDisplayHud extends DisplayHud {
    private final ItemDisplayElement itemDisplay;

    public ItemDisplayHud() {
        this(new ItemDisplayElement());
    }

    private ItemDisplayHud(ItemDisplayElement itemDisplay) {
        super(itemDisplay);
        this.itemDisplay = itemDisplay;
    }

    public ItemDisplayElement getItemDisplay() {
        return this.itemDisplay;
    }

    public void setItem(ItemStack itemStack) {
        this.itemDisplay.setItem(itemStack.copy());
        setLocation(this.location);
        update();
    }

    public ItemStack getItem() {
        return this.itemDisplay.getItem().copy();
    }

    public void setItemTransform(ItemDisplayContext transform) {
        this.itemDisplay.setItemDisplayContext(transform);
        update();
    }

    public void setItemTransform(String transform) {
        setItemTransform(ItemDisplayContext.valueOf(transform));
    }

    public ItemDisplayContext getItemTransform() {
        return this.itemDisplay.getItemDisplayContext();
    }

    @Override
    public Vector3f getLocationVector() {
        Vector3f scale = new Vector3f(this.scale);
        double offsetX = 0;
        double offsetY = 0;
        double offsetZ = this.getItem().getItem() instanceof BlockItem ? scale.z / 2 : scale.z / 32;

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
}
