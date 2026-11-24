package kim.biryeong.minigamelib;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import kim.biryeong.minigamelib.cinematic.CinematicData.*;
import kim.biryeong.minigamelib.cinematic.Skins;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle.TextFormat;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueAssets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test mod only: a small stage with the andlist mannequin and a three-line dialogue scene. */
public final class DialogueDemo {
    private DialogueDemo() {}
    static final String SKIN = "andlist";
    static final double X = 0.5, Y = 150, Z = 0.5;

    /** Registers the portrait (baked into the pack) and seeds the signed skin profile so no lookup is needed. */
    static void init() {
        try (var skin = DialogueDemo.class.getResourceAsStream("/skins/andlist.png");
             var json = DialogueDemo.class.getResourceAsStream("/skins/andlist_profile.json")) {
            Skins.register(SKIN, skin);   // portrait "andlist" + mannequin texture "skin:andlist", shipped in the pack
            var textures = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("properties").get(0).getAsJsonObject();
            Skins.signed(SKIN, textures.get("value").getAsString(), textures.get("signature").getAsString()); // profile "andlist" without a lookup
        } catch (Exception e) { throw new IllegalStateException("Cannot load the andlist test skin", e); }
    }

    static void stage(MinecraftServer server) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        String[] commands = {
                "time set midnight", "gamerule advance_time false", "weather clear",
                "fill -8 149 -8 8 149 8 minecraft:polished_deepslate",
                "fill -8 150 -6 8 158 -6 minecraft:bookshelf",
                "fill -8 150 -5 -8 158 8 minecraft:dark_oak_planks",
                "fill 8 150 -5 8 158 8 minecraft:dark_oak_planks",
                "fill -8 159 -6 8 159 8 minecraft:dark_oak_slab",
                "fill -6 150 -5 6 150 -5 minecraft:dark_oak_stairs[facing=south]",
                "setblock -4 151 -5 minecraft:lantern", "setblock 4 151 -5 minecraft:lantern",
                "setblock -2 154 -5 minecraft:soul_lantern", "setblock 2 154 -5 minecraft:soul_lantern",
                "setblock 0 153 1 minecraft:light[level=13]", "setblock 1 152 0 minecraft:light[level=10]",
        };
        for (var command : commands) server.getCommands().performPrefixedCommand(source, command);
    }

    static ResolvedScene scene() {
        // The mannequin faces the camera (south, yaw 0); the close shot slowly dollies in on its face.
        return kim.biryeong.minigamelib.cinematic.CinematicBuilder.scene("dialogue-demo").name("대화 데모")
                .shot(0, s -> s.key(0, X + 0.62, Y + 1.7, Z - 0.55, 157, 6).fov(50)
                        .key(170, X + 0.45, Y + 1.66, Z - 0.75, 160, 3).fov(44).smooth())
                .shot(170, s -> s.key(0, X - 1.5, Y + 1.45, Z + 1.0, -152, 6).fov(60)
                        .key(130, X - 1.2, Y + 1.5, Z + 0.6, -150, 7).fov(55).smooth())
                .mannequin("andlist", SKIN, a -> a.skin(SKIN).key(0, X, Y, Z - 2, -16, 2)
                        .key(150, X, Y, Z - 2, -24, -2).smooth().key(200, X, Y, Z - 2, 26, 4).smooth())
                .letterbox()
                .line(new DialogueLine(12, "andlist", SKIN, null,
                        "내가 깜깜하고 좁은 데 들어가서 문 딱 닫고 숨었을 때 아무도 나 안 열어 주면, 키가 천장만큼 큰 『<b>술래</b>』가 나와서 <u>나쁜 사람</u> 다 혼내 준다?",
                        96, true, 0))
                .line(new DialogueLine(110, "andlist", SKIN, null, "아하하, 술래가 나와서 혼내 준대요. <st>무서워라.</st> <i>상상력 한번 귀엽네.</i>", 60, false, 0))
                .line(new DialogueLine(170, "<obf>???</obf>", "head:@viewer", null, "...그럼 지금 <#ff6b6b>문</#ff6b6b>을 열어 볼까?", 90, true, 24,
                        DialogueStyle.of().nameColor(0x9FB4FF).lineColor(0x4D5F8F).highlightColor(0xC9D6FF).textColor(0xDDE4F5)
                                .name(TextFormat.of().italic(true)).text(TextFormat.of().italic(true))))
                .marker(170, "cut-to-wide")
                .duration(300)
                .build();
    }

    static Playback play(ServerPlayer player) {
        return MinigameLib.get(player.level().getServer()).cinematics().play(scene(), List.of(player));
    }
}
