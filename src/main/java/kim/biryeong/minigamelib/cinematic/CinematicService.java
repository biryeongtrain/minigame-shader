package kim.biryeong.minigamelib.cinematic;

import kim.biryeong.minigamelib.Playback;
import kim.biryeong.minigamelib.camera.api.CameraEffects;
import kim.biryeong.minigamelib.music.MusicService;
import kim.biryeong.minigamelib.shaderfx.api.ShaderEffects;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;

public final class CinematicService implements AutoCloseable {
    private final MinecraftServer server;
    private final CinematicCatalog catalog;
    private final MusicService music;
    private final List<Run> runs = new ArrayList<>();
    public CinematicService(MinecraftServer server, CinematicCatalog catalog, MusicService music) {
        this.server = server; this.catalog = catalog; this.music = music;
    }
    public CinematicCatalog catalog() { return catalog; }
    public Playback play(String sceneId, Collection<ServerPlayer> players) throws IOException { return play(catalog.resolve(sceneId), players); }
    public Playback play(ResolvedScene scene, Collection<ServerPlayer> players) { return start(scene, players, 0, false); }
    public Playback preview(ResolvedScene scene, ServerPlayer player, int tick, boolean paused) { return start(scene, List.of(player), tick, paused); }
    private Playback start(ResolvedScene scene, Collection<ServerPlayer> players, int tick, boolean paused) {
        if (!server.isSameThread()) throw new IllegalStateException("Cinematic API requires the server thread");
        validate(scene);
        if (tick < 0 || tick >= scene.scene().duration()) throw new IllegalArgumentException("Time is outside scene");
        var targets = List.copyOf(new LinkedHashSet<>(players));
        if (targets.isEmpty()) throw new IllegalArgumentException("No viewers");
        for (var p : targets) if (p.level().getServer() != server || p.hasDisconnected() || p.isDeadOrDying() || p.isSleeping() || p.isPassenger())
            throw new IllegalArgumentException("Viewer must be connected, alive, awake and unmounted");
        for (var p : targets) stop(p, Playback.Status.REPLACED);
        var run = new Run(scene, tick, paused);
        try {
            var clip = run.camera(); var track = scene.cameras().get(clip.track()); var frame = sample(track.keyframes(), tick - clip.start());
            for (var p : targets) {
                var view = CameraViews.open(p, level(track.dimension()), position(frame), frame.yaw(), frame.pitch());
                view.holdCurtain();
                run.views.put(p, view); music.reserve(p, true);
            }
            runs.add(run); return run.handle;
        } catch (RuntimeException e) { run.finish(Playback.Status.FAILED, e.getMessage()); throw e; }
    }
    public ServerLevel level(String dimension) {
        var value = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        if (value == null) throw new IllegalArgumentException("Unknown dimension: " + dimension);
        return value;
    }
    public void validate(ResolvedScene scene) {
        for (var track : scene.cameras().values()) validateFrames(track.dimension(), track.keyframes());
        for (var track : scene.entities().values()) {
            validateFrames(track.dimension(), track.keyframes());
            var actor = Actor.create(track, level(track.dimension()));
            for (var frame : track.keyframes()) actor.apply(frame);
        }
        for (var cue : scene.scene().cues()) {
            switch (cue.type()) {
                case MUSIC -> { if (!music.catalog().tracks().containsKey(cue.id()) && !music.catalog().playlists().containsKey(cue.id())) throw new IllegalArgumentException("Unknown music: " + cue.id()); }
                case TRANSITION -> { if (!ShaderEffects.TRANSITIONS.containsKey(Identifier.parse(cue.id()))) throw new IllegalArgumentException("Unknown transition: " + cue.id()); }
                case EFFECT -> { if (!ShaderEffects.EFFECTS.containsKey(Identifier.parse(cue.id()))) throw new IllegalArgumentException("Unknown effect: " + cue.id()); }
                case STOP_MUSIC -> {}
            }
        }
    }
    private void validateFrames(String dimension, List<Keyframe> frames) {
        var world = level(dimension);
        for (var frame : frames) if (!world.getWorldBorder().isWithinBounds(position(frame)) || frame.y() < world.getMinY() - 1024 || frame.y() > world.getMaxY() + 1024)
            throw new IllegalArgumentException("Keyframe is outside world bounds");
    }
    private static Vec3 position(Keyframe f) { return new Vec3(f.x(), f.y(), f.z()); }
    public void stop(ServerPlayer player) { stop(player, Playback.Status.CANCELLED); CameraViews.stop(player); }
    private void stop(ServerPlayer player, Playback.Status reason) {
        for (var run : List.copyOf(runs)) if (run.views.containsKey(player)) {
            run.remove(player);
            if (run.views.isEmpty()) run.finish(reason, "");
        }
    }
    public void tick() {
        for (var run : List.copyOf(runs)) {
            try { run.tick(); }
            catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("minigamelib").error("Cinematic {} failed", run.scene.scene().id(), e);
                run.finish(Playback.Status.FAILED, e.getMessage());
            }
        }
    }
    @Override public void close() { for (var run : List.copyOf(runs)) run.finish(Playback.Status.CANCELLED, "Server stopped"); }

    private final class Run {
        final ResolvedScene scene;
        final Map<ServerPlayer, CameraViewSession> views = new LinkedHashMap<>();
        final Map<Clip, Actor> actors = new LinkedHashMap<>();
        final Playback handle = new Playback(() -> { if (server.isSameThread()) finish(Playback.Status.CANCELLED, ""); else server.execute(() -> finish(Playback.Status.CANCELLED, "")); });
        final boolean paused;
        int time, renderedTime = -1;
        boolean prepared, rendered, waiting = true;
        Cue visibleEffect;
        Clip lastCamera;
        Playback soundtrack;
        Run(ResolvedScene scene, int time, boolean paused) { this.scene = scene; this.time = time; this.paused = paused; }
        Clip camera() {
            Clip clip = scene.scene().cameras().getFirst();
            for (var next : scene.scene().cameras()) { if (next.start() > time) break; clip = next; }
            return clip;
        }
        void tick() {
            String failure = null;
            for (var entry : List.copyOf(views.entrySet())) if (entry.getValue().closed()) {
                if (entry.getValue().failure() != null) failure = entry.getValue().failure();
                remove(entry.getKey());
            }
            if (failure != null) { finish(Playback.Status.FAILED, failure); return; }
            if (views.isEmpty()) { finish(Playback.Status.CANCELLED, "Viewers left"); return; }
            if (views.values().stream().anyMatch(v -> !v.ready())) return;
            if (waiting) { views.values().forEach(v -> v.curtain(false)); waiting = false; }
            var clip = camera(); var track = scene.cameras().get(clip.track());
            var frame = sample(track.keyframes(), time - clip.start());
            boolean cut = !clip.equals(lastCamera) || track.keyframes().stream().anyMatch(k -> k.tick() == time - clip.start() && k.interpolation() == Interpolation.CUT);
            if (!rendered || renderedTime != time) {
                boolean dimension = views.values().stream().anyMatch(v -> v.level() != level(track.dimension()));
                for (var view : views.values()) {
                    view.move(level(track.dimension()), position(frame), frame.yaw(), frame.pitch(), cut && lastCamera != null);
                    if (frame.fov() != null) CameraEffects.setFov(view.player(), frame.fov());
                }
                lastCamera = clip;
                renderActors(); rendered = true; renderedTime = time;
                if (dimension || (cut && time > 0)) { waiting = true; return; }
            }
            if (!prepared) {
                prepared = true;
                if (!paused) {
                    Cue active = null;
                    for (var cue : scene.scene().cues().stream().sorted(Comparator.comparingInt(Cue::tick)).toList())
                        if (cue.tick() < time && (cue.type() == CueType.MUSIC || cue.type() == CueType.STOP_MUSIC)) active = cue;
                    if (active != null) cue(active);
                }
            }
            Cue effect = null;
            for (var candidate : scene.scene().cues())
                if ((candidate.type() == CueType.EFFECT || candidate.type() == CueType.TRANSITION)
                        && candidate.tick() <= time && time - candidate.tick() < candidate.duration()
                        && (effect == null || candidate.tick() >= effect.tick())) effect = candidate;
            if (effect != null) visualCue(effect);
            else if (visibleEffect != null) views.values().forEach(v -> v.visualEffect(Component.empty(),255));
            visibleEffect = effect;
            if (paused) return;
            for (var cue : scene.scene().cues()) if (cue.tick() == time && (cue.type() == CueType.MUSIC || cue.type() == CueType.STOP_MUSIC)) cue(cue);
            if (++time >= scene.scene().duration()) finish(Playback.Status.COMPLETED, "");
        }
        void renderActors() {
            for (var clip : scene.scene().entities()) {
                if (clip.start() > time) continue;
                var definition = scene.entities().get(clip.track());
                var actor = actors.computeIfAbsent(clip, c -> Actor.create(definition, level(definition.dimension())));
                actor.apply(sample(definition.keyframes(), time - clip.start()));
                for (var view : views.values()) actor.send(view, time - clip.start());
            }
        }
        void cue(Cue cue) {
            if (cue.type() == CueType.MUSIC || cue.type() == CueType.STOP_MUSIC) {
                if (soundtrack != null) soundtrack.stop();
                if (cue.type() == CueType.MUSIC) soundtrack = music.play(cue.id(), views.keySet(), true);
                return;
            }
        }
        void visualCue(Cue cue) {
            var id = Identifier.parse(cue.id());
            Component text = cue.type() == CueType.TRANSITION ? ShaderEffects.transitionComponent(id, cue.color()) : ShaderEffects.effectComponent(id, cue.color());
            int opacity = cue.type() == CueType.TRANSITION ? Math.round(255f * (1f - (float)(time - cue.tick()) / cue.duration())) : 255;
            for (var view : views.values()) view.visualEffect(text,opacity);
        }
        void remove(ServerPlayer player) {
            for (var actor : actors.values()) actor.remove(player);
            var view = views.remove(player); if (view != null) view.close();
            music.stop(List.of(player)); music.reserve(player, false);
        }
        void finish(Playback.Status status, String detail) {
            if (handle.done()) return;
            if (soundtrack != null) soundtrack.stop();
            for (var p : List.copyOf(views.keySet())) remove(p);
            runs.remove(this); handle.finish(status, detail == null ? "" : detail);
        }
    }

    private static final class Actor {
        final Entity entity;
        final EntityTrack track;
        final Map<ServerPlayer, ServerLevel> viewers = new HashMap<>();
        Actor(Entity entity, EntityTrack track) { this.entity = entity; this.track = track; entity.setNoGravity(true); }
        static Actor create(EntityTrack track, ServerLevel level) {
            var key = Identifier.parse(track.type());
            if (!key.getNamespace().equals("minecraft") || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) throw new IllegalArgumentException("Expected vanilla entity type");
            var entity = BuiltInRegistries.ENTITY_TYPE.getValue(key).create(level, EntitySpawnReason.COMMAND);
            if (!(entity instanceof Mob) && !(entity instanceof Display)) throw new IllegalArgumentException("Actors support mobs and displays");
            if (entity instanceof Display.ItemDisplay display) display.setItemStack(item(track.item() == null ? "minecraft:stone" : track.item()));
            if (entity instanceof Display.BlockDisplay display) {
                var block = Identifier.parse(track.block() == null ? "minecraft:stone" : track.block());
                if (!BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalArgumentException("Unknown block: " + block);
                display.setBlockState(BuiltInRegistries.BLOCK.getValue(block).defaultBlockState());
            }
            if (entity instanceof Display.TextDisplay display) display.setText(Component.literal(track.text() == null ? track.name() : track.text()));
            return new Actor(entity, track);
        }
        static ItemStack item(String id) {
            var key = Identifier.parse(id);
            if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Unknown item: " + id);
            return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
        }
        void apply(Keyframe frame) {
            entity.setPos(position(frame)); entity.setYRot(frame.yaw()); entity.setXRot(frame.pitch());
            entity.setPose(frame.pose() == null ? Pose.STANDING : Pose.valueOf(frame.pose().toUpperCase(Locale.ROOT)));
            if (frame.animation() != null && !Set.of("swing_main_hand", "swing_off_hand", "hurt").contains(frame.animation())) throw new IllegalArgumentException("Unsupported animation: " + frame.animation());
            if (entity instanceof LivingEntity living) {
                living.setYHeadRot(frame.yaw());
                for (var slot : EquipmentSlot.values()) living.setItemSlot(slot, ItemStack.EMPTY);
                for (var equipment : frame.equipment().entrySet()) living.setItemSlot(EquipmentSlot.valueOf(equipment.getKey().toUpperCase(Locale.ROOT)), item(equipment.getValue()));
            } else if (!frame.equipment().isEmpty()) throw new IllegalArgumentException("Display actors do not have equipment");
        }
        void send(CameraViewSession view, int time) {
            if (view.level() != entity.level()) { remove(view.player()); return; }
            if (viewers.get(view.player()) != view.level()) {
                new ServerEntity(view.level(), entity, net.minecraft.world.entity.UpdateInterval.periodic(1), false, CameraViews.synchronizer(view::send)).sendPairingData(view.player(), view::send);
                viewers.put(view.player(), view.level());
            }
            view.send(ClientboundEntityPositionSyncPacket.of(entity));
            // Include defaults too: a pose/equipment reset must reach an already spawned actor.
            var data = Arrays.stream(((kim.biryeong.minigamelib.mixin.EntityDataAccessor)entity.getEntityData()).minigamelib$items())
                    .<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>map(net.minecraft.network.syncher.SynchedEntityData.DataItem::value).toList();
            view.send(new ClientboundSetEntityDataPacket(entity.getId(), data));
            if (entity instanceof LivingEntity living) {
                view.send(new ClientboundRotateHeadPacket(entity, (byte)(living.getYHeadRot()*256/360)));
                var items = new ArrayList<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>>();
                for (var slot : EquipmentSlot.values()) items.add(com.mojang.datafixers.util.Pair.of(slot, living.getItemBySlot(slot).copy()));
                view.send(new ClientboundSetEquipmentPacket(entity.getId(), items));
            }
            for (var frame : track.keyframes()) if (frame.tick() == time && frame.animation() != null) {
                if (frame.animation().equals("hurt")) view.send(new ClientboundHurtAnimationPacket(entity.getId(), entity.getYRot()));
                else view.send(new ClientboundAnimatePacket(entity, frame.animation().equals("swing_main_hand") ? 0 : 3));
            }
        }
        void remove(ServerPlayer player) { if (viewers.remove(player) != null) CameraViews.send(player, new ClientboundRemoveEntitiesPacket(entity.getId())); }
    }
}
