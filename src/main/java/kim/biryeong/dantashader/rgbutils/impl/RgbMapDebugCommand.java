package kim.biryeong.dantashader.rgbutils.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.mapcanvas.api.core.CombinedPlayerCanvas;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.mapcanvas.api.utils.VirtualDisplay;
import kim.biryeong.dantashader.rgbutils.api.RgbMapCanvasAdapter;
import kim.biryeong.dantashader.rgbutils.api.RgbMapCodec;
import kim.biryeong.dantashader.rgbutils.api.RgbMapCombinedCanvasAdapter;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RgbMapDebugCommand {
    private static final String DEBUG_IMAGE_RESOURCE = "img_2.png";
    private static final double DISPLAY_PICK_DISTANCE = 32.0D;
    private static final int DISPLAY_FALLBACK_DISTANCE = 3;
    private static final int DISPLAY_GAP = 1;

    private static final Map<UUID, ActiveDebugDisplay> ACTIVE_DISPLAYS = new ConcurrentHashMap<>();
    private static final RgbMapCodec CODEC = RgbMapCodec.createDefault();

    private RgbMapDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("rgbmapdebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))
        ));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> destroyExisting(handler.getPlayer().getUUID()));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        destroyExisting(player.getUUID());

        BufferedImage sourceImage;
        try {
            sourceImage = loadDebugImage();
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }

        CombinedPlayerCanvas rawCanvas = createRawCanvas(sourceImage);
        CombinedPlayerCanvas encodedCanvas = RgbMapCombinedCanvasAdapter.encodeImageToRgbMapCombinedCanvas(sourceImage, CODEC);
        DisplayPlacement placement = resolveDisplayPlacement(player);

        int rawSectionsX = sectionsFor(sourceImage.getWidth(), RgbMapCodec.MAP_WIDTH);
        int rawSectionsY = sectionsFor(sourceImage.getHeight(), RgbMapCodec.MAP_HEIGHT);
        int encodedSectionsX = encodedCanvas.getSectionsWidth();
        int encodedSectionsY = encodedCanvas.getSectionsHeight();

        BlockPos encodedPos = placement.pos().relative(sideDirection(placement.direction()), rawSectionsX + DISPLAY_GAP);

        rawCanvas.addPlayer(player);
        rawCanvas.sendUpdates();
        encodedCanvas.addPlayer(player);
        encodedCanvas.sendUpdates();

        VirtualDisplay rawDisplay = VirtualDisplay.builder(rawCanvas, placement.pos(), placement.direction()).glowing().invisible().build();
        VirtualDisplay encodedDisplay = VirtualDisplay.builder(encodedCanvas, encodedPos, placement.direction()).glowing().invisible().build();
        rawDisplay.addPlayer(player);
        encodedDisplay.addPlayer(player);

        ACTIVE_DISPLAYS.put(player.getUUID(), new ActiveDebugDisplay(rawCanvas, encodedCanvas, rawDisplay, encodedDisplay));

        source.sendSuccess(
                () -> Component.literal(
                        "rgbmapdebug displays created: image="
                                + sourceImage.getWidth() + "x" + sourceImage.getHeight()
                                + ", rawMaps=" + rawSectionsX + "x" + rawSectionsY
                                + ", encodedMaps=" + encodedSectionsX + "x" + encodedSectionsY
                                + ", facing=" + placement.direction()
                                + ". Use /rgbmapdebug clear to remove."
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean removed = destroyExisting(player.getUUID());

        if (removed) {
            source.sendSuccess(() -> Component.literal("rgbmapdebug display cleared."), false);
        } else {
            source.sendFailure(Component.literal("No active rgbmapdebug display found for this player."));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static boolean destroyExisting(UUID playerUuid) {
        ActiveDebugDisplay previous = ACTIVE_DISPLAYS.remove(playerUuid);
        if (previous == null) {
            return false;
        }
        previous.destroyAll();
        return true;
    }

    private static CombinedPlayerCanvas createRawCanvas(BufferedImage sourceImage) {
        int tilesX = sectionsFor(sourceImage.getWidth(), RgbMapCodec.MAP_WIDTH);
        int tilesY = sectionsFor(sourceImage.getHeight(), RgbMapCodec.MAP_HEIGHT);
        CombinedPlayerCanvas combinedCanvas = DrawableCanvas.create(tilesX, tilesY);

        DrawableCanvas rawImage = RgbMapCanvasAdapter.bufferedImageToDrawableCanvas(sourceImage);
        CanvasUtils.draw(combinedCanvas, 0, 0, rawImage);
        return combinedCanvas;
    }

    private static DisplayPlacement resolveDisplayPlacement(ServerPlayer player) {
        HitResult hitResult = player.pick(DISPLAY_PICK_DISTANCE, 0.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
            Direction direction = blockHitResult.getDirection();
            BlockPos pos = blockHitResult.getBlockPos().relative(direction);
            return new DisplayPlacement(pos, direction);
        }

        Direction lookDirection = player.getDirection();
        BlockPos fallbackPos = player.blockPosition().relative(lookDirection, DISPLAY_FALLBACK_DISTANCE);
        return new DisplayPlacement(fallbackPos, lookDirection.getOpposite());
    }

    private static int sectionsFor(int size, int tileSize) {
        return Math.max(1, (size + tileSize - 1) / tileSize);
    }

    private static Direction sideDirection(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return Direction.EAST;
        }
        return facing.getCounterClockWise();
    }

    private static BufferedImage loadDebugImage() {
        BufferedImage image;
        try (InputStream inputStream = RgbMapDebugCommand.class.getClassLoader().getResourceAsStream(DEBUG_IMAGE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing debug image resource: " + DEBUG_IMAGE_RESOURCE);
            }
            image = ImageIO.read(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read debug image resource: " + DEBUG_IMAGE_RESOURCE, exception);
        }

        if (image == null) {
            throw new IllegalStateException("Unsupported debug image format: " + DEBUG_IMAGE_RESOURCE);
        }

        return image;
    }

    private record ActiveDebugDisplay(
            CombinedPlayerCanvas rawCanvas,
            CombinedPlayerCanvas encodedCanvas,
            VirtualDisplay rawDisplay,
            VirtualDisplay encodedDisplay
    ) {
        private void destroyAll() {
            rawDisplay.destroy();
            encodedDisplay.destroy();
            rawCanvas.destroy();
            encodedCanvas.destroy();
        }
    }

    private record DisplayPlacement(BlockPos pos, Direction direction) {
    }
}
