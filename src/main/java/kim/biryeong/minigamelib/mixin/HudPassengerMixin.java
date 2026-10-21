package kim.biryeong.minigamelib.mixin;

import io.netty.channel.ChannelFutureListener;
import kim.biryeong.minigamelib.displayhud.HudPackets;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps DisplayHud entities mounted when vanilla re-sends the player's passenger list. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class HudPassengerMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> minigamelib$hudPassengers(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            return HudPackets.withHudPassengers(game.player, packet);
        }
        return packet;
    }
}
