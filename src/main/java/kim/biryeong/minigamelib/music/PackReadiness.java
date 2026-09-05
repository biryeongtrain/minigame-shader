package kim.biryeong.minigamelib.music;

import java.util.UUID;

/** Immutable state binds the load acknowledgement to the pack that was sent. */
public record PackReadiness(UUID id, String hash, boolean loaded) {
    public PackReadiness acknowledged(UUID responseId, boolean successfullyLoaded) {
        return id.equals(responseId) ? new PackReadiness(id, hash, successfullyLoaded) : this;
    }
    public boolean ready(String activeHash) { return loaded && !activeHash.isEmpty() && hash.equalsIgnoreCase(activeHash); }
}
