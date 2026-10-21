package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerChunkSender.class)
public abstract class ViewChunkSenderMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "sendNextChunks", at = @At("HEAD"), cancellable = true)
    private void minigamelib$holdBodyChunks(ServerPlayer player, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (CameraViews.get(player) != null && !CameraViews.sending()) ci.cancel();
    }
    @Redirect(method = "sendNextChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel minigamelib$level(ServerPlayer player) {
        var view = CameraViews.get(player);
        return CameraViews.sending() && view != null ? view.level() : player.level();
    }

    @Redirect(method = "sendNextChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos minigamelib$position(ServerPlayer player) {
        var view = CameraViews.get(player);
        return CameraViews.sending() && view != null ? ChunkPos.containing(BlockPos.containing(view.position())) : player.chunkPosition();
    }
}
