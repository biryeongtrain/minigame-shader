package kim.biryeong.minigamelib.cinematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;

/** Files are the source of truth; draft revisions prevent silent concurrent overwrites. */
public final class CinematicCatalog {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;
    public record Draft(Kind kind, String id, Object value, String revision) {}
    public CinematicCatalog(Path root) throws IOException {
        this.root = root;
        for (var kind : Kind.values()) Files.createDirectories(root.resolve(folder(kind)));
    }
    private static String folder(Kind kind) { return switch (kind) { case CAMERA -> "cameras"; case ENTITY -> "entities"; case SCENE -> "scenes"; }; }
    private static Class<?> type(Kind kind) { return switch (kind) { case CAMERA -> CameraTrack.class; case ENTITY -> EntityTrack.class; case SCENE -> Scene.class; }; }
    private Path path(Kind kind, String id) { return root.resolve(folder(kind)).resolve(CinematicData.id(id) + ".json"); }
    public List<String> list(Kind kind) throws IOException {
        try (var files = Files.list(root.resolve(folder(kind)))) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", "")).sorted().toList();
        }
    }
    public Draft read(Kind kind, String id) throws IOException {
        var path = path(kind,id);
        if (Files.isSymbolicLink(path)) throw new IOException("Cinematic files must not be symbolic links");
        byte[] bytes = Files.readAllBytes(path);
        Object value;
        try { value = JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),type(kind)); }
        catch (RuntimeException e) { throw new IOException("Invalid cinematic " + path + ": " + e.getMessage(),e); }
        if (value == null || !id.equals(assetId(value))) throw new IOException("Cinematic ID must match filename: " + path);
        return new Draft(kind,id,value,hash(bytes));
    }
    public static String assetId(Object value) {
        if (value instanceof CameraTrack x) return x.id();
        if (value instanceof EntityTrack x) return x.id();
        if (value instanceof Scene x) return x.id();
        throw new IllegalArgumentException("Unsupported cinematic definition");
    }
    public synchronized Draft save(Draft draft, Object value) throws IOException {
        if (!type(draft.kind).isInstance(value) || !draft.id.equals(assetId(value))) throw new IllegalArgumentException("Draft type or ID changed");
        var path = path(draft.kind,draft.id);
        String current = Files.exists(path,LinkOption.NOFOLLOW_LINKS) ? read(draft.kind,draft.id).revision : "";
        if (!current.equals(draft.revision)) throw new IOException("다른 관리자가 저장했습니다. 다시 열어 변경 내용을 확인하세요.");
        if (value instanceof Scene scene) resolve(scene, Map.of());
        byte[] bytes = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        var temp = Files.createTempFile(path.getParent(), ".draft-", ".tmp");
        try { Files.write(temp,bytes); Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
        return new Draft(draft.kind,draft.id,value,hash(bytes));
    }
    public synchronized void delete(Draft draft) throws IOException {
        if (!read(draft.kind,draft.id).revision.equals(draft.revision)) throw new IOException("File changed; reopen before deleting");
        if (draft.kind != Kind.SCENE) for (String id : list(Kind.SCENE)) {
            Scene scene = (Scene) read(Kind.SCENE,id).value;
            List<Clip> clips = draft.kind == Kind.CAMERA ? scene.cameras() : scene.entities();
            if (clips.stream().anyMatch(c -> c.track().equals(draft.id))) throw new IOException("Referenced by scene: " + id);
        }
        Files.delete(path(draft.kind,draft.id));
    }
    public ResolvedScene resolve(String id) throws IOException { return resolve((Scene) read(Kind.SCENE,id).value, Map.of()); }
    public ResolvedScene resolve(Scene scene, Map<String,Object> overrides) throws IOException {
        Map<String,CameraTrack> cameras = new HashMap<>(); Map<String,EntityTrack> entities = new HashMap<>();
        for (var c : scene.cameras()) cameras.put(c.track(), (CameraTrack) (overrides.containsKey("CAMERA/"+c.track()) ? overrides.get("CAMERA/"+c.track()) : read(Kind.CAMERA,c.track()).value));
        for (var c : scene.entities()) entities.put(c.track(), (EntityTrack) (overrides.containsKey("ENTITY/"+c.track()) ? overrides.get("ENTITY/"+c.track()) : read(Kind.ENTITY,c.track()).value));
        return new ResolvedScene(scene,cameras,entities);
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
