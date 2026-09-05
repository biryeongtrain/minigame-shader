package kim.biryeong.minigamelib.mixin;

import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;

@Mixin(ChunkHolder.class)
public abstract class ViewChunkUpdatesMixin {
    @Unique private ServerLevel minigamelib$world;
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void minigamelib$source(LevelChunk chunk, CallbackInfo ci) { minigamelib$world = (ServerLevel) chunk.getLevel(); }

    @Redirect(method = "broadcastChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;"))
    private List<ServerPlayer> minigamelib$viewers(ChunkHolder.PlayerProvider provider, ChunkPos pos, boolean border) {
        var players = new ArrayList<>(provider.getPlayers(pos, border));
        for (var view : CameraViews.all())
            if (view.level() == minigamelib$world && view.tracks(pos) && !players.contains(view.player())) players.add(view.player());
        return players;
    }

    @Inject(method = "broadcast", at = @At("HEAD"))
    private void minigamelib$broadcast(List<ServerPlayer> players, Packet<?> packet, CallbackInfo ci) {
        for (var player : players) {
            var view = CameraViews.get(player);
            if (view != null && view.level() == minigamelib$world) view.send(packet);
        }
    }
}
