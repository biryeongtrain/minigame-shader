package kim.biryeong.minigamelib.cinematic;

import kim.biryeong.minigamelib.cinematic.CinematicData.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * Builds a {@link ResolvedScene} in code: camera shots, actors, cues, markers and dialogue. The result plays with
 * {@link CinematicService#play(ResolvedScene, Collection, CinematicListener)} and can be saved with
 * {@link CinematicCatalog#save(ResolvedScene, boolean)} to fine-tune it in the editor.
 *
 * <pre>{@code
 * var scene = CinematicBuilder.scene("intro")
 *         .shot(0, s -> s.key(0, x, y, z, 160, 5).fov(50).key(120, x2, y2, z2, 165, 2).smooth())
 *         .mannequin("andlist", "andlist", a -> a.key(0, ax, ay, az, 0, 0))
 *         .letterbox()
 *         .dialogue(10, d -> d.say("andlist", "andlist", "안녕?").waitForInput()
 *                             .say("???", null, "...누구지?").color(0x9fb4ff))
 *         .marker(200, "door")
 *         .build();
 * }</pre>
 *
 * Ticks are server ticks (20 per second). The duration defaults to the end of the last key, line or cue.
 * Track IDs are derived from the scene ID ({@code <scene>.shot0}, {@code <scene>.<actor>}).
 */
public final class CinematicBuilder {
    private final String id;
    private String name, dimension = "minecraft:overworld";
    private Integer duration;
    private final List<Shot> shots = new ArrayList<>();
    private final Map<String, ActorBuilder> actors = new LinkedHashMap<>();
    private final List<Cue> cues = new ArrayList<>();
    private final List<DialogueLine> lines = new ArrayList<>();
    private Integer letterboxColor; // whole-scene letterbox, resolved at build time

    private record Shot(int start, KeyBuilder<?> keys) {}

    private CinematicBuilder(String id) { this.id = CinematicData.id(id); this.name = id; }
    public static CinematicBuilder scene(String id) { return new CinematicBuilder(id); }

    public CinematicBuilder name(String name) { this.name = Objects.requireNonNull(name); return this; }
    /** Dimension of every shot and actor added after this call. */
    public CinematicBuilder dimension(String dimension) { this.dimension = Objects.requireNonNull(dimension); return this; }
    public CinematicBuilder dimension(ResourceKey<Level> level) { return dimension(level.identifier().toString()); }
    /** Fixed length in ticks; otherwise the end of the last key, line or cue. */
    public CinematicBuilder duration(int ticks) { this.duration = ticks; return this; }

    /** A camera shot starting at {@code start}; it lasts until the next shot. The first shot must start at 0. */
    public CinematicBuilder shot(int start, Consumer<KeyBuilder<?>> keys) {
        var builder = new KeyBuilder<>(dimension);
        keys.accept(builder);
        shots.add(new Shot(start, builder));
        return this;
    }
    /** A still camera from {@code start} until the next shot. */
    public CinematicBuilder still(int start, double x, double y, double z, float yaw, float pitch) {
        return shot(start, s -> s.key(0, x, y, z, yaw, pitch));
    }
    /** An actor of a vanilla mob or display type. {@code name} must be unique in the scene. */
    public CinematicBuilder actor(String name, String type, Consumer<ActorBuilder> setup) {
        if (actors.containsKey(name)) throw new IllegalArgumentException("Duplicate actor: " + name);
        var builder = new ActorBuilder(dimension, type);
        setup.accept(builder);
        actors.put(name, builder);
        return this;
    }
    /** A {@code minecraft:mannequin} wearing {@code profile}'s skin (player name or UUID). */
    public CinematicBuilder mannequin(String name, String profile, Consumer<ActorBuilder> setup) {
        return actor(name, "minecraft:mannequin", a -> { a.profile(profile); setup.accept(a); });
    }

    public CinematicBuilder cue(Cue cue) { cues.add(cue); return this; }
    public CinematicBuilder music(int tick, String music) { return cue(new Cue(tick, CueType.MUSIC, music, 0, 0)); }
    public CinematicBuilder stopMusic(int tick) { return cue(new Cue(tick, CueType.STOP_MUSIC, "", 0, 0)); }
    public CinematicBuilder effect(int tick, String effect, int ticks, int rgb) { return cue(new Cue(tick, CueType.EFFECT, effect, ticks, rgb)); }
    public CinematicBuilder transition(int tick, String transition, int ticks, int rgb) { return cue(new Cue(tick, CueType.TRANSITION, transition, ticks, rgb)); }
    /** Letterbox bars and vignette from {@code tick} for {@code ticks}. */
    public CinematicBuilder letterbox(int tick, int ticks) { return cue(new Cue(tick, CueType.LETTERBOX, "", ticks, 0)); }
    /** Black letterbox for the whole scene. */
    public CinematicBuilder letterbox() { letterboxColor = 0; return this; }
    /** Calls {@link CinematicListener#onMarker} when reached. */
    public CinematicBuilder marker(int tick, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Marker needs an ID");
        return cue(new Cue(tick, CueType.MARKER, id, 0, 0));
    }

    public CinematicBuilder line(DialogueLine line) { lines.add(line); return this; }
    /** Lines placed one after another from {@code start}, each lasting as long as it takes to read. */
    public CinematicBuilder dialogue(int start, Consumer<DialogueBuilder> lines) {
        var builder = new DialogueBuilder(start);
        lines.accept(builder);
        builder.flush();
        builder.done.forEach(this::line);
        return this;
    }

    public ResolvedScene build() {
        if (shots.isEmpty()) throw new IllegalArgumentException("Scene needs at least one shot");
        var sortedShots = shots.stream().sorted(Comparator.comparingInt(Shot::start)).toList();
        Map<String, CameraTrack> cameras = new LinkedHashMap<>();
        List<Clip> cameraClips = new ArrayList<>();
        int end = 1;
        for (int i = 0; i < sortedShots.size(); i++) {
            var shot = sortedShots.get(i);
            var trackId = id + ".shot" + i;
            var keys = shot.keys.frames();
            cameras.put(trackId, new CameraTrack(1, trackId, name + " #" + i, shot.keys.dimension, keys));
            cameraClips.add(new Clip(trackId, shot.start));
            end = Math.max(end, shot.start + keys.getLast().tick() + 1);
        }
        Map<String, EntityTrack> entities = new LinkedHashMap<>();
        List<Clip> entityClips = new ArrayList<>();
        for (var entry : actors.entrySet()) {
            var a = entry.getValue();
            var trackId = id + "." + CinematicData.id(entry.getKey());
            var keys = a.frames();
            entities.put(trackId, new EntityTrack(1, trackId, entry.getKey(), a.dimension, a.type, a.item, a.block, a.text, keys, a.profile));
            entityClips.add(new Clip(trackId, a.start));
            end = Math.max(end, a.start + keys.getLast().tick() + 1);
        }
        var sortedLines = lines.stream().sorted(Comparator.comparingInt(DialogueLine::tick)).toList();
        for (var line : sortedLines) end = Math.max(end, line.end());
        for (var cue : cues) end = Math.max(end, cue.tick() + Math.max(1, cue.duration()));
        int length = duration != null ? duration : end;
        var allCues = new ArrayList<>(cues);
        if (letterboxColor != null) allCues.add(new Cue(0, CueType.LETTERBOX, "", length, letterboxColor));
        allCues.sort(Comparator.comparingInt(Cue::tick));
        var scene = new Scene(1, id, name, length, cameraClips, entityClips, allCues, sortedLines);
        return new ResolvedScene(scene, cameras, entities);
    }

    /** Keyframes relative to the track start. Modifiers ({@link #fov}, {@link #smooth}, ...) change the last key. */
    public static class KeyBuilder<T extends KeyBuilder<T>> {
        final String dimension;
        private final List<Keyframe> frames = new ArrayList<>();
        KeyBuilder(String dimension) { this.dimension = dimension; }
        @SuppressWarnings("unchecked") T self() { return (T) this; }

        public T key(int tick, double x, double y, double z, float yaw, float pitch) {
            if (!frames.isEmpty() && tick <= frames.getLast().tick()) throw new IllegalArgumentException("Keys must have increasing ticks");
            frames.add(new Keyframe(tick, x, y, z, yaw, pitch));
            return self();
        }
        public T key(int tick, Vec3 pos, float yaw, float pitch) { return key(tick, pos.x, pos.y, pos.z, yaw, pitch); }
        /** Field of view at the last key (1..179). */
        public T fov(float fov) { return edit(f -> new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), fov, f.interpolation(), f.pose(), f.animation(), f.equipment())); }
        /** Ease into the last key. */
        public T smooth() { return interpolation(Interpolation.SMOOTH); }
        /** Jump to the last key at its tick instead of moving. */
        public T cut() { return interpolation(Interpolation.CUT); }
        public T linear() { return interpolation(Interpolation.LINEAR); }
        public T interpolation(Interpolation interpolation) {
            return edit(f -> new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), f.fov(), interpolation, f.pose(), f.animation(), f.equipment()));
        }
        T edit(java.util.function.UnaryOperator<Keyframe> change) {
            if (frames.isEmpty()) throw new IllegalStateException("Add a key first");
            frames.set(frames.size() - 1, change.apply(frames.getLast()));
            return self();
        }
        List<Keyframe> frames() {
            if (frames.isEmpty() || frames.getFirst().tick() != 0) throw new IllegalArgumentException("Tracks need a key at tick 0");
            return List.copyOf(frames);
        }
    }

    public static final class ActorBuilder extends KeyBuilder<ActorBuilder> {
        private final String type;
        private String item, block, text, profile;
        private int start;
        ActorBuilder(String dimension, String type) { super(dimension); this.type = Objects.requireNonNull(type); }
        /** Scene tick at which the actor appears (keys are relative to it). */
        public ActorBuilder start(int tick) { start = tick; return this; }
        /** Mannequin skin: a player name or UUID, or {@code skin:<id>} (see {@link Skins}). */
        public ActorBuilder profile(String nameOrUuid) { profile = nameOrUuid; return this; }
        /** Mannequin skin registered with {@link Skins#register}; shorthand for {@code profile("skin:" + id)}. */
        public ActorBuilder skin(String id) { profile = Skins.PREFIX + id; return this; }
        public ActorBuilder item(String id) { item = id; return this; }
        public ActorBuilder block(String id) { block = id; return this; }
        public ActorBuilder text(String text) { this.text = text; return this; }
        /** Pose at the last key, e.g. {@code crouching}, {@code sleeping}. */
        public ActorBuilder pose(String pose) { return edit(f -> new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), f.fov(), f.interpolation(), pose, f.animation(), f.equipment())); }
        /** {@code swing_main_hand}, {@code swing_off_hand} or {@code hurt}, played at the last key. */
        public ActorBuilder animation(String animation) { return edit(f -> new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), f.fov(), f.interpolation(), f.pose(), animation, f.equipment())); }
        /** Equipment from the last key on: slot ({@code mainhand}, {@code head}, ...) to item ID. Later keys inherit it. */
        public ActorBuilder equip(String slot, String item) {
            return edit(f -> { var map = new LinkedHashMap<>(f.equipment()); map.put(slot, item);
                return new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), f.fov(), f.interpolation(), f.pose(), f.animation(), map); });
        }
        @Override public ActorBuilder key(int tick, double x, double y, double z, float yaw, float pitch) {
            var previous = super.frames.isEmpty() ? null : super.frames.getLast();
            super.key(tick, x, y, z, yaw, pitch);
            // Keys apply their own pose/equipment; carry the previous ones so a moving actor keeps them.
            if (previous != null) edit(f -> new Keyframe(f.tick(), f.x(), f.y(), f.z(), f.yaw(), f.pitch(), null, f.interpolation(), previous.pose(), null, previous.equipment()));
            return this;
        }
    }

    /**
     * Sequential lines. Each line lasts typing time + {@link #hold} (default 30 ticks), or exactly the typing time
     * when it waits for input; the next line starts after {@link #gap} ticks (default 4).
     */
    public static final class DialogueBuilder {
        private int cursor, hold = 30, gap = 4;
        private final List<DialogueLine> done = new ArrayList<>();
        private String speaker, portrait, text;
        private Integer color, duration;
        private kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle style, defaults;
        private boolean wait;
        private float speed;
        private DialogueBuilder(int start) { cursor = start; }

        /** Starts a new line; {@code portrait} may be null. */
        public DialogueBuilder say(String speaker, String portrait, String text) {
            flush();
            this.speaker = speaker; this.portrait = portrait; this.text = Objects.requireNonNull(text);
            color = null; duration = null; wait = false; speed = 0; style = defaults;
            return this;
        }
        public DialogueBuilder waitForInput() { wait = true; return this; }
        /** Accent colour: name, dotted line and typing highlight. */
        public DialogueBuilder color(int rgb) { color = rgb; return this; }
        /** Colours and text formatting of the current line, over {@link #defaults}. */
        public DialogueBuilder style(kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle style) {
            this.style = this.style == null ? style : this.style.merge(style); return this;
        }
        /** Style of every following line in this block (a line's own {@link #style} goes on top). */
        public DialogueBuilder defaults(kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle style) { defaults = style; return this; }
        /** Characters per second (default 30). */
        public DialogueBuilder speed(float charsPerSecond) { speed = charsPerSecond; return this; }
        /** Exact length of the current line in ticks. */
        public DialogueBuilder duration(int ticks) { duration = ticks; return this; }
        /** Silence before the next line. */
        public DialogueBuilder pause(int ticks) { flush(); cursor += ticks; return this; }
        public DialogueBuilder hold(int ticks) { hold = ticks; return this; }
        public DialogueBuilder gap(int ticks) { gap = ticks; return this; }

        void flush() {
            if (text == null) return;
            float cps = speed > 0 ? speed : DialogueLine.DEFAULT_SPEED;
            var plain = kim.biryeong.minigamelib.cinematic.dialogue.DialogueText.plain(text).replace("\n", ""); // markup tags are not typed
            int chars = plain.codePointCount(0, plain.length());
            int typing = (int) Math.ceil(chars / cps * 20) + 8; // + entrance
            int length = duration != null ? duration : wait ? typing + 1 : typing + hold;
            done.add(new DialogueLine(cursor, speaker, portrait, color, text, length, wait, speed, style));
            cursor += length + gap;
            text = null;
        }
    }
}
