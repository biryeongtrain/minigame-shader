package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.cinematic.CinematicCatalog;
import kim.biryeong.minigamelib.cinematic.dialogue.SkinBustRenderer;
import kim.biryeong.minigamelib.cinematic.dialogue.TextMetrics;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogueAssetsTest {
    @Test void bustRendersSlimSkinWithoutShaderMarkerAlpha() throws Exception {
        var skin = ImageIO.read(getClass().getResourceAsStream("/skins/andlist.png"));
        assertTrue(SkinBustRenderer.isSlim(skin), "andlist uses the slim model");
        var bust = SkinBustRenderer.render(skin, true);
        int opaque = 0;
        for (int y = 0; y < bust.getHeight(); y++) for (int x = 0; x < bust.getWidth(); x++) {
            int a = bust.getRGB(x, y) >>> 24;
            assertFalse(a >= 251 && a <= 253, "ShaderFX marker alpha at " + x + "," + y);
            if (a == 255) opaque++;
        }
        assertTrue(opaque > bust.getWidth() * bust.getHeight() / 5, "bust covers the image");
        var out = Path.of("build/test-output/andlist-bust.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(bust, "PNG", out.toFile());
    }

    @Test void bustFitsOneFontAtlasPage() throws Exception {
        var bust = SkinBustRenderer.render(ImageIO.read(getClass().getResourceAsStream("/skins/andlist.png")), true);
        assertTrue(bust.getWidth() <= 256 && bust.getHeight() <= 256, "font glyphs larger than 256 px are dropped by the client");
    }
    @Test void dialogueLinesRoundTripWithDefaultsAndValidation() {
        var json = """
                {"version":1,"id":"talk","name":"talk","duration":100,"cameras":[{"track":"cam","start":0}],
                 "dialogues":[{"tick":5,"speaker":"andlist","portrait":"andlist","text":"안녕","duration":20,"waitForInput":true},
                              {"tick":40,"speaker":"???","text":"다음","duration":10}]}""";
        var scene = CinematicCatalog.JSON.fromJson(json, Scene.class);
        assertEquals(2, scene.dialogues().size());
        var first = scene.dialogues().getFirst();
        assertEquals(DialogueLine.DEFAULT_SPEED, first.speed());
        assertNull(first.color(), "no accent set: the speaker/global/built-in style decides");
        assertEquals(DialogueLine.DEFAULT_COLOR, kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle.resolve(first).nameColor());
        assertTrue(first.waitForInput());
        assertNull(scene.dialogues().get(1).portrait());
        assertEquals(scene, CinematicCatalog.JSON.fromJson(CinematicCatalog.JSON.toJson(scene), Scene.class));
        assertTrue(CinematicCatalog.JSON.fromJson("""
                {"version":1,"id":"old","name":"old","duration":10,"cameras":[{"track":"cam","start":0}],"cues":[]}""", Scene.class).dialogues().isEmpty(),
                "scenes saved before dialogue support still load");
        var line = new DialogueLine(5, "a", null, "x", 5, false);
        assertThrows(IllegalArgumentException.class, () -> scene.withDialogues(List.of(line, line)), "ticks must increase");
        assertThrows(IllegalArgumentException.class, () -> scene.withDialogues(List.of(new DialogueLine(100, "a", null, "x", 5, false))), "line outside scene");
        assertThrows(IllegalArgumentException.class, () -> new DialogueLine(0, "a", null, "x", 0, false));
    }
    @Test void wrapKeepsWordsAndExplicitBreaks() {
        var lines = TextMetrics.wrap("가나다 라마바 사아자 차카타 파하", 9 * 7, false);
        for (var line : lines) assertTrue(TextMetrics.width(line, false) <= 9 * 7, line);
        assertEquals("가나다 라마바 사아자 차카타 파하", String.join(" ", lines));
        assertEquals(List.of("첫 줄", "둘째 줄"), TextMetrics.wrap("첫 줄\n둘째 줄", 200, false));
    }
}
