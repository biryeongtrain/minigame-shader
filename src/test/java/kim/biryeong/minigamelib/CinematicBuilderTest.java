package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.cinematic.CinematicBuilder;
import kim.biryeong.minigamelib.cinematic.CinematicCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;
import static org.junit.jupiter.api.Assertions.*;

class CinematicBuilderTest {
    @Test void buildsShotsActorsCuesAndInfersDuration() {
        var scene = CinematicBuilder.scene("intro")
                .shot(0, s -> s.key(0, 0, 70, 0, 10, 5).fov(50).key(40, 4, 71, 2, 20, 0).smooth())
                .still(40, 1, 2, 3, 0, 0)
                .mannequin("hero", "andlist", a -> a.start(10).key(0, 1, 70, 1, 0, 0).equip("mainhand", "minecraft:stick").key(20, 2, 70, 1, 90, 0))
                .letterbox().marker(30, "door")
                .build();
        var s = scene.scene();
        assertEquals(List.of(new Clip("intro.shot0", 0), new Clip("intro.shot1", 40)), s.cameras());
        assertEquals(50f, scene.cameras().get("intro.shot0").keyframes().getFirst().fov());
        assertEquals(Interpolation.SMOOTH, scene.cameras().get("intro.shot0").keyframes().getLast().interpolation());
        var hero = scene.entities().get("intro.hero");
        assertEquals("andlist", hero.profile());
        assertEquals("minecraft:mannequin", hero.type());
        assertEquals(Map.of("mainhand", "minecraft:stick"), hero.keyframes().getLast().equipment(), "later keys keep equipment");
        assertEquals(new Clip("intro.hero", 10), s.entities().getFirst());
        assertEquals(41, s.duration(), "ends after the last key");
        assertTrue(s.cues().stream().anyMatch(c -> c.type() == CueType.LETTERBOX && c.duration() == 41));
        assertTrue(s.cues().stream().anyMatch(c -> c.type() == CueType.MARKER && c.id().equals("door")));
    }
    @Test void dialogueBuilderTimesLinesFromTextLength() {
        var scene = CinematicBuilder.scene("talk").still(0, 0, 70, 0, 0, 0)
                .dialogue(10, d -> d.say("a", "a", "가".repeat(30)).waitForInput().say("b", null, "나나").color(0x123456).pause(20).say("c", null, "끝"))
                .build();
        var lines = scene.scene().dialogues();
        assertEquals(3, lines.size());
        var first = lines.getFirst();
        assertEquals(10, first.tick());
        assertEquals(20 + 8 + 1, first.duration(), "waiting lines last exactly the typing time");
        assertTrue(first.waitForInput());
        assertEquals(first.end() + 4, lines.get(1).tick());
        assertEquals(0x123456, lines.get(1).color());
        assertEquals(lines.get(1).end() + 4 + 20, lines.get(2).tick());
        assertEquals(lines.getLast().end(), scene.scene().duration());
    }
    @Test void rejectsUnorderedKeysAndMissingFirstShot() {
        assertThrows(IllegalArgumentException.class, () -> CinematicBuilder.scene("x").shot(0, s -> s.key(10, 0, 0, 0, 0, 0).key(5, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> CinematicBuilder.scene("x").shot(5, s -> s.key(0, 0, 70, 0, 0, 0)).build());
        assertThrows(IllegalArgumentException.class, () -> CinematicBuilder.scene("x").marker(1, " "));
    }
    @Test void savedSceneResolvesAgainAndRespectsOverwrite(@TempDir Path dir) throws Exception {
        var catalog = new CinematicCatalog(dir);
        var scene = CinematicBuilder.scene("saved").still(0, 0, 70, 0, 0, 0).actor("pig", "minecraft:pig", a -> a.key(0, 1, 70, 1, 0, 0)).build();
        catalog.save(scene, false);
        assertEquals(scene, catalog.resolve("saved"));
        assertThrows(java.io.IOException.class, () -> catalog.save(scene, false));
        catalog.save(scene, true);
    }
}
