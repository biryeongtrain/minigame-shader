package kim.biryeong.minigamelib.mixin;

import io.netty.channel.ChannelFutureListener;
import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ViewConnectionMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void minigamelib$forgetPack(CallbackInfo ci) {
        kim.biryeong.minigamelib.music.MusicPacks.forget(((ServerCommonPacketListenerImpl)(Object)this).getOwner().id());
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void minigamelib$route(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        var common = (ServerCommonPacketListenerImpl)(Object)this;
        if (packet instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket push) {
            var sent = kim.biryeong.minigamelib.music.MusicPacks.sent(common,push);
            if (sent != push) { common.send(sent,listener); ci.cancel(); return; }
        }
        if (packet instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket pop) {
            var sent = kim.biryeong.minigamelib.music.MusicPacks.mapPop(common,pop);
            if (sent != pop) { common.send(sent,listener); ci.cancel(); return; }
            kim.biryeong.minigamelib.music.MusicPacks.popped(common, pop.id());
        }
        if ((Object) this instanceof ServerGamePacketListenerImpl handler) {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket)
                kim.biryeong.minigamelib.MinigameLib.get(handler.player.level().getServer()).music().interrupted(handler.player);
        }
        if ((Object) this instanceof ServerGamePacketListenerImpl handler) {
            if (!CameraViews.allow(handler.player, packet)) ci.cancel();
            else if (packet instanceof net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket) CameraViews.batchSent(handler.player);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "handleResourcePackResponse", at = @At("HEAD"), argsOnly = true, order = 900)
    private net.minecraft.network.protocol.common.ServerboundResourcePackPacket minigamelib$packResponse(net.minecraft.network.protocol.common.ServerboundResourcePackPacket packet) {
        return kim.biryeong.minigamelib.music.MusicPacks.response((ServerCommonPacketListenerImpl)(Object)this,packet);
    }

    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void minigamelib$dialog(net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl handler && packet.id().getNamespace().equals("minigamelib") && packet.id().getPath().startsWith("editor/")) {
            handler.player.level().getServer().execute(() -> {
                if (packet.payload().orElse(null) instanceof net.minecraft.nbt.CompoundTag tag)
                    kim.biryeong.minigamelib.MinigameLib.get(handler.player.level().getServer()).editor().response(handler.player, packet.id(), tag);
            });
            ci.cancel();
        }
    }
}
