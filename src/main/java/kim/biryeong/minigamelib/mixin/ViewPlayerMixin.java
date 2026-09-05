package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ViewPlayerMixin {
    @Inject(method = {"setGameMode", "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"}, at = @At("HEAD"))
    private void minigamelib$beforeChange(CallbackInfoReturnable<Boolean> cir) { CameraViews.stop((ServerPlayer) (Object) this); }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
    private void minigamelib$beforeDimension(CallbackInfoReturnable<ServerPlayer> cir) { CameraViews.stop((ServerPlayer) (Object) this); }

    @Inject(method = {"die", "teleportTo(DDD)V"}, at = @At("HEAD"))
    private void minigamelib$beforeDeathOrTeleport(CallbackInfo ci) { CameraViews.stop((ServerPlayer) (Object) this); }
}
