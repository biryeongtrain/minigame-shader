package kim.biryeong.minigamelib;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.minigamelib.cinematic.CameraViews;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Visual check of the cinematic dialogue box with the andlist mannequin: typing, waiting for input, advancing with
 * sneak, a portrait-less speaker after a camera cut, and the restored view. Screenshots land in
 * {@code build/run/clientGameTest/screenshots} (prefix {@code dialogue-}).
 */
public class DialogueVisualTests implements FabricClientGameTest {
    private static final String PACK = "minigamelib-dialogue-test.zip";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            Path pack = sp.getServer().computeOnServer(server -> {
                Path out = server.getServerDirectory().resolve(PACK);
                try { if (PolymerResourcePackUtils.getInstance().build(out).hadIssues()) throw new IllegalStateException("Pack build had issues"); }
                catch (Exception e) { throw new RuntimeException(e); }
                return out;
            });
            CompletableFuture<?>[] reload = new CompletableFuture<?>[1];
            ctx.runOnClient(mc -> {
                Path dest = mc.getResourcePackDirectory().resolve(PACK);
                try { Files.createDirectories(dest.getParent()); Files.copy(pack, dest, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                var repo = mc.getResourcePackRepository();
                repo.reload();
                var selected = new ArrayList<>(repo.getSelectedIds());
                selected.add("file/" + PACK);
                repo.setSelected(selected);
                reload[0] = mc.reloadResourcePacks();
            });
            ctx.waitFor(mc -> reload[0].isDone(), 20 * 60);
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(20);

            sp.getServer().runOnServer(server -> {
                DialogueDemo.stage(server);
                var p = server.getPlayerList().getPlayers().getFirst();
                p.teleportTo(p.level(), DialogueDemo.X, DialogueDemo.Y, DialogueDemo.Z + 4, java.util.Set.of(), 180, 0, true);
            });
            ctx.waitTicks(60);
            sp.getServer().runOnServer(server -> DialogueDemo.play(server.getPlayerList().getPlayers().getFirst()));
            for (int i = 0; i < 400 && !sp.getServer().computeOnServer(server -> {
                var view = CameraViews.get(server.getPlayerList().getPlayers().getFirst());
                return view != null && view.ready();
            }); i++) ctx.waitTick();

            // Frames 8..56 every 2 ticks: entrance slide, typewriter fade (assembled into a clip by the caller).
            for (int i = 0; i < 25; i++) { ctx.waitTicks(i == 0 ? 8 : 2); ctx.takeScreenshot("dialogue-anim-" + String.format("%02d", i)); }
            ctx.takeScreenshot("dialogue-1-typing");   // ~tick 56
            ctx.waitTicks(20);   // ~76: line 1 fully revealed
            ctx.takeScreenshot("dialogue-2-revealed");
            ctx.waitTicks(40);   // held at tick 107 waiting for input
            ctx.takeScreenshot("dialogue-3-waiting");
            ctx.getInput().resizeWindow(1600, 600);   // ultrawide: bars stretch, the box stays anchored right
            ctx.waitTicks(3);
            ctx.takeScreenshot("dialogue-3b-ultrawide");
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(3);
            ctx.getInput().holdKeyFor(options -> options.keyShift, 4);
            ctx.waitTicks(20);   // line 2, same speaker: no entrance
            ctx.takeScreenshot("dialogue-4-next-line");
            ctx.waitTicks(80);   // after the cut: styled speaker with the viewer's head
            ctx.takeScreenshot("dialogue-5-viewer-head-styled");
            ctx.waitTicks(80);   // holding on the last line
            ctx.takeScreenshot("dialogue-6-waiting-again");
            ctx.getInput().holdKeyFor(options -> options.keyJump, 4);
            ctx.waitTicks(60);
            ctx.takeScreenshot("dialogue-7-restored");
        }
    }
}
