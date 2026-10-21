package kim.biryeong.minigamelib.displayhud;

import kim.biryeong.minigamelib.MinigameLibInitializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Above-hand trigger (DisplayHud 2.2). The above-hand pixels are lifted by the {@code entity_outline} post effect,
 * which the client only runs on frames with a glow outline. While a player sees an above-hand HUD, one glowing,
 * fully transparent item display rides that player. It is not listed by {@code getHuds}/{@code getVisibleHuds}.
 */
final class AboveHandTrigger {
    static final Identifier ITEM_MODEL = Identifier.fromNamespaceAndPath(MinigameLibInitializer.MODID, "above_hand_trigger");
    private static final Map<UUID, ItemDisplayHud> TRIGGERS = new ConcurrentHashMap<>();

    private AboveHandTrigger() {
    }

    static synchronized void refresh(ServerPlayer viewer) {
        if (viewer.hasDisconnected()) {
            forget(viewer.getUUID());
            return;
        }
        boolean needed = false;
        for (DisplayHud hud : HudRegistry.getVisibleHuds(viewer.getUUID())) {
            if (hud.isAboveHand() && hud.isShownTo(viewer)) {
                needed = true;
                break;
            }
        }
        UUID key = viewer.getUUID();
        ItemDisplayHud current = TRIGGERS.get(key);
        if (needed && current == null) {
            ItemDisplayHud trigger = create();
            // Register before showing so the mount packet already contains the trigger id.
            TRIGGERS.put(key, trigger);
            trigger.showTo(viewer);
        } else if (!needed && current != null) {
            TRIGGERS.remove(key);
            current.hideFrom(viewer);
        }
    }

    static void respawn(ServerPlayer viewer) {
        ItemDisplayHud trigger = TRIGGERS.get(viewer.getUUID());
        if (trigger != null) {
            trigger.respawnTo(viewer);
        }
    }

    static void teleport(ServerPlayer viewer) {
        ItemDisplayHud trigger = TRIGGERS.get(viewer.getUUID());
        if (trigger != null) {
            trigger.teleportTo(viewer);
        }
    }

    static void forget(UUID player) {
        TRIGGERS.remove(player);
    }

    static void clear() {
        TRIGGERS.clear();
    }

    static Integer triggerId(UUID viewer) {
        ItemDisplayHud trigger = TRIGGERS.get(viewer);
        return trigger == null ? null : trigger.getEntityId();
    }

    private static ItemDisplayHud create() {
        ItemDisplayHud trigger = new ItemDisplayHud();
        trigger.aboveHandTrigger = true;
        trigger.updateWhenDataChanged(false); // showTo sends the full data once
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, ITEM_MODEL);
        trigger.setItem(stack);
        // 1px at the top-left corner of the screen; the texture is fully transparent.
        trigger.setScale(1f, 1f, 1f);
        trigger.setLocation(1f, 1f, 0f);
        trigger.setGlowColorOverride(DisplayHud.ABOVE_HAND_GLOW_COLOR);
        trigger.setGlowing(true);
        return trigger;
    }
}
