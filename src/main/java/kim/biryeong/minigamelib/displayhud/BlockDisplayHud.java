package kim.biryeong.minigamelib.displayhud;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class BlockDisplayHud extends DisplayHud {
    private final Display.BlockDisplay blockDisplay;

    public BlockDisplayHud() {
        this(new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, DisplayHudManager.level()));
    }

    private BlockDisplayHud(Display.BlockDisplay blockDisplay) {
        super(blockDisplay);
        this.blockDisplay = blockDisplay;
    }

    public Display.BlockDisplay getBlockDisplay() {
        return blockDisplay;
    }

    public void setBlock(BlockState state) {
        blockDisplay.setBlockState(state);
        changed();
    }

    public BlockState getBlock() {
        return blockDisplay.getBlockState();
    }

    @Override
    public Vector3f getLocationVector() {
        return screenTranslation(0, 0, scale.z / 2);
    }
}
