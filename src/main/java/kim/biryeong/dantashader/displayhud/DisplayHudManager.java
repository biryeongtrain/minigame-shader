package kim.biryeong.dantashader.displayhud;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

public final class DisplayHudManager {
    public static float unitX = 1f;
    public static float unitY = 1f;
    public static float screenX = 1920f;
    public static float screenY = 1080f;
    public static int alignmentGap = 10_000;

    private static final Queue<DelayedTask> DELAYED_TASKS = new ArrayDeque<>();
    private static boolean initialized;

    private DisplayHudManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DisplayHud.clearHuds(handler.getPlayer()));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                for (DisplayHud hud : DisplayHud.getHuds(player).values()) {
                    if (hud.isRemovedWhenPlayerDied()) {
                        hud.remove();
                    }
                }
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                runLater(newPlayer.getServer(), 3, () -> DisplayHud.reattachAll(oldPlayer, newPlayer))
        );

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                runLater(player.getServer(), 3, () -> {
                    for (DisplayHud hud : DisplayHud.getHuds(player).values()) {
                        hud.respawn();
                    }
                })
        );

        ServerTickEvents.END_SERVER_TICK.register(DisplayHudManager::tickDelayedTasks);
    }

    public static void configure(float unitX, float unitY, float screenX, float screenY, int alignmentGap) {
        DisplayHudManager.unitX = unitX;
        DisplayHudManager.unitY = unitY;
        DisplayHudManager.screenX = screenX;
        DisplayHudManager.screenY = screenY;
        DisplayHudManager.alignmentGap = alignmentGap;
    }

    public static void runLater(MinecraftServer server, int ticks, Runnable task) {
        DELAYED_TASKS.add(new DelayedTask(server.getTickCount() + Math.max(0, ticks), task));
    }

    private static void tickDelayedTasks(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<DelayedTask> iterator = DELAYED_TASKS.iterator();
        while (iterator.hasNext()) {
            DelayedTask delayedTask = iterator.next();
            if (delayedTask.runAtTick <= now) {
                iterator.remove();
                delayedTask.task.run();
            }
        }
    }

    private record DelayedTask(int runAtTick, Runnable task) {
    }
}
