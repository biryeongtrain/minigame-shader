package kim.biryeong.dantashader.shaderfx.impl;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import kim.biryeong.dantashader.shaderfx.api.ShaderEffects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ModConfig {
    static Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(ResourceLocation.class, new SimpleCodecDeserializer<>(ResourceLocation.CODEC))
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();
    static Path CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("shaderfx.json");
    static ModConfig instance;

    // config
    public boolean addAssets = true;
    public boolean enableAnimatedEmojiConversion = true;
    public boolean markAsRequired = true;
    public JoinEffect joinEffect = new JoinEffect(true, "transition", ShaderEffects.TRANSITION_ENCLOSING_TRIANGLES.location(), 0x0, 15, 20);

    public record JoinEffect(boolean enabled, String type, ResourceLocation effect, int color, int stay, int fadeOut) {
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
            save();
        }
        return instance;
    }
    public static boolean load() {
        if (!CONFIG_FILE_PATH.toFile().exists()) {
            instance = new ModConfig();
            try {
                if (CONFIG_FILE_PATH.toFile().createNewFile()) {
                    FileOutputStream stream = new FileOutputStream(CONFIG_FILE_PATH.toFile());
                    stream.write(GSON.toJson(instance).getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        try {
            ModConfig.instance = GSON.fromJson(new FileReader(CONFIG_FILE_PATH.toFile()), ModConfig.class);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    public static void save() {
        try (FileOutputStream stream = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
            stream.write(GSON.toJson(instance).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
