package kim.biryeong.minigamelib.cinematic;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import kim.biryeong.minigamelib.mixin.ChunkMapAccessor;
import kim.biryeong.minigamelib.mixin.EntityDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class CameraViewSession implements AutoCloseable {
    private final ServerPlayer player;
    private final Float previousFov;
    private final Display.ItemDisplay soundAnchor;
    private final ElementHolder holder = new ElementHolder();
    private final ItemDisplayElement camera = new ItemDisplayElement();
    private final eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement effect = new eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement();
    private final Set<Long> desired = new HashSet<>(), queued = new HashSet<>();
    private final Map<Integer, Entity> entities = new HashMap<>();
    private PlayerChunkSender chunks = new PlayerChunkSender(false);
    private CameraViews.Region region;
    private ServerLevel level;
    private Vec3 position;
    private long loadingSince;
    private boolean ready, closed, autoReveal = true;
    private String failure;
    private int teleportId = Integer.MIN_VALUE;
    private float yaw, pitch;

    CameraViewSession(ServerPlayer player) {
        this.player = player;
        previousFov = kim.biryeong.minigamelib.camera.api.CameraEffects.getFov(player);
        soundAnchor = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, player.level());
        soundAnchor.setId(camera.getEntityId());
        camera.setTeleportDuration(1);
        camera.setNoGravity(true);
        holder.addElement(camera);
        effect.setBackground(0);
        effect.setBillboardMode(Display.BillboardConstraints.CENTER);
        effect.setBrightness(new net.minecraft.util.Brightness(15,15));
        effect.setViewRange(16);
        holder.addElement(effect);
    }
    public ServerPlayer player() { return player; }
    public ServerLevel level() { return level; }
    public Vec3 position() { return position; }
    public PlayerChunkSender chunks() { return chunks; }
    public boolean ready() { return ready && !closed; }
    public boolean closed() { return closed; }
    public int cameraId() { return camera.getEntityId(); }
    public Entity soundAnchor() { return soundAnchor; }
    public String failure() { return failure; }
    public void holdCurtain() { autoReveal = false; }
    public boolean tracks(ChunkPos pos) { return queued.contains(pos.toLong()) && !chunks.isPending(pos.toLong()); }
    public void send(Packet<?> packet) { CameraViews.send(player, packet); }

    public void move(ServerLevel nextLevel, Vec3 pos, float yaw, float pitch, boolean cut) {
        if (closed) throw new IllegalStateException("View is closed");
        this.position = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        boolean dimensionChange = level != nextLevel;
        if (dimensionChange) {
            CameraViews.sending(() -> holder.stopWatching(player));
            CameraViews.release(region);
            region = null;
            desired.clear(); queued.clear(); entities.clear();
            chunks = new PlayerChunkSender(false);
            level = nextLevel;
            new eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment(holder, level, () -> position);
            var info = player.createCommonSpawnInfo(level);
            var spectator = new CommonPlayerSpawnInfo(info.dimensionType(), info.dimension(), info.seed(),
                    GameType.SPECTATOR, info.gameType(), info.isDebug(), info.isFlat(), info.lastDeathLocation(), info.portalCooldown(), info.seaLevel());
            send(new ClientboundRespawnPacket(spectator, ClientboundRespawnPacket.KEEP_ALL_DATA));
            var abilities = new net.minecraft.world.entity.player.Abilities();
            abilities.invulnerable = true; abilities.flying = true; abilities.mayfly = true;
            send(new ClientboundPlayerAbilitiesPacket(abilities));
            send(new ClientboundSetDefaultSpawnPositionPacket(BlockPos.containing(pos), yaw));
            send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0));
            environment();
        }
        if (dimensionChange || cut) {
            ready = false;
            loadingSince = System.nanoTime();
            send(new ClientboundPlayerPositionPacket(teleportId++, new PositionMoveRotation(pos, Vec3.ZERO, yaw, pitch), Set.of()));
        }
        camera.setTeleportDuration(cut ? 0 : 1);
        camera.setYaw(yaw); camera.setPitch(pitch);
        effect.setOffset(Vec3.directionFromRotation(pitch,yaw).scale(2));
        CameraViews.sending(holder::tick);
        updateRegion();
        if (dimensionChange) CameraViews.sending(() -> holder.startWatching(player));
        if (dimensionChange || cut) {
            send(new ClientboundSetCameraPacket(soundAnchor));
            curtain(true);
        }
    }

    private void updateRegion() {
        int radius = Math.max(2, Math.min(player.requestedViewDistance(), player.getServer().getPlayerList().getViewDistance()));
        var center = new ChunkPos(BlockPos.containing(position));
        var next = new CameraViews.Region(level, center, radius + 1);
        if (next.equals(region)) return;
        CameraViews.acquire(next);
        CameraViews.release(region);
        region = next;
        Set<Long> nextChunks = new HashSet<>();
        for (int x = center.x - radius; x <= center.x + radius; x++)
            for (int z = center.z - radius; z <= center.z + radius; z++) nextChunks.add(ChunkPos.asLong(x, z));
        for (long old : desired) if (!nextChunks.contains(old)) {
            CameraViews.sending(() -> chunks.dropChunk(player, new ChunkPos(old)));
            queued.remove(old);
        }
        desired.clear(); desired.addAll(nextChunks);
        send(new ClientboundSetChunkCacheRadiusPacket(radius));
        send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
    }

    public void tick() {
        if (closed) return;
        if (player.hasDisconnected() || player.isDeadOrDying()) { close(); return; }
        for (long id : desired) if (!queued.contains(id)) {
            var chunk = level.getChunkSource().chunkMap.getChunkToSend(id);
            if (chunk != null) { chunks.markChunkPendingToSend(chunk); queued.add(id); }
        }
        CameraViews.sending(() -> chunks.sendNextChunks(player));
        if (!ready) {
            var center = new ChunkPos(BlockPos.containing(position));
            ready = true;
            for (int x = center.x - 1; x <= center.x + 1; x++)
                for (int z = center.z - 1; z <= center.z + 1; z++) ready &= tracks(new ChunkPos(x, z));
            ready &= ((kim.biryeong.minigamelib.mixin.ChunkSenderAccessor)chunks).minigamelib$unacknowledged() == 0;
            if (!ready && System.nanoTime() - loadingSince > 15_000_000_000L) {
                failure = "시네마틱 청크 준비 시간이 초과되었습니다.";
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(failure));
                close(); return;
            }
            if (ready && autoReveal) curtain(false);
        }
        updateEntities();
        if (player.getServer().getTickCount() % 20 == 0) environment();
    }

    private void updateEntities() {
        // ponytail: per-view entity scan; share snapshots by scene if audience size makes this measurable.
        double r = region.radius() * 16.0;
        var area = new AABB(position.x - r, level.getMinY(), position.z - r,
                position.x + r, level.getMaxY(), position.z + r);
        Set<Integer> visible = new HashSet<>();
        for (var entity : level.getEntities((Entity) null, area, e -> e != player && !e.isRemoved() && e.broadcastToPlayer(player))) {
            if (!tracks(entity.chunkPosition())) continue;
            visible.add(entity.getId());
            if (!entities.containsKey(entity.getId())) {
                new ServerEntity(level, entity, 1, false, this::send, (p, ignored) -> send(p)).sendPairingData(player, this::send);
                entities.put(entity.getId(), entity);
            }
            send(ClientboundEntityPositionSyncPacket.of(entity));
            var data = Arrays.stream(((EntityDataAccessor) entity.getEntityData()).minigamelib$items())
                    .<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>map(net.minecraft.network.syncher.SynchedEntityData.DataItem::value).toList();
            send(new ClientboundSetEntityDataPacket(entity.getId(), data));
            if (entity instanceof LivingEntity living) {
                send(new ClientboundRotateHeadPacket(entity, (byte) (living.getYHeadRot() * 256 / 360)));
                var equipment = new ArrayList<com.mojang.datafixers.util.Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>>();
                for (var slot : EquipmentSlot.values()) equipment.add(com.mojang.datafixers.util.Pair.of(slot, living.getItemBySlot(slot).copy()));
                send(new ClientboundSetEquipmentPacket(entity.getId(), equipment));
            }
        }
        for (int id : List.copyOf(entities.keySet())) if (!visible.contains(id)) {
            send(new ClientboundRemoveEntitiesPacket(id)); entities.remove(id);
        }
    }

    public void environment() {
        kim.biryeong.minigamelib.camera.api.CameraEffects.sync(player);
        send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
        send(new ClientboundGameEventPacket(level.isRaining() ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1)));
        send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1)));
    }

    public void visualEffect(net.minecraft.network.chat.Component text, int opacity) {
        effect.setText(text);
        // Native text opacity is also the ShaderFX vertex alpha; rotation remains native entity rotation.
        effect.setTextOpacity((byte)Math.max(0,Math.min(255,opacity)));
        CameraViews.sending(holder::tick);
    }

    public void curtain(boolean visible) {
        if (visible) {
            send(new ClientboundSetTitlesAnimationPacket(0, 400, 0));
            send(new ClientboundSetTitleTextPacket(kim.biryeong.minigamelib.shaderfx.api.ShaderEffects.effectComponent(
                    kim.biryeong.minigamelib.shaderfx.api.ShaderEffects.FADE.location(), 0)));
        } else send(new ClientboundClearTitlesPacket(true));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CameraViews.remove(this);
        try {
            CameraViews.sending(holder::destroy);
            if (!player.hasDisconnected()) {
                for (int id : entities.keySet()) send(new ClientboundRemoveEntitiesPacket(id));
                for (long id : queued) send(new ClientboundForgetLevelChunkPacket(new ChunkPos(id)));
                send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(player.level()), ClientboundRespawnPacket.KEEP_ALL_DATA));
                send(new ClientboundSetCameraPacket(player));
                player.getServer().getPlayerList().sendLevelInfo(player, player.level());
                player.onUpdateAbilities();
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                player.setChunkTrackingView(ChunkTrackingView.EMPTY);
                ((ChunkMapAccessor) player.level().getChunkSource().chunkMap).minigamelib$updateTracking(player);
                player.inventoryMenu.sendAllDataToRemote();
                restoreEntities();
                if (previousFov == null) kim.biryeong.minigamelib.camera.api.CameraEffects.resetFov(player);
                else kim.biryeong.minigamelib.camera.api.CameraEffects.setFov(player, previousFov);
                curtain(false);
            }
        } finally {
            CameraViews.release(region);
            desired.clear(); queued.clear(); entities.clear();
        }
    }

    private void restoreEntities() {
        var world = player.level();
        double radius = Math.min(player.requestedViewDistance(), player.getServer().getPlayerList().getViewDistance()) * 16.0;
        var body = player.position();
        for (var entity : world.getEntities((Entity)null, new AABB(body.x-radius,world.getMinY(),body.z-radius,body.x+radius,world.getMaxY(),body.z+radius),
                e -> e != player && !e.isRemoved() && e.broadcastToPlayer(player))) {
            send(new ClientboundRemoveEntitiesPacket(entity.getId()));
            new ServerEntity(world,entity,1,false,this::send,(p,ids)->send(p)).sendPairingData(player,this::send);
        }
        for (var other : List.copyOf(((eu.pb4.polymer.virtualentity.impl.HolderHolder)player.connection).polymer$getHolders())) {
            if (other.getAttachment() != null && other.getAttachment().getWorld() == world) {
                CameraViews.sending(() -> { other.stopWatching(player); other.startWatching(player); });
            }
        }
        kim.biryeong.minigamelib.rgbutils.impl.RgbMapDebugCommand.restore(player);
    }
}
