package kim.biryeong.minigamelib.music;

/** Monotonic audio time is independent of the server tick counter. */
public final class MusicTimeline {
    private final MusicCatalog.Playlist playlist;
    private int index = -1;
    private long end = Long.MAX_VALUE;
    private boolean finished;
    public MusicTimeline(MusicCatalog.Playlist playlist) { this.playlist = playlist; }
    public String next() {
        if (finished) return null;
        if (++index >= playlist.tracks().size()) {
            if (!playlist.repeat()) { finished = true; return null; }
            index = 0;
        }
        return playlist.tracks().get(index);
    }
    public void started(double seconds, long now) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 86400 * 365) throw new IllegalArgumentException("Invalid track length");
        end = now + (long)(seconds * 1_000_000_000L);
    }
    public boolean elapsed(long now) { return now - end >= 0; }
}
