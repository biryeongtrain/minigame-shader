package kim.biryeong.minigamelib.music;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

public record MusicCatalog(Map<String, Track> tracks, Map<String, Playlist> playlists) {
    public record Track(String id, String path, String name, double seconds) {}
    public record Playlist(List<String> tracks, boolean repeat) {
        public Playlist { tracks = List.copyOf(tracks); if (tracks.isEmpty()) throw new IllegalArgumentException("Empty playlist"); }
    }
    public record Scan(MusicCatalog catalog, Map<String, byte[]> assets) {}
    public MusicCatalog { tracks = Collections.unmodifiableMap(new LinkedHashMap<>(tracks)); playlists = Map.copyOf(playlists); }
    public static MusicCatalog empty() { return new MusicCatalog(Map.of(), Map.of()); }
    public static Scan scan(Path directory, Consumer<String> warning) throws IOException {
        Files.createDirectories(directory);
        var tracks = new LinkedHashMap<String, Track>();
        var assets = new LinkedHashMap<String, byte[]>();
        var byPath = new HashMap<String, String>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted(Comparator.comparing(p -> directory.relativize(p).toString())).toList()) {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    double seconds = OggVorbis.duration(new ByteArrayInputStream(bytes));
                    String id = "track_" + hash(relative.getBytes(StandardCharsets.UTF_8));
                    String name = path.getFileName().toString(); name = name.substring(0, name.length() - 4);
                    tracks.put(id, new Track(id, relative, name, seconds)); byPath.put(relative, id);
                    assets.put("assets/minigamelib/sounds/music/" + id + ".ogg", bytes);
                } catch (IOException | IllegalArgumentException e) { warning.accept(relative + ": " + e.getMessage()); }
            }
        }
        var lists = new LinkedHashMap<String, Playlist>();
        if (!tracks.isEmpty()) lists.put("all", new Playlist(List.copyOf(tracks.keySet()), true));
        var definitions = directory.resolve("playlists.json");
        if (Files.exists(definitions)) {
            try {
                for (var entry : JsonParser.parseString(Files.readString(definitions)).getAsJsonObject().entrySet()) {
                    try {
                        var id = kim.biryeong.minigamelib.cinematic.CinematicData.id(entry.getKey());
                        if (id.equals("all")) throw new IllegalArgumentException("all is generated");
                        var json = entry.getValue().getAsJsonObject();
                        var ids = new ArrayList<String>();
                        for (var item : json.getAsJsonArray("tracks")) {
                            String ref = item.getAsString(), resolved = byPath.getOrDefault(ref, ref);
                            if (!tracks.containsKey(resolved)) throw new IllegalArgumentException("Unknown track: " + ref);
                            ids.add(resolved);
                        }
                        lists.put(id, new Playlist(ids, json.has("repeat") && json.get("repeat").getAsBoolean()));
                    } catch (RuntimeException e) { warning.accept("playlists.json/" + entry.getKey() + ": " + e.getMessage()); }
                }
            } catch (RuntimeException e) { warning.accept("playlists.json: " + e.getMessage()); }
        }
        var sounds = new JsonObject();
        for (var track : tracks.values()) {
            var sound = new JsonObject(); sound.addProperty("name", "minigamelib:music/" + track.id); sound.addProperty("stream", true);
            sound.addProperty("attenuation_distance", 0);
            var values = new JsonArray(); values.add(sound);
            var event = new JsonObject(); event.add("sounds", values);
            sounds.add("music/" + track.id, event);
        }
        assets.put("assets/minigamelib/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));
        return new Scan(new MusicCatalog(tracks, lists), assets);
    }
    public static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
