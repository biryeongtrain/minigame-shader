package kim.biryeong.minigamelib.cinematic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueAssets;
import kim.biryeong.minigamelib.cinematic.dialogue.SkinBustRenderer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.ResolvableProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player skins for dialogue scenes. A registered skin becomes both a chest-up portrait (portrait ID = skin ID) and a
 * mannequin texture shipped in the pack, so an actor with profile {@code skin:<id>} wears it without a Minecraft
 * account or a Mojang lookup. Registered skins and portraits reach players with the next pack build
 * ({@code /polymer generate-pack} or a restart); register them in your mod initializer or on server start.
 *
 * <p>Mannequin profiles ({@link CinematicData.EntityTrack#profile()}):
 * <ul>
 *   <li>{@code skin:<id>} — a skin registered here or a 64x64 PNG in {@code config/minigame-lib/dialogue/portraits};</li>
 *   <li>a player name or UUID — looked up through Mojang, or seeded with {@link #signed}.</li>
 * </ul>
 */
public final class Skins {
    private Skins() {}
    public static final String PREFIX = "skin:";
    private record Entry(BufferedImage image, boolean slim) {}
    private static final Map<String, Entry> SKINS = new ConcurrentHashMap<>();

    /** Registers a 64x64 skin; the slim (Alex) model is detected from the arm pixels. */
    public static void register(String id, BufferedImage skin) { register(id, skin, SkinBustRenderer.isSlim(skin)); }
    public static void register(String id, BufferedImage skin, boolean slim) {
        if (skin.getWidth() != 64 || skin.getHeight() != 64) throw new IllegalArgumentException("Skins must be 64x64 PNGs");
        DialogueAssets.registerSkin(id, skin, slim); // validates the ID and draws the portrait
        SKINS.put(id, new Entry(skin, slim));
    }
    public static void register(String id, InputStream png) throws IOException { register(id, read(png)); }
    public static void register(String id, Path png) throws IOException { try (var in = Files.newInputStream(png)) { register(id, in); } }
    public static boolean registered(String id) { return SKINS.containsKey(id); }
    public static Set<String> ids() { return Set.copyOf(SKINS.keySet()); }

    /**
     * Downloads {@code player}'s current skin, saves it to the portrait folder and registers it as
     * {@code <player in lower case>}. Completes off the server thread with the skin ID.
     */
    public static CompletableFuture<String> fetch(MinecraftServer server, String player) {
        return PlayerProfiles.resolve(server, player).thenApplyAsync(profile -> {
            try {
                var textures = server.services().sessionService().getTextures(profile.partialProfile());
                if (textures.skin() == null) throw new IllegalStateException("No skin");
                var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
                var bytes = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(textures.skin().getUrl())).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();
                var image = read(new java.io.ByteArrayInputStream(bytes));
                var id = player.toLowerCase(Locale.ROOT);
                var folder = DialogueAssets.portraitFolder();
                if (folder != null) { Files.createDirectories(folder); ImageIO.write(image, "PNG", folder.resolve(id + ".png").toFile()); }
                register(id, image, "slim".equals(textures.skin().getMetadata("model")));
                return id;
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        });
    }

    /**
     * Seeds the mannequin profile cache with signed textures (the {@code textures} property of a profile, e.g. from
     * the session server or a skin site), so {@code name} needs no lookup. Returns the profile.
     */
    public static GameProfile signed(String name, String value, String signature) {
        var decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
        var uuid = decoded.has("profileId") ? decoded.get("profileId").getAsString()
                .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5") : UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        var profileName = decoded.has("profileName") ? decoded.get("profileName").getAsString() : name;
        var properties = com.google.common.collect.LinkedHashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", value, signature));
        var profile = new GameProfile(UUID.fromString(uuid), profileName, new PropertyMap(properties));
        PlayerProfiles.put(name, profile);
        return profile;
    }

    /** The mannequin profile for {@code spec} (see the class description). */
    public static ResolvableProfile profile(MinecraftServer server, String spec) {
        if (spec.startsWith(PREFIX)) return custom(spec.substring(PREFIX.length()));
        return PlayerProfiles.get(server, spec);
    }
    /** Whether {@link #profile} is final (custom skins always are; looked-up players once resolved). */
    public static boolean resolved(String spec) { return spec.startsWith(PREFIX) || PlayerProfiles.resolved(spec); }

    private static ResolvableProfile custom(String id) {
        var entry = SKINS.get(id);
        if (entry == null) DialogueAssets.portrait(id); // loads config/minigame-lib/dialogue/portraits/<id>.png if present
        entry = SKINS.get(id);
        var json = new JsonObject();
        json.addProperty("texture", texture(id));
        json.addProperty("model", entry != null && entry.slim ? "slim" : "wide");
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
    }
    static String texture(String id) { return "minigamelib:entity/skin/" + id; }

    /** Adds the mannequin textures to the pack. */
    public static void addToPack(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder) {
        for (var entry : new TreeMap<>(SKINS).entrySet()) {
            var out = new ByteArrayOutputStream();
            try { ImageIO.write(entry.getValue().image, "PNG", out); } catch (IOException e) { throw new IllegalStateException(e); }
            builder.addData("assets/minigamelib/textures/entity/skin/" + entry.getKey() + ".png", out.toByteArray());
        }
    }

    private static BufferedImage read(InputStream png) throws IOException {
        var image = ImageIO.read(png);
        if (image == null) throw new IOException("Not a PNG");
        if (image.getWidth() == 64 && image.getHeight() == 32) image = legacy(image);
        var argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(image, 0, 0, null);
        return argb;
    }
    /** 64x32 skins: mirror the right arm and leg into the 64x64 layout, as the client does. */
    private static BufferedImage legacy(BufferedImage old) {
        var out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.drawImage(old, 0, 0, null);
        int[][] copies = {{4,16,16,48,4,4},{8,16,20,48,4,4},{0,20,24,52,4,12},{4,20,20,52,4,12},{8,20,16,52,4,12},{12,20,28,52,4,12},
                {44,16,32,48,4,4},{48,16,36,48,4,4},{40,20,40,52,4,12},{44,20,36,52,4,12},{48,20,32,52,4,12},{52,20,44,52,4,12}};
        for (var c : copies) g.drawImage(old, c[2] + c[4], c[3], c[2], c[3] + c[5], c[0], c[1], c[0] + c[4], c[1] + c[5], null);
        g.dispose();
        return out;
    }
}
