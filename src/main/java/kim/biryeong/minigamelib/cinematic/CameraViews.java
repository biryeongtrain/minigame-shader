package kim.biryeong.minigamelib.cinematic;

import net.minecraft.commands.Commands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.*;
import static net.minecraft.commands.arguments.DimensionArgument.*;
import static net.minecraft.commands.arguments.coordinates.Vec3Argument.*;

public final class CameraViews {
    /** 26.x ServerEntity synchronizer that routes every packet to one consumer (the viewer). */
    public static net.minecraft.server.level.ServerEntity.Synchronizer synchronizer(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sink) {
        return new net.minecraft.server.level.ServerEntity.Synchronizer() {
            @Override public void sendToTrackingPlayers(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet) { sink.accept(packet); }
            @Override public void sendToTrackingPlayersAndSelf(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet) { sink.accept(packet); }
            @Override public void sendToTrackingPlayersFiltered(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet, java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> filter) { sink.accept(packet); }
        };
    }

    private static final Map<UUID, CameraViewSession> VIEWS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> SENDING = ThreadLocal.withInitial(() -> 0);
    private static final Map<Region, Integer> TICKETS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<net.minecraft.server.network.PlayerChunkSender>> BATCHES = new HashMap<>();
    private static final TicketType CAMERA_TICKET = new TicketType(0L, TicketType.FLAG_LOADING);
    // Only connection, UI and account state may bypass the cinematic world's packet route.
    private static final Set<String> ALLOWED = Set.of("keep_alive", "ping", "disconnect", "custom_payload",
            "system_chat", "player_chat", "disguised_chat", "delete_chat", "chat_suggestions",
            "player_info_update", "player_info_remove", "commands", "command_suggestions", "server_data",
            "resource_pack_push", "resource_pack_pop", "store_cookie", "cookie_request", "transfer",
            "boss_event", "set_title_text", "set_subtitle_text", "set_action_bar_text", "set_titles_animation",
            "clear_titles", "show_dialog", "clear_dialog", "update_advancements", "award_stats",
            "set_objective", "set_score", "reset_score", "set_display_objective", "set_player_team",
            "container_set_content", "container_set_slot", "container_set_data", "container_close",
            "set_player_inventory", "set_cursor_item", "recipe_book_add", "recipe_book_remove", "recipe_book_settings");

    private CameraViews() {}

    public static void init() {
        Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("minigamelib", "camera"), CAMERA_TICKET);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var view : List.copyOf(VIEWS.values())) {
                if (view.player().level().getServer() != server) continue;
                try { view.tick(); }
                catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger("minigamelib").error("Camera view failed", e);
                    view.close();
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { stop(handler.player); BATCHES.remove(handler.player.getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> List.copyOf(VIEWS.values()).stream()
                .filter(v -> v.player().level().getServer() == server).forEach(CameraViewSession::close));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p) || get(p) == null);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(literal("minigamelib")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(literal("cinematic")
                        .then(literal("view").then(argument("dimension", dimension())
                                .then(argument("position", vec3()).executes(c -> {
                                    var p = c.getSource().getPlayerOrException();
                                    open(p, getDimension(c, "dimension"), getVec3(c, "position"), p.getYRot(), p.getXRot());
                                    return 1;
                                }))))
                        .then(literal("stop").executes(c -> { stop(c.getSource().getPlayerOrException()); return 1; })))));
    }

    public static CameraViewSession get(ServerPlayer player) { return VIEWS.get(player.getUUID()); }
    public static Collection<CameraViewSession> all() { return List.copyOf(VIEWS.values()); }
    public static boolean sending() { return SENDING.get() != 0; }
    public static void batchSent(ServerPlayer player) {
        var view = get(player);
        var sender = sending() && view != null ? view.chunks() : player.connection.chunkSender;
        BATCHES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(sender);
    }
    public static void batchReceived(ServerPlayer player, float rate) {
        var batches = BATCHES.get(player.getUUID());
        var sender = batches == null ? null : batches.poll();
        if (batches != null && batches.isEmpty()) BATCHES.remove(player.getUUID());
        if (sender != null && ((kim.biryeong.minigamelib.mixin.ChunkSenderAccessor)sender).minigamelib$unacknowledged() > 0)
            sender.onChunkBatchReceivedByClient(Float.isFinite(rate) ? rate : 0.01f);
    }
    public static void sending(Runnable action) {
        SENDING.set(SENDING.get() + 1);
        try { action.run(); } finally { SENDING.set(SENDING.get() - 1); }
    }
    public static void send(ServerPlayer player, Packet<?> packet) { sending(() -> player.connection.send(packet)); }
    public static boolean allow(ServerPlayer player, Packet<?> packet) {
        if (sending() || get(player) == null) return true;
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var child : bundle.subPackets()) if (!allow(player, child)) return false;
            return true;
        }
        return ALLOWED.contains(packet.type().id().getPath());
    }

    public static CameraViewSession open(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        if (player.hasDisconnected() || player.isDeadOrDying() || player.isPassenger() || player.isSleeping())
            throw new IllegalStateException("Player must be connected, alive, awake and unmounted");
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !level.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException("Invalid camera position or rotation");
        stop(player);
        player.closeContainer();
        var view = new CameraViewSession(player);
        VIEWS.put(player.getUUID(), view);
        try { view.move(level, pos, yaw, pitch, true); }
        catch (RuntimeException e) { view.close(); throw e; }
        return view;
    }
    public static void stop(ServerPlayer player) { var view = get(player); if (view != null) view.close(); }
    static void remove(CameraViewSession view) { VIEWS.remove(view.player().getUUID(), view); }

    record Region(ServerLevel level, ChunkPos center, int radius) {}
    static void acquire(Region region) {
        if (TICKETS.merge(region, 1, Integer::sum) == 1)
            region.level.getChunkSource().addTicketWithRadius(CAMERA_TICKET, region.center, region.radius);
    }
    static void release(Region region) {
        if (region == null) return;
        Integer count = TICKETS.get(region);
        if (count == null) return;
        if (count > 1) TICKETS.put(region, count - 1);
        else {
            TICKETS.remove(region);
            region.level.getChunkSource().removeTicketWithRadius(CAMERA_TICKET, region.center, region.radius);
        }
    }
    public static int ticketCount() { return TICKETS.size(); }
}
