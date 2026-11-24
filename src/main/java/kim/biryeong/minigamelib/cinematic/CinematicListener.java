package kim.biryeong.minigamelib.cinematic;

import kim.biryeong.minigamelib.cinematic.CinematicData.DialogueLine;
import net.minecraft.server.level.ServerPlayer;

/**
 * Callbacks of one cinematic run, all on the server thread. Exceptions are logged and do not stop the scene.
 * Completion (finished, stopped, replaced, failed) is reported by {@link kim.biryeong.minigamelib.Playback#completion()}.
 * Paused editor previews fire nothing.
 */
public interface CinematicListener {
    CinematicListener NONE = new CinematicListener() {};

    /** A {@code MARKER} cue was reached; {@code id} is the cue's ID. On a tick where a line waits, fired after it is released. */
    default void onMarker(String id, int tick) {}
    /** A dialogue line started typing. */
    default void onLine(DialogueLine line) {}
    /** A {@code waitForInput} line reached its last tick; the scene holds until a viewer advances. */
    default void onWaiting(DialogueLine line) {}
    /** {@code player} (jump, sneak, left click or {@code CinematicService.advance}) released the waiting line. */
    default void onAdvance(ServerPlayer player, DialogueLine line) {}
}
