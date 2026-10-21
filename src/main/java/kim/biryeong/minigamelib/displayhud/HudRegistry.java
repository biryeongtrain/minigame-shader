package kim.biryeong.minigamelib.displayhud;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HudRegistry {
    private static final Map<UUID, Map<String, DisplayHud>> PERSONAL = new ConcurrentHashMap<>();
    private static final Map<String, GlobalHud<? extends DisplayHud>> GLOBAL = new ConcurrentHashMap<>();

    private HudRegistry() {
    }

    static DisplayHud getPersonalHud(UUID player, String id) {
        Objects.requireNonNull(id, "id");
        synchronized (PERSONAL) {
            Map<String, DisplayHud> huds = PERSONAL.get(player);
            return huds == null ? null : huds.get(id);
        }
    }

    static Map<String, DisplayHud> getPersonalHuds(UUID player) {
        synchronized (PERSONAL) {
            Map<String, DisplayHud> huds = PERSONAL.get(player);
            return huds == null || huds.isEmpty() ? Collections.emptyMap() : new LinkedHashMap<>(huds);
        }
    }

    static boolean registerPersonalHud(UUID player, String id, DisplayHud hud) {
        synchronized (PERSONAL) {
            Map<String, DisplayHud> huds = PERSONAL.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
            if (huds.containsKey(id)) {
                return false;
            }
            huds.put(id, hud);
            return true;
        }
    }

    static void unregisterPersonalHud(UUID player, String id, DisplayHud hud) {
        synchronized (PERSONAL) {
            Map<String, DisplayHud> huds = PERSONAL.get(player);
            if (huds == null) return;
            if (huds.get(id) == hud) {
                huds.remove(id);
            }
            if (huds.isEmpty()) {
                PERSONAL.remove(player);
            }
        }
    }

    static void clearPersonalHuds(UUID player) {
        List<DisplayHud> huds;
        synchronized (PERSONAL) {
            Map<String, DisplayHud> removed = PERSONAL.remove(player);
            if (removed == null || removed.isEmpty()) return;
            huds = new ArrayList<>(removed.values());
        }
        for (DisplayHud hud : huds) {
            hud.remove();
        }
    }

    static boolean registerGlobalHud(String id, GlobalHud<? extends DisplayHud> hud) {
        return GLOBAL.putIfAbsent(id, hud) == null;
    }

    static void unregisterGlobalHud(String id, GlobalHud<? extends DisplayHud> hud) {
        GLOBAL.remove(id, hud);
    }

    static GlobalHud<? extends DisplayHud> getGlobalHud(String id) {
        return GLOBAL.get(Objects.requireNonNull(id, "id"));
    }

    static Map<String, GlobalHud<? extends DisplayHud>> getGlobalHuds() {
        return new LinkedHashMap<>(GLOBAL);
    }

    static Collection<DisplayHud> getVisibleHuds(UUID player) {
        List<DisplayHud> huds = new ArrayList<>(getPersonalHuds(player).values());
        for (GlobalHud<? extends DisplayHud> global : GLOBAL.values()) {
            if (global.isShown(player)) {
                huds.add(global.getHud());
            }
        }
        return huds;
    }

    static int[] getVisibleHudIds(UUID player) {
        Collection<DisplayHud> huds = getVisibleHuds(player);
        Integer trigger = AboveHandTrigger.triggerId(player);
        int[] ids = new int[huds.size() + (trigger == null ? 0 : 1)];
        int i = 0;
        for (DisplayHud hud : huds) {
            ids[i++] = hud.getEntityId();
        }
        if (trigger != null) {
            ids[i] = trigger;
        }
        return ids;
    }

    static boolean hasVisibleHuds(UUID player) {
        Map<String, DisplayHud> personal = PERSONAL.get(player);
        if (personal != null && !personal.isEmpty()) return true;
        for (GlobalHud<? extends DisplayHud> global : GLOBAL.values()) {
            if (global.isShown(player)) return true;
        }
        return AboveHandTrigger.triggerId(player) != null;
    }

    static void hideGlobalHuds(ServerPlayer player) {
        for (GlobalHud<? extends DisplayHud> global : GLOBAL.values()) {
            global.hide(player);
        }
    }

    /** Disconnect: the client entities are already gone, only forget the player. */
    static void forget(UUID player) {
        List<DisplayHud> huds;
        synchronized (PERSONAL) {
            Map<String, DisplayHud> removed = PERSONAL.remove(player);
            huds = removed == null ? List.of() : new ArrayList<>(removed.values());
        }
        for (DisplayHud hud : huds) {
            hud.forgetViewer(player);
            hud.remove();
        }
        for (GlobalHud<? extends DisplayHud> global : GLOBAL.values()) {
            global.forget(player);
        }
        AboveHandTrigger.forget(player);
    }

    static void clearAll() {
        PERSONAL.clear();
        GLOBAL.clear();
        AboveHandTrigger.clear();
    }
}
