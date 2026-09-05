package kim.biryeong.minigamelib.camera.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import kim.biryeong.minigamelib.camera.api.CameraEffects;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CameraCommands {
    private CameraCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("minigamelib")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("fov")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("degrees", FloatArgumentType.floatArg(1.0f, Math.nextDown(180.0f)))
                                                        .executes(context -> {
                                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                            float fov = FloatArgumentType.getFloat(context, "degrees");
                                                            CameraEffects.setFov(player, fov);
                                                            context.getSource().sendSuccess(() -> Component.literal("FOV set to " + fov + " for " + player.getGameProfile().getName()), false);
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    CameraEffects.resetFov(player);
                                                    context.getSource().sendSuccess(() -> Component.literal("FOV reset for " + player.getGameProfile().getName()), false);
                                                    return Command.SINGLE_SUCCESS;
                                                }))))
        ));
    }
}
