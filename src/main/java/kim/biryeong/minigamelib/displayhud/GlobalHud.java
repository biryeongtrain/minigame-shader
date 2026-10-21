package kim.biryeong.minigamelib.displayhud;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One HUD entity shown to many players (DisplayHud 2.0). Changes made through {@link #getHud()} are sent to every
 * player the HUD is shown to.
 */
public final class GlobalHud<T extends DisplayHud> {
    private final String id;
    private final T hud;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private boolean removed;

    public GlobalHud(String id, T hud) {
        this(id, hud, UUID.randomUUID());
    }

    public GlobalHud(String id, T hud, UUID uuid) {
        this.id = Objects.requireNonNull(id, "id");
        this.hud = Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(uuid, "uuid");
        if (!HudRegistry.registerGlobalHud(id, this)) {
            throw new IllegalArgumentException("GlobalHud id already exists: " + id);
        }
        if (!hud.attachGlobal(this, id, uuid)) {
            HudRegistry.unregisterGlobalHud(id, this);
            throw new IllegalStateException("Hud is already spawned or attached");
        }
    }

    public static GlobalHud<TextDisplayHud> text(String id) {
        return new GlobalHud<>(id, new TextDisplayHud());
    }

    public static GlobalHud<ItemDisplayHud> item(String id) {
        return new GlobalHud<>(id, new ItemDisplayHud());
    }

    public static GlobalHud<BlockDisplayHud> block(String id) {
        return new GlobalHud<>(id, new BlockDisplayHud());
    }

    public static <T extends DisplayHud> GlobalHud<T> create(String id, T hud) {
        return new GlobalHud<>(id, hud);
    }

    public static GlobalHud<? extends DisplayHud> getHud(String id) {
        return HudRegistry.getGlobalHud(id);
    }

    public static Map<String, GlobalHud<? extends DisplayHud>> getHuds() {
        return new LinkedHashMap<>(HudRegistry.getGlobalHuds());
    }

    public static void removeHud(String id) {
        GlobalHud<? extends DisplayHud> hud = getHud(id);
        if (hud != null) {
            hud.remove();
        }
    }

    public static void hideAll(ServerPlayer player) {
        HudRegistry.hideGlobalHuds(player);
    }

    public boolean show(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (removed || !players.add(player.getUUID())) {
            return false;
        }
        if (!hud.showTo(player)) {
            players.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public int show(ServerPlayer... players) {
        return show(List.of(players));
    }

    public int show(Iterable<? extends ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            if (show(player)) changed++;
        }
        return changed;
    }

    public boolean hide(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!players.remove(player.getUUID())) {
            return false;
        }
        hud.hideFrom(player);
        return true;
    }

    public int hide(ServerPlayer... players) {
        return hide(List.of(players));
    }

    public int hide(Iterable<? extends ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            if (hide(player)) changed++;
        }
        return changed;
    }

    /** Forgets a disconnected player without sending packets. */
    void forget(UUID player) {
        players.remove(player);
        hud.forgetViewer(player);
    }

    public void remove() {
        if (removed) return;
        removed = true;
        for (ServerPlayer player : getPlayers()) {
            hide(player);
        }
        players.clear();
        hud.removeGlobal();
        HudRegistry.unregisterGlobalHud(id, this);
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isShown(ServerPlayer player) {
        return players.contains(player.getUUID());
    }

    boolean isShown(UUID player) {
        return players.contains(player);
    }

    public String getId() {
        return id;
    }

    public T getHud() {
        return hud;
    }

    public Collection<ServerPlayer> getPlayers() {
        return DisplayHudManager.resolve(players);
    }
}
