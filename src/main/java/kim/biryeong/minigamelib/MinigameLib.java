package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.cinematic.*;
import kim.biryeong.minigamelib.music.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class MinigameLib implements AutoCloseable {
    public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("minigame-lib");
    private static final Map<MinecraftServer, MinigameLib> SERVERS = Collections.synchronizedMap(new IdentityHashMap<>());
    private final MusicService music;
    private final CinematicService cinematics;
    private final CinematicEditor editor;
    private MinigameLib(MinecraftServer server) {
        try {
            Files.createDirectories(CONFIG);
            var settings = CONFIG.resolve("music.json");
            if (!Files.exists(settings)) Files.writeString(settings, "{\n  \"autoStart\": false\n}\n");
            var json = com.google.gson.JsonParser.parseString(Files.readString(settings)).getAsJsonObject();
            music = new MusicService(json.has("autoStart") && json.get("autoStart").getAsBoolean());
            cinematics = new CinematicService(server, new CinematicCatalog(CONFIG.resolve("cinematics")), music);
            editor = new CinematicEditor(server, cinematics);
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot load minigame-lib configuration", e); }
    }
    public static MinigameLib get(MinecraftServer server) {
        synchronized (SERVERS) { return SERVERS.computeIfAbsent(Objects.requireNonNull(server), MinigameLib::new); }
    }
    public CinematicService cinematics() { return cinematics; }
    public MusicService music() { return music; }
    public CinematicEditor editor() { return editor; }
    static void init() {
        MusicPacks.init(CONFIG.resolve("music"));
        ServerLifecycleEvents.SERVER_STARTING.register(MinigameLib::get);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var lib = SERVERS.get(server); if (lib != null) lib.close(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
        ServerTickEvents.END_SERVER_TICK.register(server -> { var lib = get(server); lib.cinematics.tick(); lib.editor.tick(); });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive) -> get(newPlayer.getServer()).music.joined(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(server).music.joined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var lib = get(server); lib.editor.close(handler.player); lib.cinematics.stop(handler.player); lib.music.left(handler.player);
        });
        LibraryCommands.register();
    }
    @Override public void close() { editor.close(); cinematics.close(); music.close(); }
}
