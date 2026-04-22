package kim.biryeong.dantashader.shaderfx.impl;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import kim.biryeong.dantashader.DantaShaderInitializer;
import kim.biryeong.dantashader.shaderfx.api.ShaderEffects;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ModCommands {
    public static void register() {
        final SuggestionProvider<CommandSourceStack> SUGGESTER = (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(ShaderEffects.EFFECTS.keySet().stream().map(ResourceLocation::toString), suggestionsBuilder);
        final SuggestionProvider<CommandSourceStack> SUGGESTER_TRANSITIONS = (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(ShaderEffects.TRANSITIONS.keySet().stream().map(ResourceLocation::toString), suggestionsBuilder);
        CommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext, commandSelection) -> {
            dispatcher.register(commandFullscreen(SUGGESTER));
            dispatcher.register(commandFullscreenImageTransition(SUGGESTER_TRANSITIONS));
            dispatcher.register(commandLocal(SUGGESTER));
            dispatcher.register(commandCustom());
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandFullscreen(SuggestionProvider<CommandSourceStack> suggestionProvider) {
        return Commands.literal("shaderfx").requires(x -> x.hasPermission(2)).then(Commands.literal("run").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(suggestionProvider).then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("color", HexColorArgument.hexColor()).then(Commands.argument("fadeIn", IntegerArgumentType.integer(0)).then(Commands.argument("stay", IntegerArgumentType.integer(0)).then(Commands.argument("fadeOut", IntegerArgumentType.integer(0)).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            int fadeIn = IntegerArgumentType.getInteger(x, "fadeIn");
            int stay = IntegerArgumentType.getInteger(x, "stay");
            int fadeOut = IntegerArgumentType.getInteger(x, "fadeOut");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponent(id, color));
            var timesPacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
            player.connection.send(new ClientboundBundlePacket(ImmutableList.of(timesPacket, titlePacket)));

            return Command.SINGLE_SUCCESS;
        })))).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponent(id, color));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        })).executes(x -> {
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponent(id));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandFullscreenImageTransition(SuggestionProvider<CommandSourceStack> suggestionProvider) {
        return Commands.literal("shaderfx:transition").requires(x -> x.hasPermission(2)).then(Commands.literal("run").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(suggestionProvider).then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("color", HexColorArgument.hexColor()).then(Commands.argument("fadeIn", IntegerArgumentType.integer(0)).then(Commands.argument("stay", IntegerArgumentType.integer(0)).then(Commands.argument("fadeOut", IntegerArgumentType.integer(0)).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            int fadeIn = IntegerArgumentType.getInteger(x, "fadeIn");
            int stay = IntegerArgumentType.getInteger(x, "stay");
            int fadeOut = IntegerArgumentType.getInteger(x, "fadeOut");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.transitionComponent(id, color));
            var timesPacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
            player.connection.send(new ClientboundBundlePacket(ImmutableList.of(timesPacket, titlePacket)));

            return Command.SINGLE_SUCCESS;
        })))).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.transitionComponent(id, color));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        })).executes(x -> {
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.transitionComponent(id));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        }))));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> commandLocal(SuggestionProvider<CommandSourceStack> suggestionProvider) {
        return Commands.literal("shaderfx:local").requires(x -> x.hasPermission(2)).then(Commands.literal("run").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(suggestionProvider).then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("color", HexColorArgument.hexColor()).then(Commands.argument("fadeIn", IntegerArgumentType.integer(0)).then(Commands.argument("stay", IntegerArgumentType.integer(0)).then(Commands.argument("fadeOut", IntegerArgumentType.integer(0)).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            int fadeIn = IntegerArgumentType.getInteger(x, "fadeIn");
            int stay = IntegerArgumentType.getInteger(x, "stay");
            int fadeOut = IntegerArgumentType.getInteger(x, "fadeOut");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponentLocal(id, color));
            var timesPacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
            player.connection.send(new ClientboundBundlePacket(ImmutableList.of(timesPacket, titlePacket)));

            return Command.SINGLE_SUCCESS;
        })))).executes(x -> {
            int color = HexColorArgument.getHexColor(x, "color");
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponentLocal(id, color));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        })).executes(x -> {
            ResourceLocation id = ResourceLocationArgument.getId(x, "id");
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(ShaderEffects.effectComponentLocal(id));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandCustom() {
        return Commands.literal("shaderfx:custom").requires(x -> x.hasPermission(2)).then(Commands.literal("run").then(Commands.argument("text", StringArgumentType.string()).then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("fadeIn", IntegerArgumentType.integer(0)).then(Commands.argument("stay", IntegerArgumentType.integer(0)).then(Commands.argument("fadeOut", IntegerArgumentType.integer(0)).executes(x -> {
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            int fadeIn = IntegerArgumentType.getInteger(x, "fadeIn");
            int stay = IntegerArgumentType.getInteger(x, "stay");
            int fadeOut = IntegerArgumentType.getInteger(x, "fadeOut");

            var titlePacket = new ClientboundSetTitleTextPacket(DantaShaderInitializer.ADVENTURE.asNative(MiniMessage.miniMessage().deserialize(StringArgumentType.getString(x, "text"))));
            var timesPacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
            player.connection.send(new ClientboundBundlePacket(ImmutableList.of(timesPacket, titlePacket)));

            return Command.SINGLE_SUCCESS;
        })))).executes(x -> {
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(DantaShaderInitializer.ADVENTURE.asNative(MiniMessage.miniMessage().deserialize(StringArgumentType.getString(x, "text"))));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        })).executes(x -> {
            ServerPlayer player = EntityArgument.getPlayer(x, "player");

            var titlePacket = new ClientboundSetTitleTextPacket(DantaShaderInitializer.ADVENTURE.asNative(MiniMessage.miniMessage().deserialize(StringArgumentType.getString(x, "text"))));
            player.connection.send(titlePacket);

            return Command.SINGLE_SUCCESS;
        })));
    }
}

