package kim.biryeong.minigamelib.displayhud;

import com.mojang.math.Transformation;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A display entity fixed to a player's screen by the DisplayHud core shaders.
 * <p>
 * Port of DisplayHUD 2.2 (https://github.com/dorondo0000/DisplayHUD) for Fabric 26.3.
 * The entity is never added to a level: it only exists on the clients that view it, where it rides
 * the viewer's own player entity. A HUD is either personal ({@link #spawn(ServerPlayer, String)})
 * or shared by several players through a {@link GlobalHud}.
 * <p>
 * All methods must be called on the server thread. Instances can only be created while the server is running.
 */
public abstract class DisplayHud {
    /**
     * Above-hand marker brightness (block 1 / sky 2). Must match {@code DISPLAYHUD_ABOVE_HAND_LIGHT} in
     * {@code include/displayhud.glsl}. The HUD shaders ignore brightness, so it has no visible effect.
     */
    public static final int ABOVE_HAND_BLOCK_LIGHT = 1;
    public static final int ABOVE_HAND_SKY_LIGHT = 2;
    /** Legacy above-hand glow colour. The above-hand trigger glows with it and the pack never outlines it. */
    public static final int ABOVE_HAND_GLOW_COLOR = 0x01FEFD;

    public enum HudAlignment {
        CENTER,
        LEFT,
        RIGHT,
        UNALIGNED
    }

    // ---- static registry helpers --------------------------------------------------------------------------------

    public static DisplayHud getHud(ServerPlayer player, String id) {
        return HudRegistry.getPersonalHud(player.getUUID(), id);
    }

    public static Map<String, DisplayHud> getHuds(ServerPlayer player) {
        return HudRegistry.getPersonalHuds(player.getUUID());
    }

    /** Personal HUDs plus the global HUDs shown to this player. */
    public static Collection<DisplayHud> getVisibleHuds(ServerPlayer player) {
        return HudRegistry.getVisibleHuds(player.getUUID());
    }

    /** Entity ids mounted on the player (visible HUDs and the above-hand trigger). */
    public static int[] getVisibleHudIds(ServerPlayer player) {
        return HudRegistry.getVisibleHudIds(player.getUUID());
    }

    public static void mountVisibleHuds(ServerPlayer player) {
        HudPackets.updateMount(player);
    }

    public static void removeHud(ServerPlayer player, String id) {
        DisplayHud hud = getHud(player, id);
        if (hud != null) {
            hud.remove();
        }
    }

    public static void clearHuds(ServerPlayer player) {
        HudRegistry.clearPersonalHuds(player.getUUID());
    }

    private enum HudScope {
        UNSPAWNED,
        PERSONAL,
        GLOBAL,
        REMOVED
    }

    // ---- state --------------------------------------------------------------------------------------------------

    protected final Display display;
    protected UUID owner;
    protected String id;
    protected UUID uuid;

    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private HudScope hudScope = HudScope.UNSPAWNED;
    private GlobalHud<?> globalOwner;
    private boolean defaultsInitialized;
    /** Internal transparent display that keeps the above-hand post effect running. */
    boolean aboveHandTrigger;

    protected boolean aboveHand;
    protected int brightnessBlock = 15;
    protected int brightnessSky = 15;

    protected Vector3f location = new Vector3f(940f, 540f, 0f);
    protected Vector3f scale = new Vector3f(100f, 100f, 1f);
    protected HudAlignment alignment = HudAlignment.CENTER;

    private final Vector3f translation = new Vector3f();
    private final Vector3f scaleVector = new Vector3f(1f, 1f, 1f);
    private final Quaternionf leftRotation = new Quaternionf();
    private final Quaternionf rightRotation = new Quaternionf();

    protected boolean removeWhenPlayerDied;
    protected boolean updateWhenDataChanged = true;

    protected final Map<String, Object> extraData = new ConcurrentHashMap<>();

    protected DisplayHud(Display display) {
        this.display = Objects.requireNonNull(display, "display");
        this.display.setNoGravity(true);
        this.display.setPosRotInterpolationDuration(0);
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------

    public boolean spawn(ServerPlayer player, String id) {
        return spawn(player, id, UUID.randomUUID());
    }

    public boolean spawn(ServerPlayer player, String id, UUID uuid) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");
        if (hudScope != HudScope.UNSPAWNED) {
            return false;
        }
        if (!HudRegistry.registerPersonalHud(player.getUUID(), id, this)) {
            return false;
        }
        this.owner = player.getUUID();
        this.id = id;
        this.uuid = uuid;
        this.hudScope = HudScope.PERSONAL;
        initializeDefaults();
        showTo(player);
        return true;
    }

    /** Re-sends this HUD to every viewer (for example after the client lost its entities). */
    public void respawn() {
        for (ServerPlayer viewer : viewerPlayers()) {
            respawnTo(viewer);
            if (aboveHand) {
                AboveHandTrigger.respawn(viewer);
            }
        }
    }

    public void remove() {
        if (hudScope == HudScope.GLOBAL && globalOwner != null) {
            globalOwner.remove();
            return;
        }
        removePacketsFromViewers();
        if (hudScope == HudScope.PERSONAL && owner != null && id != null) {
            HudRegistry.unregisterPersonalHud(owner, id, this);
        }
        hudScope = HudScope.REMOVED;
    }

    public boolean isRemoved() {
        return hudScope == HudScope.REMOVED;
    }

    /** Sends changed entity data to every viewer. Called automatically unless {@link #updateWhenDataChanged(boolean)} is off. */
    public void update() {
        if (id == null) return;
        List<SynchedEntityData.DataValue<?>> metadata = display.getEntityData().packDirty();
        if (metadata == null || metadata.isEmpty()) return;
        for (ServerPlayer viewer : viewerPlayers()) {
            HudPackets.update(viewer, getEntityId(), metadata);
        }
    }

    public void teleport() {
        for (ServerPlayer viewer : viewerPlayers()) {
            teleportTo(viewer);
        }
    }

    public void mount() {
        for (ServerPlayer viewer : viewerPlayers()) {
            mountTo(viewer);
        }
    }

    boolean attachGlobal(GlobalHud<?> owner, String id, UUID uuid) {
        if (hudScope != HudScope.UNSPAWNED) {
            return false;
        }
        this.id = id;
        this.uuid = uuid;
        this.globalOwner = owner;
        this.hudScope = HudScope.GLOBAL;
        initializeDefaults();
        return true;
    }

    void removeGlobal() {
        removePacketsFromViewers();
        hudScope = HudScope.REMOVED;
    }

    boolean showTo(ServerPlayer viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        initializeDefaults();
        if (!viewers.add(viewer.getUUID())) {
            return false;
        }
        HudPackets.spawn(viewer, display, uuid);
        sendFullUpdate(viewer);
        mountTo(viewer);
        if (!aboveHandTrigger) {
            AboveHandTrigger.refresh(viewer);
        }
        return true;
    }

    boolean hideFrom(ServerPlayer viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!viewers.remove(viewer.getUUID())) {
            return false;
        }
        HudPackets.remove(viewer, getEntityId());
        HudPackets.updateMount(viewer);
        if (!aboveHandTrigger) {
            AboveHandTrigger.refresh(viewer);
        }
        return true;
    }

    /** Drops a viewer that already disconnected without sending packets. */
    void forgetViewer(UUID viewer) {
        viewers.remove(viewer);
    }

    boolean isShownTo(ServerPlayer viewer) {
        return viewers.contains(viewer.getUUID());
    }

    void respawnTo(ServerPlayer viewer) {
        if (!viewers.contains(viewer.getUUID())) return;
        HudPackets.remove(viewer, getEntityId());
        HudPackets.spawn(viewer, display, uuid);
        sendFullUpdate(viewer);
        mountTo(viewer);
    }

    void teleportTo(ServerPlayer viewer) {
        if (!viewers.contains(viewer.getUUID())) return;
        HudPackets.teleport(viewer, getEntityId());
    }

    void mountTo(ServerPlayer viewer) {
        if (!viewers.contains(viewer.getUUID())) return;
        HudPackets.updateMount(viewer);
    }

    boolean isGlobalHud() {
        return hudScope == HudScope.GLOBAL;
    }

    GlobalHud<?> getGlobalOwner() {
        return globalOwner;
    }

    Set<UUID> getViewerIds() {
        return new LinkedHashSet<>(viewers);
    }

    List<ServerPlayer> viewerPlayers() {
        return DisplayHudManager.resolve(viewers);
    }

    private void removePacketsFromViewers() {
        for (ServerPlayer viewer : viewerPlayers()) {
            hideFrom(viewer);
        }
        viewers.clear();
    }

    private void initializeDefaults() {
        if (defaultsInitialized) return;
        boolean update = updateWhenDataChanged;
        updateWhenDataChanged = false;
        setLeftRotation(getLeftRotationVector());
        applyBrightness();
        setScale(scale);
        setLocation(location);
        updateWhenDataChanged = update;
        defaultsInitialized = true;
    }

    private void sendFullUpdate(ServerPlayer viewer) {
        // Clear the dirty flags first so the next update() only carries real changes.
        display.getEntityData().packDirty();
        List<SynchedEntityData.DataValue<?>> metadata = display.getEntityData().getNonDefaultValues();
        if (metadata == null || metadata.isEmpty()) return;
        HudPackets.update(viewer, getEntityId(), metadata);
    }

    protected final void changed() {
        if (updateWhenDataChanged) update();
    }

    // ---- options ------------------------------------------------------------------------------------------------

    public void removeWhenPlayerDied() {
        this.removeWhenPlayerDied = true;
    }

    public void removeWhenPlayerDied(boolean toggle) {
        this.removeWhenPlayerDied = toggle;
    }

    public boolean isRemovedWhenPlayerDied() {
        return removeWhenPlayerDied;
    }

    /** Send every change immediately (default). Turn off to batch several setters and call {@link #update()} once. */
    public void updateWhenDataChanged() {
        this.updateWhenDataChanged = true;
    }

    public void updateWhenDataChanged(boolean toggle) {
        this.updateWhenDataChanged = toggle;
    }

    public boolean isUpdatedWhenDataChanged() {
        return updateWhenDataChanged;
    }

    /** The owner of a personal HUD, or {@code null} for global or unspawned HUDs and offline owners. */
    public ServerPlayer getPlayer() {
        return owner == null ? null : DisplayHudManager.player(owner);
    }

    public UUID getOwnerUuid() {
        return owner;
    }

    public String getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    /** The vanilla display entity backing this HUD. It is never added to a level. */
    public Display getDisplay() {
        return display;
    }

    public int getEntityId() {
        return display.getId();
    }

    public int getNMSid() {
        return getEntityId();
    }

    public void setExtraData(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            extraData.remove(key);
        } else {
            extraData.put(key, value);
        }
    }

    public Object getExtraData(String key) {
        return extraData.get(Objects.requireNonNull(key, "key"));
    }

    public boolean hasExtraData(String key) {
        return extraData.containsKey(Objects.requireNonNull(key, "key"));
    }

    public Object removeExtraData(String key) {
        return extraData.remove(Objects.requireNonNull(key, "key"));
    }

    // ---- transformation -----------------------------------------------------------------------------------------

    private void applyTransformation(int time) {
        display.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(leftRotation),
                new Vector3f(scaleVector), new Quaternionf(rightRotation)));
        display.setTransformationInterpolationDelay(0);
        display.setTransformationInterpolationDuration(time);
        changed();
    }

    public void setLocation(float x, float y, float z) {
        setLocation(new Vector3f(x, y, z), 0);
    }

    public void setLocation(float x, float y, float z, int time) {
        setLocation(new Vector3f(x, y, z), time);
    }

    public void setLocation(Vector3f location) {
        setLocation(location, 0);
    }

    public void setLocation(Vector3f location, int time) {
        this.location = new Vector3f(location);
        Vector3f translated = getLocationVector();
        translated.y -= getAlignmentInt();
        translation.set(translated);
        applyTransformation(time);
    }

    public Vector3f getLocation() {
        return new Vector3f(location);
    }

    public void setScale(float x, float y, float z) {
        setScale(new Vector3f(x, y, z), 0);
    }

    public void setScale(float x, float y, float z, int time) {
        setScale(new Vector3f(x, y, z), time);
    }

    public void setScale(Vector3f scale) {
        setScale(scale, 0);
    }

    public void setScale(Vector3f scale, int time) {
        this.scale = new Vector3f(scale);
        scaleVector.set(getScaleVector());
        applyTransformation(time);
    }

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public void setLeftRotation(float x, float y, float z) {
        setLeftRotation(new Vector3f(x, y, z), 0);
    }

    public void setLeftRotation(float x, float y, float z, int time) {
        setLeftRotation(new Vector3f(x, y, z), time);
    }

    public void setLeftRotation(Vector3f vector) {
        setLeftRotation(vector, 0);
    }

    public void setLeftRotation(Vector3f vector, int time) {
        leftRotation.set(vecToQuat(vector));
        applyTransformation(time);
    }

    public Quaternionf getLeftRotation() {
        return new Quaternionf(leftRotation);
    }

    public Vector3f getLeftRotationVector() {
        return quatToVec(getLeftRotation());
    }

    public void setRightRotation(float x, float y, float z) {
        setRightRotation(new Vector3f(x, y, z), 0);
    }

    public void setRightRotation(float x, float y, float z, int time) {
        setRightRotation(new Vector3f(x, y, z), time);
    }

    public void setRightRotation(Vector3f vector) {
        setRightRotation(vector, 0);
    }

    public void setRightRotation(Vector3f vector, int time) {
        rightRotation.set(vecToQuat(vector));
        applyTransformation(time);
    }

    public Quaternionf getRightRotation() {
        return new Quaternionf(rightRotation);
    }

    public Vector3f getRightRotationVector() {
        return quatToVec(getRightRotation());
    }

    /** Raw transformation. Bypasses the HUD location/scale helpers. */
    public void setTransformation(Transformation transformation) {
        setTransformation(transformation, 0);
    }

    /** Raw transformation interpolated over {@code time} client ticks. */
    public void setTransformation(Transformation transformation, int time) {
        translation.set(transformation.translation());
        leftRotation.set(transformation.leftRotation());
        scaleVector.set(transformation.scale());
        rightRotation.set(transformation.rightRotation());
        applyTransformation(time);
    }

    public void setAlignment(HudAlignment alignment) {
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        setLocation(location, 0);
    }

    public HudAlignment getAlignment() {
        return alignment;
    }

    public int getAlignmentInt() {
        int gap = DisplayHudManager.alignmentGap;
        return switch (alignment) {
            case LEFT -> gap;
            case CENTER -> gap * 2;
            case RIGHT -> gap * 3;
            case UNALIGNED -> 0;
        };
    }

    public void setInterpolationDuration(int duration) {
        display.setTransformationInterpolationDuration(duration);
        changed();
    }

    public int getInterpolationDuration() {
        return display.getTransformationInterpolationDuration();
    }

    public void setInterpolationDelay(int delay) {
        display.setTransformationInterpolationDelay(delay);
        changed();
    }

    public int getInterpolationDelay() {
        return display.getTransformationInterpolationDelay();
    }

    // ---- rendering options --------------------------------------------------------------------------------------

    /**
     * Brightness override. HUD shaders ignore brightness, so this normally has no visible effect (default 15/15).
     * While above-hand is on the marker brightness (1/2) is sent instead and this value is applied when it is turned off.
     */
    public void setBrightness(int block, int sky) {
        brightnessBlock = block;
        brightnessSky = sky;
        applyBrightness();
        changed();
    }

    public int getBrightnessBlock() {
        return brightnessBlock;
    }

    public int getBrightnessSky() {
        return brightnessSky;
    }

    private void applyBrightness() {
        display.setBrightnessOverride(aboveHand
                ? new Brightness(ABOVE_HAND_BLOCK_LIGHT, ABOVE_HAND_SKY_LIGHT)
                : new Brightness(brightnessBlock, brightnessSky));
    }

    /**
     * Draws this HUD above the first-person hand and held item (DisplayHud 2.2). Needs the 2.2 shaders and the
     * {@code entity_outline} post effect shipped in minigame-lib's pack. A transparent trigger display is attached
     * automatically to every viewer that sees at least one above-hand HUD.
     */
    public void setAboveHand(boolean aboveHand) {
        if (aboveHandTrigger || this.aboveHand == aboveHand) return;
        this.aboveHand = aboveHand;
        applyBrightness();
        changed();
        for (ServerPlayer viewer : viewerPlayers()) {
            AboveHandTrigger.refresh(viewer);
        }
    }

    public boolean isAboveHand() {
        return aboveHand;
    }

    /** Vanilla glow outline. Colour: {@link #setGlowColorOverride(int)}. Works together with above-hand. */
    public void setGlowing(boolean glowing) {
        display.setGlowingTag(glowing);
        changed();
    }

    public boolean isGlowing() {
        return display.hasGlowingTag();
    }

    public void setHeight(float height) {
        display.setHeight(height);
        changed();
    }

    public float getHeight() {
        return display.getHeight();
    }

    public void setWidth(float width) {
        display.setWidth(width);
        changed();
    }

    public float getWidth() {
        return display.getWidth();
    }

    public void setGlowColorOverride(int color) {
        display.setGlowColorOverride(color);
        changed();
    }

    public int getGlowColorOverride() {
        return display.getGlowColorOverride();
    }

    public void setViewRange(float viewRange) {
        display.setViewRange(viewRange);
        changed();
    }

    public float getViewRange() {
        return display.getViewRange();
    }

    // ---- maths --------------------------------------------------------------------------------------------------

    /** Translation sent to the client before the alignment offset. Overridden per display type. */
    public Vector3f getLocationVector() {
        return new Vector3f(location);
    }

    public Vector3f getScaleVector() {
        float unitX = DisplayHudManager.unitX;
        float unitY = DisplayHudManager.unitY;
        return new Vector3f(scale.x * unitX, scale.y * unitY, scale.z * unitX);
    }

    /** Shared screen-space to translation conversion used by the item/block/text HUDs. */
    protected Vector3f screenTranslation(double offsetX, double offsetY, double offsetZ) {
        double unitX = DisplayHudManager.unitX;
        double unitY = DisplayHudManager.unitY;
        double screenX = DisplayHudManager.screenX;
        double screenY = DisplayHudManager.screenY;
        int alignmentGap = DisplayHudManager.alignmentGap;
        double newX = ((screenX / 2) - location.x - offsetX) * unitX;
        double newY = -1.5 * alignmentGap - 0.178 + (((screenY / 2) - location.y - offsetY) * unitY);
        double newZ = (unitX * offsetZ) - (location.z * 100);
        return new Vector3f((float) newX, (float) newY, (float) newZ);
    }

    public static Quaternionf vecToQuat(Vector3f vector) {
        return new Quaternionf().rotateZYX(
                (float) Math.toRadians(vector.x),
                (float) Math.toRadians(vector.y),
                (float) Math.toRadians(vector.z)
        );
    }

    public static Vector3f quatToVec(Quaternionf quat) {
        Vector3f eulerRad = new Vector3f();
        quat.getEulerAnglesZYX(eulerRad);
        return new Vector3f(
                (float) Math.toDegrees(eulerRad.x),
                (float) Math.toDegrees(eulerRad.y),
                (float) Math.toDegrees(eulerRad.z)
        );
    }
}
