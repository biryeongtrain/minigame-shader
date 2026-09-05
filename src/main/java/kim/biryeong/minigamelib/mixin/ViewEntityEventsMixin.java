package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ViewEntityEventsMixin {
    @Inject(method = {"broadcast", "broadcastAndSend"}, at = @At("HEAD"))
    private void minigamelib$entityEvent(Entity entity, Packet<?> packet, CallbackInfo ci) {
        for (var view : CameraViews.all())
            if (view.level() == entity.level() && view.tracks(entity.chunkPosition())) view.send(packet);
    }
}
