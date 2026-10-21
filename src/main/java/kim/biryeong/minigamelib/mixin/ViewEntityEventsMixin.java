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
    @Inject(method = {"sendToTrackingPlayers", "sendToTrackingPlayersAndSelf"}, at = @At("HEAD"))
    private void minigamelib$entityEvent(Entity entity, Packet<?> packet, CallbackInfo ci) {
        forward(entity, packet);
    }

    @Inject(method = "sendToTrackingPlayersFiltered", at = @At("HEAD"))
    private void minigamelib$entityEventFiltered(Entity entity, Packet<?> packet, java.util.function.Predicate<?> filter, CallbackInfo ci) {
        forward(entity, packet);
    }

    private static void forward(Entity entity, Packet<?> packet) {
        for (var view : CameraViews.all())
            if (view.level() == entity.level() && view.tracks(entity.chunkPosition())) view.send(packet);
    }
}
