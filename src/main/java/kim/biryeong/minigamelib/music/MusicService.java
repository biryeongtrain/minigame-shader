package kim.biryeong.minigamelib.music;

import kim.biryeong.minigamelib.Playback;
import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import java.util.*;
import java.util.concurrent.*;

/** The clock only sends sound packets; world and entity state is never mutated by it. */
public final class MusicService implements AutoCloseable {
    private final Map<UUID, ServerPlayer> online = new LinkedHashMap<>();
    private final Map<UUID, Channel> owners = new HashMap<>();
    private final Set<UUID> cinematic = new HashSet<>();
    private final Set<Channel> channels = new LinkedHashSet<>();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "minigamelib-music"); t.setDaemon(true); return t;
    });
    private final boolean autoStart;
    private Channel global;
    private boolean closed, autoStarted;

    public MusicService(boolean autoStart) {
        this.autoStart = autoStart;
        clock.scheduleAtFixedRate(this::tick, 0, 10, TimeUnit.MILLISECONDS);
    }
    public MusicCatalog catalog() { return MusicPacks.published().catalog(); }
    public synchronized void joined(ServerPlayer player) { online.put(player.getUUID(), player); }
    public synchronized void left(ServerPlayer player) { stop(List.of(player)); online.remove(player.getUUID()); cinematic.remove(player.getUUID()); MusicPacks.forget(player); }
    public synchronized void reserve(ServerPlayer player, boolean reserve) {
        stop(List.of(player));
        if (reserve) cinematic.add(player.getUUID()); else cinematic.remove(player.getUUID());
    }
    public synchronized void interrupted(ServerPlayer player) {
        var channel = owners.remove(player.getUUID());
        if (channel != null) channel.silence(player);
    }
    public synchronized Playback play(String id, Collection<ServerPlayer> players) { return play(id, players, false); }
    public synchronized Playback play(String id, Collection<ServerPlayer> players, boolean scene) {
        if (closed) throw new IllegalStateException("Music service is closed");
        var source = catalog();
        var list = source.playlists().get(id);
        if (list == null) {
            if (!source.tracks().containsKey(id)) throw new IllegalArgumentException("Unknown music or playlist: " + id);
            list = new MusicCatalog.Playlist(List.of(id), false);
        }
        var targets = List.copyOf(new LinkedHashSet<>(players));
        if (targets.isEmpty()) throw new IllegalArgumentException("No listeners");
        for (var p : targets) if (!online.containsKey(p.getUUID())) throw new IllegalArgumentException("Listener is not connected");
        if (!scene && targets.stream().anyMatch(p -> cinematic.contains(p.getUUID()))) throw new IllegalStateException("Cinematic music has priority");
        stop(targets);
        var channel = new Channel(id, source, list, targets, scene);
        channels.add(channel); channel.next(); return channel.handle;
    }
    public synchronized Playback playGlobal(String playlist) {
        var source = catalog(); var list = source.playlists().get(playlist);
        if (list == null && source.tracks().containsKey(playlist)) list = new MusicCatalog.Playlist(List.of(playlist), false);
        if (list == null) throw new IllegalArgumentException("Unknown music: " + playlist);
        if (global != null) global.finish(Playback.Status.REPLACED);
        global = new Channel(playlist, source, list, List.of(), false);
        global.broadcast = true; channels.add(global); global.next(); return global.handle;
    }
    public synchronized void stop(Collection<ServerPlayer> players) {
        for (var p : players) {
            interrupted(p);
            for (var channel : List.copyOf(channels)) if (!channel.broadcast && channel.targets.remove(p)) {
                if (channel.targets.isEmpty()) channel.finish(Playback.Status.CANCELLED);
            }
        }
    }
    public synchronized void stopGlobal() { if (global != null) global.finish(Playback.Status.CANCELLED); }
    public synchronized void nextGlobal() { if (global != null) global.next(); }
    public synchronized void next(Collection<ServerPlayer> players) {
        var selected = new HashSet<Channel>();
        for (var p : players) { var channel = owners.get(p.getUUID()); if (channel != null) selected.add(channel); }
        selected.forEach(Channel::next);
    }
    private synchronized void tick() {
        if (closed) return;
        try {
            if (autoStart && !autoStarted && catalog().playlists().containsKey("all") && online.values().stream().anyMatch(MusicPacks::ready)) {
                autoStarted = true; playGlobal("all");
            }
            long now = System.nanoTime();
            for (var channel : List.copyOf(channels)) if (channel.timeline.elapsed(now)) channel.next();
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger("minigamelib").error("Music playback failed", e); }
    }
    @Override public synchronized void close() {
        closed = true; clock.shutdownNow();
        for (var channel : List.copyOf(channels)) channel.finish(Playback.Status.CANCELLED);
        online.clear(); cinematic.clear();
    }
    private final class Channel {
        final String id;
        MusicCatalog catalog;
        final Set<ServerPlayer> targets;
        final boolean scene;
        final Set<ServerPlayer> sounding = new HashSet<>();
        final Playback handle = new Playback(() -> { synchronized (MusicService.this) { finish(Playback.Status.CANCELLED); } });
        MusicTimeline timeline;
        Identifier sound;
        boolean broadcast;
        Channel(String id, MusicCatalog catalog, MusicCatalog.Playlist list, Collection<ServerPlayer> targets, boolean scene) {
            this.id = id; this.catalog = catalog; this.targets = new LinkedHashSet<>(targets); this.scene = scene; timeline = new MusicTimeline(list);
        }
        void next() {
            if (handle.done()) return;
            for (var p : List.copyOf(sounding)) { owners.remove(p.getUUID(), this); silence(p); }
            if (catalog != MusicService.this.catalog()) {
                catalog = MusicService.this.catalog();
                var list = catalog.playlists().get(id);
                if (list == null && catalog.tracks().containsKey(id)) list = new MusicCatalog.Playlist(List.of(id), false);
                if (list == null) { finish(Playback.Status.FAILED); return; }
                timeline = new MusicTimeline(list);
            }
            String next = timeline.next();
            if (next == null) { finish(Playback.Status.COMPLETED); return; }
            var track = MusicService.this.catalog().tracks().get(next);
            if (track == null) { finish(Playback.Status.FAILED); return; }
            sound = Identifier.fromNamespaceAndPath("minigamelib", "music/" + track.id());
            timeline.started(track.seconds(), System.nanoTime());
            for (var p : broadcast ? List.copyOf(online.values()) : List.copyOf(targets)) {
                if (!online.containsKey(p.getUUID()) || !MusicPacks.ready(p) || (!scene && cinematic.contains(p.getUUID()))) continue;
                var previous = owners.get(p.getUUID());
                if (broadcast && previous != null && previous != this) continue;
                if (previous != null) previous.silence(p);
                owners.put(p.getUUID(), this); sounding.add(p);
                var view = CameraViews.get(p);
                net.minecraft.world.entity.Entity anchor = p;
                if (view != null) anchor = view.soundAnchor();
                CameraViews.send(p, new ClientboundSoundEntityPacket(Holder.direct(SoundEvent.createVariableRangeEvent(sound)),
                        SoundSource.MUSIC, anchor, 1, 1, ThreadLocalRandom.current().nextLong()));
            }
        }
        void silence(ServerPlayer p) {
            if (sounding.remove(p) && sound != null)
                CameraViews.send(p, new ClientboundStopSoundPacket(sound, SoundSource.MUSIC));
        }
        void finish(Playback.Status status) {
            for (var p : List.copyOf(sounding)) { owners.remove(p.getUUID(), this); silence(p); }
            channels.remove(this); if (global == this) global = null;
            handle.finish(status, "");
        }
    }
}
