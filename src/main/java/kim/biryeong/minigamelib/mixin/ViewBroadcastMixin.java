package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class ViewBroadcastMixin {
    @Inject(method = "respawn", at = @At("HEAD"))
    private void minigamelib$beforeRespawn(net.minecraft.server.level.ServerPlayer player, boolean alive, net.minecraft.world.entity.Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.server.level.ServerPlayer> cir) {
        var lib = kim.biryeong.minigamelib.MinigameLib.get(player.getServer());
        lib.editor().close(player); lib.cinematics().stop(player); lib.music().stop(java.util.List.of(player));
    }

    @Inject(method = "broadcast", at = @At("HEAD"))
    private void minigamelib$worldEvent(Player excluded, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        for (var view : CameraViews.all())
            if (view.player() != excluded && view.level().dimension().equals(dimension)
                    && view.position().distanceToSqr(x, y, z) < radius * radius) view.send(packet);
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
    private void minigamelib$dimensionEvent(Packet<?> packet, ResourceKey<Level> dimension, CallbackInfo ci) {
        for (var view : CameraViews.all())
            if (view.level().dimension().equals(dimension) && !CameraViews.allow(view.player(), packet)) view.send(packet);
    }
}
