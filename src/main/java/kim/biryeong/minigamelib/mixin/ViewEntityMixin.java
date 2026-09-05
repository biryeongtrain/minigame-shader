package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ViewEntityMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void minigamelib$freezeBody(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p && CameraViews.get(p) != null) {
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            ci.cancel();
        }
    }
}
