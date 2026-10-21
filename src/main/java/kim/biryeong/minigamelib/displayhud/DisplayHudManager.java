package kim.biryeong.minigamelib.displayhud;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * DisplayHud settings and Fabric event wiring. The unit/screen/alignment values must match
 * {@code assets/minecraft/shaders/include/displayhud.glsl} (DisplayHUD 2.x defaults).
 */
public final class DisplayHudManager {
    public static float unitX = 100f;
    public static float unitY = 100f;
    public static float screenX = 1920f;
    public static float screenY = 1080f;
    public static int alignmentGap = 1_000_000;

    private static final Queue<DelayedTask> DELAYED_TASKS = new ArrayDeque<>();
    private static MinecraftServer server;
    private static boolean initialized;

    private DisplayHudManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            HudRegistry.clearAll();
            DELAYED_TASKS.clear();
            server = null;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> HudRegistry.forget(handler.getPlayer().getUUID()));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            for (DisplayHud hud : DisplayHud.getVisibleHuds(player)) {
                if (!hud.isRemovedWhenPlayerDied()) continue;
                if (hud.isGlobalHud()) {
                    GlobalHud<?> global = hud.getGlobalOwner();
                    if (global != null) global.hide(player);
                } else {
                    hud.remove();
                }
            }
        });

        // Respawn and level changes recreate the client world, so every HUD has to be sent again.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                runLater(3, () -> resend(newPlayer.getUUID())));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                runLater(3, () -> resend(player.getUUID())));

        ServerTickEvents.END_SERVER_TICK.register(DisplayHudManager::tickDelayedTasks);
    }

    public static void configure(float unitX, float unitY, float screenX, float screenY, int alignmentGap) {
        DisplayHudManager.unitX = unitX;
        DisplayHudManager.unitY = unitY;
        DisplayHudManager.screenX = screenX;
        DisplayHudManager.screenY = screenY;
        DisplayHudManager.alignmentGap = alignmentGap;
    }

    /** Sends every visible HUD (and the above-hand trigger) to the player again. */
    public static void resend(ServerPlayer player) {
        for (DisplayHud hud : DisplayHud.getVisibleHuds(player)) {
            hud.respawnTo(player);
        }
        AboveHandTrigger.respawn(player);
        DisplayHud.mountVisibleHuds(player);
    }

    private static void resend(UUID uuid) {
        ServerPlayer player = player(uuid);
        if (player != null) resend(player);
    }

    public static MinecraftServer server() {
        return server;
    }

    /** Level used to construct the (never added) display entities. */
    static ServerLevel level() {
        if (server == null) {
            throw new IllegalStateException("DisplayHud can only be created while the server is running");
        }
        return server.overworld();
    }

    static ServerPlayer player(UUID uuid) {
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    static List<ServerPlayer> resolve(Collection<UUID> uuids) {
        List<ServerPlayer> players = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            ServerPlayer player = player(uuid);
            if (player != null && !player.hasDisconnected()) players.add(player);
        }
        return players;
    }

    public static void runLater(int ticks, Runnable task) {
        if (server == null) return;
        DELAYED_TASKS.add(new DelayedTask(server.getTickCount() + Math.max(0, ticks), task));
    }

    /** @deprecated use {@link #runLater(int, Runnable)} */
    @Deprecated
    public static void runLater(MinecraftServer ignored, int ticks, Runnable task) {
        runLater(ticks, task);
    }

    private static void tickDelayedTasks(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<DelayedTask> iterator = DELAYED_TASKS.iterator();
        List<Runnable> due = new ArrayList<>();
        while (iterator.hasNext()) {
            DelayedTask task = iterator.next();
            if (task.runAtTick <= now) {
                iterator.remove();
                due.add(task.task);
            }
        }
        due.forEach(Runnable::run);
    }

    private record DelayedTask(int runAtTick, Runnable task) {
    }
}
