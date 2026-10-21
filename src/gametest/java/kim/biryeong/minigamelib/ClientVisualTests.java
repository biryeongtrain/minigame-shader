package kim.biryeong.minigamelib;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Visual smoke test for the 26.3 shaders: builds minigame-lib's pack on the integrated server, loads it on the
 * client and takes screenshots of DisplayHud (normal, above-hand, global) and a ShaderFX effect.
 * Run with {@code ./gradlew runClientGameTest}; screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class ClientVisualTests implements FabricClientGameTest {
    private static final String PACK = "minigamelib-test.zip";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String api = System.getProperty("minigamelib.graphicsApi", "default");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();

            Path pack = sp.getServer().computeOnServer(server -> {
                try {
                    Path out = server.getServerDirectory().resolve(PACK);
                    var result = PolymerResourcePackUtils.getInstance().build(out);
                    if (result.hadIssues()) throw new IllegalStateException("Pack build had issues");
                    return out;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            CompletableFuture<?>[] reload = new CompletableFuture<?>[1];
            ctx.runOnClient(mc -> {
                Path dest = mc.getResourcePackDirectory().resolve(PACK);
                try {
                    Files.createDirectories(dest.getParent());
                    Files.copy(pack, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                var repo = mc.getResourcePackRepository();
                repo.reload();
                var selected = new ArrayList<>(repo.getSelectedIds());
                selected.add("file/" + PACK);
                repo.setSelected(selected);
                reload[0] = mc.reloadResourcePacks();
            });
            ctx.waitFor(mc -> reload[0].isDone(), 20 * 60);
            ctx.waitTicks(20);
            String logs = ctx.computeOnClient(mc -> String.valueOf(mc.getResourcePackRepository().getSelectedIds()));
            System.out.println("[minigamelib-visual] selected packs: " + logs + " api=" + api);

            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("gamerule advance_time false");
            sp.getServer().runCommand("give @p minecraft:diamond_sword");
            ctx.getInput().lookAt(0f, 10f);
            ctx.waitTicks(10);
            ctx.takeScreenshot(api + "-0-baseline");

            sp.getServer().runCommand("execute as @p run displayhudtest demo");
            ctx.waitTicks(10);
            ctx.takeScreenshot(api + "-1-displayhud-demo");

            sp.getServer().runCommand("execute as @p run displayhudtest clear");
            sp.getServer().runCommand("execute as @p run displayhudtest abovehand");
            ctx.waitTicks(10);
            ctx.takeScreenshot(api + "-2-displayhud-abovehand");

            sp.getServer().runCommand("execute as @p run displayhudtest clear");
            sp.getServer().runCommand("execute as @p run displayhudtest global show");
            ctx.waitTicks(10);
            ctx.takeScreenshot(api + "-3-displayhud-global");
            sp.getServer().runCommand("execute as @p run displayhudtest global remove");

            sp.getServer().runCommand("shaderfx run minigamelib:circle @p ff3366 10 200 10");
            ctx.waitTicks(30);
            ctx.takeScreenshot(api + "-4-shaderfx-circle");

            sp.getServer().runCommand("shaderfx:transition run minigamelib:transition-hbars @p 000000 20 40 20");
            ctx.waitTicks(25);
            ctx.takeScreenshot(api + "-5-shaderfx-transition");
            ctx.waitTicks(80);
        }
    }
}
