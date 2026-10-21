package kim.biryeong.minigamelib.displayhud;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

public class ItemDisplayHud extends DisplayHud {
    private final Display.ItemDisplay itemDisplay;

    public ItemDisplayHud() {
        this(new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, DisplayHudManager.level()));
    }

    private ItemDisplayHud(Display.ItemDisplay itemDisplay) {
        super(itemDisplay);
        this.itemDisplay = itemDisplay;
    }

    public Display.ItemDisplay getItemDisplay() {
        return itemDisplay;
    }

    public void setItem(ItemStack stack) {
        itemDisplay.setItemStack(stack.copy());
        setLocation(location);
        changed();
    }

    public ItemStack getItem() {
        return itemDisplay.getItemStack().copy();
    }

    public void setItemTransform(ItemDisplayContext transform) {
        itemDisplay.setItemTransform(transform);
        changed();
    }

    public void setItemTransform(String transform) {
        setItemTransform(ItemDisplayContext.valueOf(transform));
    }

    public ItemDisplayContext getItemTransform() {
        return itemDisplay.getItemTransform();
    }

    @Override
    public Vector3f getLocationVector() {
        double offsetZ = itemDisplay.getItemStack().getItem() instanceof BlockItem ? scale.z / 2 : scale.z / 32;
        return screenTranslation(0, 0, offsetZ);
    }
}
