package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ViewInputMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void minigamelib$holdPhysics(CallbackInfo ci) {
        if (CameraViews.get(player) != null) {
            clientIsFloating = false; aboveGroundTickCount = 0;
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void minigamelib$beforeConnectionTeleport(CallbackInfo ci) { CameraViews.stop(player); }

    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handlePlayerInput", "handlePlayerAction",
            "handleUseItemOn", "handleUseItem", "handleInteract", "handlePlayerCommand", "handleSetCarriedItem",
            "handleContainerClick", "handleContainerButtonClick", "handleSetCreativeModeSlot", "handlePlayerAbilities",
            "handleTeleportToEntityPacket", "handleAcceptTeleportPacket", "handleAcceptPlayerLoad",
            "handlePickItemFromBlock", "handlePickItemFromEntity", "handlePlaceRecipe", "handleSelectTrade", "handleRenameItem", "handleEditBook"}, at = @At("HEAD"), cancellable = true)
    private void minigamelib$freezeInput(CallbackInfo ci) {
        if (CameraViews.get(player) != null) ci.cancel();
    }

    @Inject(method = "handleChunkBatchReceived", at = @At("HEAD"), cancellable = true)
    private void minigamelib$chunkAck(ServerboundChunkBatchReceivedPacket packet, CallbackInfo ci) {
        player.level().getServer().execute(() -> CameraViews.batchReceived(player, packet.desiredChunksPerTick()));
        ci.cancel();
    }
}
