package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.cinematic.CinematicCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;
import static org.junit.jupiter.api.Assertions.*;

class CinematicTest {
    @TempDir Path root;
    @Test void cameraCrossesYawBoundaryWithoutFullTurn() {
        var frames=List.of(new Keyframe(0,0,0,0,170,0),new Keyframe(20,10,4,6,-170,40));
        var middle=sample(frames,10);
        assertEquals(180,middle.yaw()); assertEquals(5,middle.x()); assertEquals(20,middle.pitch());
        assertEquals(frames.getLast(),sample(frames,20));
    }
    @Test void cutsHoldUntilTheirBoundaryAndInvalidInputIsRejected() {
        var first=new Keyframe(0,0,0,0,0,0);
        var last=new Keyframe(20,10,0,0,90,0,null,Interpolation.CUT,null,null,Map.of());
        assertEquals(first,sample(List.of(first,last),19)); assertEquals(last,sample(List.of(first,last),20));
        assertThrows(IllegalArgumentException.class,()->new Keyframe(0,Double.NaN,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CameraTrack(1,"test","test","minecraft:overworld",List.of(first,first)));
    }
    @Test void savesRoundTripAndStaleDraftCannotOverwrite(@TempDir Path directory) throws Exception {
        var catalog=new CinematicCatalog(directory);
        var track=new CameraTrack(1,"intro","인트로","minecraft:overworld",List.of(new Keyframe(0,1,2,3,4,5)));
        var draft=new CinematicCatalog.Draft(Kind.CAMERA,"intro",track,"");
        var saved=catalog.save(draft,track);
        assertEquals(track,catalog.read(Kind.CAMERA,"intro").value());
        assertThrows(java.io.IOException.class,()->catalog.save(draft,track));
        assertEquals(saved.revision(),catalog.read(Kind.CAMERA,"intro").revision());
    }
    @Test void badPathsMissingReferencesAndMalformedFilesFailWithoutOverwrite() throws Exception {
        var catalog=new CinematicCatalog(root);
        assertThrows(IllegalArgumentException.class,()->catalog.read(Kind.CAMERA,"../outside"));
        var scene=new Scene(1,"intro","intro",20,List.of(new Clip("missing",0)),List.of(),List.of());
        assertThrows(java.io.IOException.class,()->catalog.save(new CinematicCatalog.Draft(Kind.SCENE,"intro",scene,""),scene));
        assertFalse(Files.exists(root.resolve("scenes/intro.json")));
        Files.writeString(root.resolve("cameras/broken.json"),"{ invalid");
        assertThrows(java.io.IOException.class,()->catalog.read(Kind.CAMERA,"broken"));
    }
}
