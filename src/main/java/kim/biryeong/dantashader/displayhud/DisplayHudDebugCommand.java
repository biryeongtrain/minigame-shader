package kim.biryeong.dantashader.displayhud;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;

public final class DisplayHudDebugCommand {
    private static final String TEXT_ID = "debug:text";
    private static final String ITEM_ID = "debug:item";
    private static final String BLOCK_ID = "debug:block";
    private static final String[] TEST_IDS = {TEXT_ID, ITEM_ID, BLOCK_ID};

    private DisplayHudDebugCommand() {
    }

    public static void register() {
        SuggestionProvider<CommandSourceStack> ids = (context, builder) ->
                SharedSuggestionProvider.suggest(Arrays.stream(TEST_IDS), builder);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(command(ids))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(SuggestionProvider<CommandSourceStack> ids) {
        return Commands.literal("displayhudtest")
                .requires(source -> source.hasPermission(2))
                .executes(context -> spawnDemo(context.getSource()))
                .then(Commands.literal("demo")
                        .executes(context -> spawnDemo(context.getSource())))
                .then(Commands.literal("text")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> spawnText(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "message")
                                ))))
                .then(Commands.literal("item")
                        .executes(context -> spawnItem(context.getSource(), new ItemStack(Items.DIAMOND_SWORD))))
                .then(Commands.literal("hand")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack stack = player.getMainHandItem().copy();
                            if (stack.isEmpty()) {
                                stack = new ItemStack(Items.DIAMOND);
                            }
                            return spawnItem(context.getSource(), stack);
                        }))
                .then(Commands.literal("block")
                        .executes(context -> spawnBlock(context.getSource())))
                .then(Commands.literal("move")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(ids)
                                .then(Commands.argument("x", FloatArgumentType.floatArg())
                                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                        .executes(context -> moveHud(context.getSource(),
                                                                ResourceLocationArgument.getId(context, "id"),
                                                                FloatArgumentType.getFloat(context, "x"),
                                                                FloatArgumentType.getFloat(context, "y"),
                                                                FloatArgumentType.getFloat(context, "z"),
                                                                0
                                                        ))
                                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                                .executes(context -> moveHud(context.getSource(),
                                                                        ResourceLocationArgument.getId(context, "id"),
                                                                        FloatArgumentType.getFloat(context, "x"),
                                                                        FloatArgumentType.getFloat(context, "y"),
                                                                        FloatArgumentType.getFloat(context, "z"),
                                                                        IntegerArgumentType.getInteger(context, "time")
                                                                ))))))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())));
    }

    private static int spawnDemo(CommandSourceStack source) throws CommandSyntaxException {
        useShaderDefaults();
        clear(source.getPlayerOrException());
        ServerPlayer player = source.getPlayerOrException();

        createText(player, "Polymer DisplayHUD\nText display test", 960, 420, 70);
        createItem(player, new ItemStack(Items.DIAMOND_SWORD), 890, 540, 72);
        createBlock(player, 1030, 540, 72);

        source.sendSuccess(() -> Component.literal("Spawned DisplayHUD demo HUDs."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnText(CommandSourceStack source, String message) throws CommandSyntaxException {
        useShaderDefaults();
        ServerPlayer player = source.getPlayerOrException();
        createText(player, message, 960, 420, 70);
        source.sendSuccess(() -> Component.literal("Spawned text HUD."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnItem(CommandSourceStack source, ItemStack stack) throws CommandSyntaxException {
        useShaderDefaults();
        ServerPlayer player = source.getPlayerOrException();
        createItem(player, stack, 960, 540, 80);
        source.sendSuccess(() -> Component.literal("Spawned item HUD."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnBlock(CommandSourceStack source) throws CommandSyntaxException {
        useShaderDefaults();
        ServerPlayer player = source.getPlayerOrException();
        createBlock(player, 960, 540, 80);
        source.sendSuccess(() -> Component.literal("Spawned block HUD."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int moveHud(CommandSourceStack source, ResourceLocation id, float x, float y, float z, int time) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String key = id.toString();
        DisplayHud hud = DisplayHud.getHud(player, key);
        if (hud == null) {
            source.sendFailure(Component.literal("No test HUD with id '" + key + "'."));
            return 0;
        }

        hud.setLocation(x, y, z, time);
        source.sendSuccess(() -> Component.literal("Moved " + key + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        clear(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Cleared DisplayHUD test HUDs."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void clear(ServerPlayer player) {
        for (String id : TEST_IDS) {
            DisplayHud.removeHud(player, id);
        }
    }

    private static void useShaderDefaults() {
        DisplayHudManager.configure(1f, 1f, 1920f, 1080f, 10_000);
    }

    private static TextDisplayHud createText(ServerPlayer player, String message, float x, float y, float size) {
        DisplayHud.removeHud(player, TEXT_ID);

        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(120);
        hud.setTextAlignment(Display.TextDisplay.Align.CENTER);
        hud.setShadowToggle(true);
        hud.setText(Component.literal(message));
        hud.setScale(size, size, 1);
        hud.setViewRange(1000);
        hud.setLocation(x, y, 0);
        hud.spawn(player, TEXT_ID);
        return hud;
    }

    private static ItemDisplayHud createItem(ServerPlayer player, ItemStack stack, float x, float y, float size) {
        DisplayHud.removeHud(player, ITEM_ID);

        ItemDisplayHud hud = new ItemDisplayHud();
        hud.setItem(stack);
        hud.setItemTransform(ItemDisplayContext.GUI);
        hud.setScale(size, size, 1);
        hud.setViewRange(1000);
        hud.setLocation(x, y, 0);
        hud.spawn(player, ITEM_ID);
        return hud;
    }

    private static BlockDisplayHud createBlock(ServerPlayer player, float x, float y, float size) {
        DisplayHud.removeHud(player, BLOCK_ID);

        BlockDisplayHud hud = new BlockDisplayHud();
        hud.setBlock(Blocks.DIAMOND_BLOCK.defaultBlockState());
        hud.setScale(size, size, size);
        hud.setViewRange(1000);
        hud.setLocation(x, y, 0);
        hud.spawn(player, BLOCK_ID);
        return hud;
    }
}
