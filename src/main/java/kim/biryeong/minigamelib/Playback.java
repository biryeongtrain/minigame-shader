package kim.biryeong.minigamelib;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A server-thread playback handle. Completion is delivered after cleanup. */
public final class Playback implements AutoCloseable {
    public enum Status { COMPLETED, CANCELLED, REPLACED, FAILED }
    public record Result(Status status, String detail) {}
    private final CompletableFuture<Result> completion = new CompletableFuture<>();
    private final Runnable stop;
    @org.jetbrains.annotations.ApiStatus.Internal
    public Playback(Runnable stop) { this.stop = stop; }
    public CompletionStage<Result> completion() { return completion.minimalCompletionStage(); }
    public boolean done() { return completion.isDone(); }
    @org.jetbrains.annotations.ApiStatus.Internal
    public void finish(Status status, String detail) { completion.complete(new Result(status, detail)); }
    public void stop() { if (!done()) stop.run(); }
    @Override public void close() { stop(); }
}
