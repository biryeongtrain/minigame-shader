package kim.biryeong.minigamelib.displayhud;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** Packet helpers for DisplayHud (replaces DisplayHUD's NmsManager/MountListener). */
public final class HudPackets {
    private HudPackets() {
    }

    static void spawn(ServerPlayer viewer, Display display, UUID uuid) {
        Vec3 pos = viewer.position();
        send(viewer, new ClientboundAddEntityPacket(display.getId(), uuid, pos.x, pos.y, pos.z, 0f, 0f,
                display.getType(), 0, Vec3.ZERO, 0d));
    }

    static void remove(ServerPlayer viewer, int entityId) {
        send(viewer, new ClientboundRemoveEntitiesPacket(entityId));
    }

    static void teleport(ServerPlayer viewer, int entityId) {
        send(viewer, ClientboundTeleportEntityPacket.teleport(entityId,
                new PositionMoveRotation(viewer.position(), Vec3.ZERO, 0f, 0f), Set.of(), false));
    }

    static void update(ServerPlayer viewer, int entityId, List<SynchedEntityData.DataValue<?>> metadata) {
        if (metadata.isEmpty()) return;
        send(viewer, new ClientboundSetEntityDataPacket(entityId, metadata));
    }

    /**
     * Re-sends the player's passengers. {@link #withHudPassengers} adds the HUD ids on the way out, so the packet
     * also keeps real passengers and Polymer virtual passengers.
     */
    static void updateMount(ServerPlayer viewer) {
        send(viewer, new ClientboundSetPassengersPacket(viewer));
    }

    private static void send(ServerPlayer viewer, Packet<?> packet) {
        if (viewer.hasDisconnected()) return;
        viewer.connection.send(packet);
    }

    /**
     * Called for every packet sent to {@code receiver}. Passenger packets whose vehicle is the receiver itself get
     * the receiver's HUD entity ids appended, so vanilla mount updates never dismount the HUD.
     */
    public static Packet<?> withHudPassengers(ServerPlayer receiver, Packet<?> packet) {
        if (receiver == null) return packet;
        if (packet instanceof ClientboundSetPassengersPacket passengers) {
            return merge(receiver, passengers);
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            boolean changed = false;
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                if (sub instanceof ClientboundSetPassengersPacket passengers) {
                    ClientboundSetPassengersPacket merged = merge(receiver, passengers);
                    changed |= merged != passengers;
                    out.add(merged);
                } else {
                    out.add(sub);
                }
            }
            return changed ? new ClientboundBundlePacket(out) : packet;
        }
        return packet;
    }

    private static ClientboundSetPassengersPacket merge(ServerPlayer receiver, ClientboundSetPassengersPacket packet) {
        if (packet.getVehicle() != receiver.getId() || !HudRegistry.hasVisibleHuds(receiver.getUUID())) {
            return packet;
        }
        int[] hud = HudRegistry.getVisibleHudIds(receiver.getUUID());
        int[] merged = IntStream.concat(Arrays.stream(packet.getPassengers()), Arrays.stream(hud)).distinct().toArray();
        if (merged.length == packet.getPassengers().length) {
            return packet;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(packet.getVehicle());
            buf.writeVarIntArray(merged);
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }
}
