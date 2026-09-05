package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.music.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicTest {
    @TempDir Path directory;
    private byte[] audio() throws IOException { try(var stream = getClass().getResourceAsStream("/music/tone.ogg")) { return stream.readAllBytes(); } }
    @Test void readsRealVorbisAndRejectsTruncationAndChecksumDamage() throws Exception {
        byte[] audio = audio();
        assertEquals(.4,OggVorbis.duration(new ByteArrayInputStream(audio)),.002);
        assertThrows(IOException.class,()->OggVorbis.duration(new ByteArrayInputStream(Arrays.copyOf(audio,audio.length-1))));
        audio[audio.length-10]^=1;
        assertThrows(IOException.class,()->OggVorbis.duration(new ByteArrayInputStream(audio)));
        assertThrows(IOException.class,()->OggVorbis.duration(new ByteArrayInputStream("OpusHead".getBytes())));
    }
    @Test void recursivelyBuildsStableSafeIdsAndPreservesNamesAndPlaylistOrder() throws Exception {
        Files.createDirectory(directory.resolve("하위 폴더"));
        Files.write(directory.resolve("하위 폴더/노래 하나.ogg"),audio());
        Files.write(directory.resolve("a.ogg"),audio());
        Files.writeString(directory.resolve("bad.ogg"),"bad");
        Files.writeString(directory.resolve("playlists.json"),"{\"custom\":{\"tracks\":[\"하위 폴더/노래 하나.ogg\",\"a.ogg\"],\"repeat\":false}}");
        var warnings=new ArrayList<String>(); var first=MusicCatalog.scan(directory,warnings::add);
        assertEquals(2,first.catalog().tracks().size()); assertEquals(1,warnings.size()); assertTrue(warnings.getFirst().contains("bad.ogg"));
        var all=first.catalog().playlists().get("all").tracks();
        assertEquals("a.ogg",first.catalog().tracks().get(all.getFirst()).path());
        assertEquals("노래 하나",first.catalog().tracks().get(all.getLast()).name());
        assertEquals(all.reversed(),first.catalog().playlists().get("custom").tracks());
        assertTrue(first.assets().containsKey("assets/minigamelib/sounds.json"));
        assertTrue(new String(first.assets().get("assets/minigamelib/sounds.json")).contains("\"stream\":true"));
        assertEquals(first.catalog(),MusicCatalog.scan(directory,s->{}).catalog());
        Files.delete(directory.resolve("a.ogg"));
        assertEquals(1,MusicCatalog.scan(directory,s->{}).catalog().tracks().size());
        assertFalse(MusicCatalog.scan(directory,s->{}).catalog().playlists().containsKey("custom"));
    }
    @Test void requiresLoadedStatusForTheSentUuidAndActiveHash() {
        var id=UUID.randomUUID(); var state=new PackReadiness(id,"abc",false);
        assertFalse(state.ready("abc"));
        assertFalse(state.acknowledged(id,false).ready("abc"));
        assertFalse(state.acknowledged(UUID.randomUUID(),true).ready("abc"));
        state=state.acknowledged(id,true); assertTrue(state.ready("abc")); assertFalse(state.ready("def"));
        assertFalse(new PackReadiness(id,"def",false).ready("def"));
    }
    @Test void sequenceRepeatAndMonotonicDeadlines() {
        var loop=new MusicTimeline(new MusicCatalog.Playlist(List.of("a","b"),true));
        assertEquals("a",loop.next()); loop.started(2,1_000);
        assertFalse(loop.elapsed(2_000_000_999L)); assertTrue(loop.elapsed(2_000_001_000L));
        assertEquals("b",loop.next()); assertEquals("a",loop.next());
        var once=new MusicTimeline(new MusicCatalog.Playlist(List.of("a","b"),false));
        assertEquals("a",once.next());assertEquals("b",once.next());assertNull(once.next());assertNull(once.next());
        assertThrows(IllegalArgumentException.class,()->loop.started(Double.NaN,0));
    }
}
