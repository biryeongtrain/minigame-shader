package kim.biryeong.minigamelib.music;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.minigamelib.MinigameLib;
import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicPacks {
    public record Published(MusicCatalog catalog, String hash) {}
    private static final java.util.concurrent.atomic.AtomicReference<MusicCatalog> BUILD = new java.util.concurrent.atomic.AtomicReference<>();
    private static final Map<UUID, PackReadiness> LISTENERS = new ConcurrentHashMap<>();
    private static volatile Published published = new Published(MusicCatalog.empty(), "");
    private MusicPacks() {}
    private static volatile Path musicDirectory;
    public static void init(Path directory) {
        musicDirectory = directory;
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            try {
                var scan = MusicCatalog.scan(directory, s -> org.slf4j.LoggerFactory.getLogger("minigamelib").warn("Music: {}", s));
                for (var asset : scan.assets().entrySet())
                    if (!builder.addData(asset.getKey(), asset.getValue())) throw new IllegalStateException("Cannot add " + asset.getKey());
                BUILD.set(scan.catalog());
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot scan music directory", e); }
        });
    }
    public static Published published() { return published; }
    /**
     * A reused pack skips the build, so {@link #finish} never publishes it: without this {@link #ready} stays false for
     * everyone (no music, and activities that wait on pack readiness never start). The catalog is rescanned from the same
     * directory the cached pack was keyed on.
     */
    public static void restore(String sha1) throws java.io.IOException {
        var directory = musicDirectory;
        if (directory == null || sha1 == null || sha1.isEmpty()) return;
        var scan = MusicCatalog.scan(directory, s -> org.slf4j.LoggerFactory.getLogger("minigamelib").warn("Music: {}", s));
        published = new Published(scan.catalog(), sha1.toLowerCase(java.util.Locale.ROOT));
    }
    public static void begin() { BUILD.set(null); }
    public static void finish(Path output, boolean success) {
        var candidate = BUILD.getAndSet(null);
        if (!success || candidate == null) return;
        try (var stream = Files.newInputStream(output)) {
            var digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536]; int size;
            while ((size = stream.read(buffer)) >= 0) digest.update(buffer, 0, size);
            published = new Published(candidate, HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger("minigamelib").error("Cannot verify generated music pack", e); }
    }
    public static ClientboundResourcePackPushPacket sent(ServerCommonPacketListenerImpl handler, ClientboundResourcePackPushPacket packet) {
        if (!packet.id().equals(PolymerResourcePackUtils.getMainUuid())) return packet;
        var previous = LISTENERS.get(handler.getOwner().id());
        if (previous != null) handler.send(new ClientboundResourcePackPopPacket(Optional.of(previous.id())));
        // Polymer reuses one UUID. A fresh wire UUID prevents an old load ACK from approving a newer build.
        var sent = new ClientboundResourcePackPushPacket(UUID.randomUUID(),packet.url(),packet.hash(),packet.required(),packet.prompt());
        LISTENERS.put(handler.getOwner().id(), new PackReadiness(sent.id(), sent.hash(), false));
        if (handler instanceof ServerGamePacketListenerImpl game) interrupt(game.player);
        return sent;
    }
    public static ServerboundResourcePackPacket response(ServerCommonPacketListenerImpl handler, ServerboundResourcePackPacket packet) {
        var state = LISTENERS.get(handler.getOwner().id());
        if (state == null || !state.id().equals(packet.id())) return packet;
        LISTENERS.replace(handler.getOwner().id(),state,state.acknowledged(packet.id(),packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        // Keep Polymer's native pack tracking under its public main UUID.
        return new ServerboundResourcePackPacket(PolymerResourcePackUtils.getMainUuid(),packet.action());
    }
    public static ClientboundResourcePackPopPacket mapPop(ServerCommonPacketListenerImpl handler, ClientboundResourcePackPopPacket packet) {
        var state = LISTENERS.get(handler.getOwner().id());
        return state != null && packet.id().filter(PolymerResourcePackUtils.getMainUuid()::equals).isPresent()
                ? new ClientboundResourcePackPopPacket(Optional.of(state.id())) : packet;
    }
    public static boolean ready(ServerPlayer player) {
        var state = LISTENERS.get(player.getUUID());
        return state != null && state.ready(published.hash);
    }
    private static void interrupt(ServerPlayer player) {
        player.level().getServer().execute(() -> { CameraViews.stop(player); MinigameLib.get(player.level().getServer()).music().interrupted(player); });
    }
    public static void forget(ServerPlayer player) { forget(player.getUUID()); }
    public static void forget(UUID player) { LISTENERS.remove(player); }
    public static void popped(ServerCommonPacketListenerImpl handler, Optional<UUID> id) {
        var state = LISTENERS.get(handler.getOwner().id());
        if (state != null && (id.isEmpty() || state.id().equals(id.get()))) {
            LISTENERS.remove(handler.getOwner().id());
            if (handler instanceof ServerGamePacketListenerImpl game) interrupt(game.player);
        }
    }
}
