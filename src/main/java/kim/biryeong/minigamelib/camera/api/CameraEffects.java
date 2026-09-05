package kim.biryeong.minigamelib.camera.api;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraEffects {
    private static final Map<UUID, Float> FOV_BY_PLAYER = new ConcurrentHashMap<>();
    private static boolean initialized;

    private CameraEffects() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) {
                return;
            }

            FOV_BY_PLAYER.forEach((uuid, fov) -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    sendFov(player, fov);
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FOV_BY_PLAYER.remove(handler.getPlayer().getUUID()));
    }

    public static void setFov(ServerPlayer player, float fov) {
        Objects.requireNonNull(player, "player");
        if (!Float.isFinite(fov) || fov < 1.0f || fov >= 180.0f) {
            throw new IllegalArgumentException("fov must be finite and in the range [1.0, 180.0)");
        }

        FOV_BY_PLAYER.put(player.getUUID(), fov);
        sendFov(player, fov);
    }

    public static void resetFov(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        FOV_BY_PLAYER.remove(player.getUUID());
        sync(player);
    }

    public static Float getFov(ServerPlayer player) { return FOV_BY_PLAYER.get(player.getUUID()); }

    public static void sync(ServerPlayer player) {
        var view = kim.biryeong.minigamelib.cinematic.CameraViews.get(player);
        var level = view == null ? player.level() : view.level();
        Float fov = getFov(player);
        long time = fov == null ? level.getGameTime() : -(long) Math.floor(((fov + 0.5f) / 180.0f) * 24_000.0f);
        kim.biryeong.minigamelib.cinematic.CameraViews.send(player, new ClientboundSetTimePacket(time, level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
    }

    private static void sendFov(ServerPlayer player, float fov) {
        sync(player);
    }
}
