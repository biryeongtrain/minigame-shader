package kim.biryeong.minigamelib.shaderfx.impl;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import kim.biryeong.minigamelib.shaderfx.api.ShaderEffects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ModConfig {
    static Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Identifier.class, new SimpleCodecDeserializer<>(Identifier.CODEC))
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();
    static Path CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("minigame-lib/shaderfx.json");
    static ModConfig instance;

    // config
    public boolean addAssets = true;
    public boolean enableAnimatedEmojiConversion = true;
    public boolean markAsRequired = true;
    public JoinEffect joinEffect = new JoinEffect(true, "transition", ShaderEffects.TRANSITION_ENCLOSING_TRIANGLES.location(), 0x0, 15, 20);

    public record JoinEffect(boolean enabled, String type, Identifier effect, int color, int stay, int fadeOut) {
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }
    public static boolean load() {
        try {
            java.nio.file.Files.createDirectories(CONFIG_FILE_PATH.getParent());
            Path legacy = FabricLoader.getInstance().getConfigDir().resolve("shaderfx.json");
            if (!java.nio.file.Files.exists(CONFIG_FILE_PATH) && java.nio.file.Files.exists(legacy)) {
                String migrated = java.nio.file.Files.readString(legacy).replace("dantashader:", "minigamelib:");
                java.nio.file.Files.writeString(CONFIG_FILE_PATH, migrated);
            }
        } catch (IOException e) { throw new IllegalStateException("Could not prepare shader configuration", e); }

        if (!Files.exists(CONFIG_FILE_PATH)) {
            instance = new ModConfig();
            save();
            return true;
        }

        try {
            ModConfig.instance = java.util.Objects.requireNonNull(GSON.fromJson(Files.readString(CONFIG_FILE_PATH), ModConfig.class), "Shader configuration is null");
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid shader configuration: " + CONFIG_FILE_PATH, e);
        }

        return false;
    }

    public static void save() {
        try {
            var temporary = Files.createTempFile(CONFIG_FILE_PATH.getParent(), ".shaderfx-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(instance));
                Files.move(temporary, CONFIG_FILE_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
