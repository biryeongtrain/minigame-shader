package kim.biryeong.dantashader.displayhud;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class BlockDisplayHud extends DisplayHud {
    private final BlockDisplayElement blockDisplay;

    public BlockDisplayHud() {
        this(new BlockDisplayElement());
    }

    private BlockDisplayHud(BlockDisplayElement blockDisplay) {
        super(blockDisplay);
        this.blockDisplay = blockDisplay;
    }

    public BlockDisplayElement getBlockDisplay() {
        return this.blockDisplay;
    }

    public void setBlock(BlockState blockState) {
        this.blockDisplay.setBlockState(blockState);
        update();
    }

    public BlockState getBlock() {
        return this.blockDisplay.getBlockState();
    }

    @Override
    public Vector3f getLocationVector() {
        Vector3f scale = new Vector3f(this.scale);
        double offsetX = 0;
        double offsetY = 0;
        double offsetZ = scale.z / 2;

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
