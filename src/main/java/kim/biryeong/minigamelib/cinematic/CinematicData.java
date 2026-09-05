package kim.biryeong.minigamelib.cinematic;

import java.util.*;

/** JSON and Java share these immutable cinematic definitions. Times are server ticks. */
public final class CinematicData {
    private CinematicData() {}
    public enum Interpolation { LINEAR, SMOOTH, CUT }
    public enum Kind { CAMERA, ENTITY, SCENE }
    public enum CueType { MUSIC, STOP_MUSIC, TRANSITION, EFFECT }

    public record Keyframe(int tick, double x, double y, double z, float yaw, float pitch, Float fov,
                           Interpolation interpolation, String pose, String animation, Map<String, String> equipment) {
        public Keyframe {
            if (tick < 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984 || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch) || pitch < -90 || pitch > 90)
                throw new IllegalArgumentException("Invalid keyframe time, position or rotation");
            if (fov != null && (!Float.isFinite(fov) || fov < 1 || fov >= 180)) throw new IllegalArgumentException("FOV must be in [1, 180)");
            interpolation = interpolation == null ? Interpolation.LINEAR : interpolation;
            equipment = equipment == null ? Map.of() : Map.copyOf(equipment);
        }
        public Keyframe(int tick, double x, double y, double z, float yaw, float pitch) {
            this(tick, x, y, z, yaw, pitch, null, Interpolation.LINEAR, null, null, Map.of());
        }
        public Keyframe at(int time) { return new Keyframe(time,x,y,z,yaw,pitch,fov,interpolation,pose,animation,equipment); }
    }

    public record CameraTrack(int version, String id, String name, String dimension, List<Keyframe> keyframes) {
        public CameraTrack { CinematicData.version(version); CinematicData.id(id); name = name == null ? id : name; Objects.requireNonNull(dimension); keyframes = frames(keyframes); }
    }
    public record EntityTrack(int version, String id, String name, String dimension, String type,
                              String item, String block, String text, List<Keyframe> keyframes) {
        public EntityTrack { CinematicData.version(version); CinematicData.id(id); name = name == null ? id : name; Objects.requireNonNull(dimension); Objects.requireNonNull(type); keyframes = frames(keyframes); }
    }
    public record Clip(String track, int start) {
        public Clip { id(track); if (start < 0) throw new IllegalArgumentException("Clip start must be nonnegative"); }
    }
    public record Cue(int tick, CueType type, String id, int duration, int color) {
        public Cue { if (tick < 0 || duration < 0) throw new IllegalArgumentException("Invalid cue time"); Objects.requireNonNull(type); }
    }
    public record Scene(int version, String id, String name, int duration, List<Clip> cameras, List<Clip> entities, List<Cue> cues) {
        public Scene {
            CinematicData.version(version); CinematicData.id(id); name = name == null ? id : name;
            if (duration < 1) throw new IllegalArgumentException("Scene must last at least one tick");
            cameras = List.copyOf(Objects.requireNonNull(cameras));
            if (cameras.isEmpty() || cameras.getFirst().start != 0) throw new IllegalArgumentException("Scene needs a camera at tick 0");
            int last = -1;
            for (var clip : cameras) { if (clip.start <= last || clip.start >= duration) throw new IllegalArgumentException("Camera clips must be ordered and non-overlapping"); last = clip.start; }
            entities = entities == null ? List.of() : List.copyOf(entities);
            if (new HashSet<>(entities).size() != entities.size()) throw new IllegalArgumentException("Duplicate entity clips; duplicate the entity track to create another actor");
            for (var clip : entities) if (clip.start >= duration) throw new IllegalArgumentException("Entity clip is outside scene");
            cues = cues == null ? List.of() : List.copyOf(cues);
            for (var cue : cues) if (cue.tick >= duration) throw new IllegalArgumentException("Cue is outside scene");
        }
    }
    public record ResolvedScene(Scene scene, Map<String, CameraTrack> cameras, Map<String, EntityTrack> entities) {
        public ResolvedScene {
            cameras = Map.copyOf(cameras); entities = Map.copyOf(entities);
            for (var clip : scene.cameras) if (!cameras.containsKey(clip.track)) throw new IllegalArgumentException("Missing camera: " + clip.track);
            for (var clip : scene.entities) if (!entities.containsKey(clip.track)) throw new IllegalArgumentException("Missing entity: " + clip.track);
        }
    }

    public static String id(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,63}") || id.contains("..")) throw new IllegalArgumentException("ID must contain 1–64 lowercase letters, digits, underscores, dots or hyphens");
        return id;
    }
    private static void version(int version) { if (version != 1) throw new IllegalArgumentException("Unsupported cinematic version: " + version); }
    private static List<Keyframe> frames(List<Keyframe> input) {
        var frames = List.copyOf(Objects.requireNonNull(input));
        if (frames.isEmpty() || frames.getFirst().tick != 0) throw new IllegalArgumentException("Track needs a keyframe at tick 0");
        int previous = -1;
        for (var frame : frames) { if (frame.tick <= previous) throw new IllegalArgumentException("Keyframes must have increasing times"); previous = frame.tick; }
        return frames;
    }
    public static Keyframe sample(List<Keyframe> frames, double tick) {
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("Invalid time");
        if (tick <= frames.getFirst().tick) return frames.getFirst();
        for (int i = 1; i < frames.size(); i++) {
            var b = frames.get(i); var a = frames.get(i - 1);
            if (tick >= b.tick) continue;
            if (b.interpolation == Interpolation.CUT) return a;
            double t = (tick - a.tick) / (b.tick - a.tick);
            if (b.interpolation == Interpolation.SMOOTH) t = t * t * (3 - 2 * t);
            float yawDelta = (float) (((b.yaw - a.yaw) % 360 + 540) % 360 - 180);
            Float fov = a.fov;
            if (a.fov != null && b.fov != null) fov = (float) (a.fov + (b.fov - a.fov) * t);
            return new Keyframe((int) tick, a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t, a.z + (b.z-a.z)*t,
                    a.yaw + yawDelta*(float)t, a.pitch+(b.pitch-a.pitch)*(float)t, fov, b.interpolation, a.pose, a.animation, a.equipment);
        }
        return frames.getLast();
    }
}
