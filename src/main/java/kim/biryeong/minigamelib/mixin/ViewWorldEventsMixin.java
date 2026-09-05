package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ViewWorldEventsMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I", at = @At("RETURN"), cancellable = true)
    private <T extends ParticleOptions> void minigamelib$particles(T particle, boolean force, boolean always, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed, CallbackInfoReturnable<Integer> cir) {
        int extra = 0;
        for (var view : CameraViews.all()) {
            if (view.level() == (Object) this && view.player().level() != (Object) this
                    && view.position().distanceToSqr(x, y, z) < (force ? 512.0 * 512 : 32.0 * 32)) {
                view.send(new ClientboundLevelParticlesPacket(particle, force, always, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count));
                extra++;
            }
        }
        cir.setReturnValue(cir.getReturnValue() + extra);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z", at = @At("HEAD"), cancellable = true)
    private void minigamelib$targetParticle(ServerPlayer player, boolean force, double x, double y, double z, Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
        var view = CameraViews.get(player);
        if (view != null) {
            boolean visible = view.level() == (Object) this && view.position().distanceToSqr(new Vec3(x, y, z)) < (force ? 512.0 * 512 : 32.0 * 32);
            if (visible) view.send(packet);
            cir.setReturnValue(visible);
        }
    }
}
