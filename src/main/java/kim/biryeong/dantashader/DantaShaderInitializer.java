package kim.biryeong.dantashader;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.dantashader.displayhud.DisplayHudDebugCommand;
import kim.biryeong.dantashader.displayhud.DisplayHudManager;
import kim.biryeong.dantashader.rgbutils.impl.RgbMapDebugCommand;
import kim.biryeong.dantashader.shaderfx.api.ShaderEffects;
import kim.biryeong.dantashader.shaderfx.impl.ModCommands;
import kim.biryeong.dantashader.shaderfx.impl.ModConfig;
import kim.biryeong.dantashader.shaderfx.impl.RPHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DantaShaderInitializer implements ModInitializer {
    public static final String MODID = "dantashader";
    private static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static MinecraftServerAudiences ADVENTURE;

    @Override
    public void onInitialize() {
        if (ModConfig.getInstance().addAssets) RPHandler.enableAssets();
        if (ModConfig.getInstance().enableAnimatedEmojiConversion) RPHandler.enableAnimojiConversion();
        if (ModConfig.getInstance().markAsRequired) PolymerResourcePackUtils.markAsRequired();
        PolymerResourcePackUtils.addModAssets(MODID);

        ShaderEffects.addImport(ResourceLocation.withDefaultNamespace("shaderfx_utils.glsl"));
        ShaderEffects.addImport(ResourceLocation.withDefaultNamespace("spikes.glsl"));
        ShaderEffects.addImport(ResourceLocation.withDefaultNamespace("fractal1.glsl"));
        ShaderEffects.addImport(ResourceLocation.withDefaultNamespace("fractal2.glsl"));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> ADVENTURE = MinecraftServerAudiences.of(server));
        DisplayHudManager.init();

        var joinEffect = ModConfig.getInstance().joinEffect;
        if (joinEffect != null && joinEffect.enabled()) {
            ServerPlayConnectionEvents.JOIN.register((serverGamePacketListener, packetSender, server) -> {
                var p1 = new ClientboundSetTitlesAnimationPacket(0, joinEffect.stay(), joinEffect.fadeOut());

                Component fx;
                if (joinEffect.type().equalsIgnoreCase("transition")) fx = ShaderEffects.transitionComponent(joinEffect.effect(), joinEffect.color());
                else fx = ShaderEffects.effectComponent(joinEffect.effect(), joinEffect.color());

                var p2 = new ClientboundSetTitleTextPacket(fx);
                serverGamePacketListener.send(new ClientboundBundlePacket(List.of(p1, p2)));
            });
        }

        ModCommands.register();
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            DisplayHudDebugCommand.register();
            RgbMapDebugCommand.register();
            LOGGER.info("Registered /displayhudtest command (development environment only)");
            LOGGER.info("Registered /rgbmapdebug command (development environment only)");
        }
    }
}
