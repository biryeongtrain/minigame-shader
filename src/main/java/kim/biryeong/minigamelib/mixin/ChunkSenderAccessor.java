package kim.biryeong.minigamelib.mixin;

import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerChunkSender.class)
public interface ChunkSenderAccessor {
    @Accessor("unacknowledgedBatches") int minigamelib$unacknowledged();
}
