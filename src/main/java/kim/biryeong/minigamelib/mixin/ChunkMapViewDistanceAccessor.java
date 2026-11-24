package kim.biryeong.minigamelib.mixin;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual native chunk-send cap; worlds can override the global PlayerList setting. */
@Mixin(ChunkMap.class)
public interface ChunkMapViewDistanceAccessor {
    @Accessor("serverViewDistance") int minigamelib$serverViewDistance();
}
