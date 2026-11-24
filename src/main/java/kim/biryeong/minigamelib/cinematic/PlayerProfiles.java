package kim.biryeong.minigamelib.cinematic;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin profiles for mannequin actors, resolved through the server's profile resolver (Mojang API) once per name and
 * cached. Until resolution finishes the unresolved profile is returned, which the client may resolve on its own.
 */
public final class PlayerProfiles {
    private PlayerProfiles() {}
    private static final Map<String, ResolvableProfile> RESOLVED = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ResolvableProfile>> PENDING = new ConcurrentHashMap<>();

    /** Name or UUID string. Returns the resolved profile when known, otherwise an unresolved one (and starts resolving). */
    public static ResolvableProfile get(MinecraftServer server, String nameOrId) {
        var key = nameOrId.toLowerCase(java.util.Locale.ROOT);
        var done = RESOLVED.get(key);
        if (done != null) return done;
        resolve(server, nameOrId);
        return unresolved(nameOrId);
    }
    public static boolean resolved(String nameOrId) { return RESOLVED.containsKey(nameOrId.toLowerCase(java.util.Locale.ROOT)); }

    public static CompletableFuture<ResolvableProfile> resolve(MinecraftServer server, String nameOrId) {
        var key = nameOrId.toLowerCase(java.util.Locale.ROOT);
        var done = RESOLVED.get(key);
        if (done != null) return CompletableFuture.completedFuture(done);
        return PENDING.computeIfAbsent(key, k -> unresolved(nameOrId).resolveProfile(server.services().profileResolver())
                .thenApply(profile -> {
                    var resolved = ResolvableProfile.createResolved(profile);
                    RESOLVED.put(k, resolved);
                    return resolved;
                }).whenComplete((value, error) -> {
                    PENDING.remove(k);
                    if (error != null) LoggerFactory.getLogger("minigamelib").warn("Cannot resolve skin profile {}: {}", nameOrId, error.toString());
                }));
    }
    /** Seeds the cache, e.g. with a profile that already carries signed textures. */
    public static void put(String nameOrId, GameProfile profile) { RESOLVED.put(nameOrId.toLowerCase(java.util.Locale.ROOT), ResolvableProfile.createResolved(profile)); }

    private static ResolvableProfile unresolved(String nameOrId) {
        try { return ResolvableProfile.createUnresolved(UUID.fromString(nameOrId)); }
        catch (IllegalArgumentException e) { return ResolvableProfile.createUnresolved(nameOrId); }
    }
}
