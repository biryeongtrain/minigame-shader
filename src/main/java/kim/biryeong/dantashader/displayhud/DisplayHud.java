package kim.biryeong.dantashader.displayhud;

import com.mojang.math.Transformation;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DisplayHud {
    public enum HudAlignment {
        CENTER,
        LEFT,
        RIGHT,
        UNALIGNED
    }

    private static final Map<UUID, Map<String, DisplayHud>> HUD_REGISTRY = new ConcurrentHashMap<>();

    public static DisplayHud getHud(ServerPlayer player, String id) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");

        Map<String, DisplayHud> huds = HUD_REGISTRY.get(player.getUUID());
        return huds != null ? huds.get(id) : null;
    }

    public static Map<String, DisplayHud> getHuds(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        Map<String, DisplayHud> huds = HUD_REGISTRY.get(player.getUUID());
        if (huds == null || huds.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(huds);
    }

    public static void removeHud(ServerPlayer player, String id) {
        DisplayHud hud = getHud(player, id);
        if (hud != null) {
            hud.remove();
        }
    }

    public static void clearHuds(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        Map<String, DisplayHud> huds = HUD_REGISTRY.remove(player.getUUID());
        if (huds == null) {
            return;
        }

        for (DisplayHud hud : new HashMap<>(huds).values()) {
            hud.destroyWithoutRegistryUpdate();
        }
    }

    static void reattachAll(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        Map<String, DisplayHud> huds = HUD_REGISTRY.get(oldPlayer.getUUID());
        if (huds == null || huds.isEmpty()) {
            return;
        }

        HUD_REGISTRY.put(newPlayer.getUUID(), huds);
        for (DisplayHud hud : new HashMap<>(huds).values()) {
            hud.reattachPlayer(newPlayer);
        }
    }

    protected ServerPlayer player;
    protected String id;
    protected UUID uuid;

    protected final DisplayElement display;
    protected ElementHolder holder;
    protected OwnerOnlyEntityAttachment attachment;

    protected Vector3f location = new Vector3f(940f, 540f, 0f);
    protected Vector3f scale = new Vector3f(100f, 100f, 1f);
    protected HudAlignment alignment = HudAlignment.CENTER;

    protected boolean removeWhenPlayerDied = false;

    protected final Map<String, Object> extraData = new ConcurrentHashMap<>();

    protected DisplayHud(DisplayElement display) {
        this.display = Objects.requireNonNull(display, "display");
        this.display.ignorePositionUpdates();
        this.display.setNoGravity(true);
        this.display.setTeleportDuration(0);
    }

    public boolean spawn(ServerPlayer player, String id) {
        return spawn(player, id, UUID.randomUUID());
    }

    public boolean spawn(ServerPlayer player, String id, UUID uuid) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");

        if (this.player != null) {
            return false;
        }

        Map<String, DisplayHud> map = HUD_REGISTRY.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        if (map.containsKey(id)) {
            return false;
        }

        map.put(id, this);
        this.player = player;
        this.id = id;
        this.uuid = uuid;
        this.holder = new ElementHolder();
        this.holder.addPassengerElement(this.display);
        this.attachment = new OwnerOnlyEntityAttachment(this.holder, player);

        setLeftRotation(getLeftRotationVector());
        setBrightness(15, 15);
        setScale(this.scale);
        setLocation(this.location);
        this.attachment.enableOwnerWatching();
        update();

        return true;
    }

    public void respawn() {
        if (this.player != null) {
            reattachPlayer(this.player);
        }
    }

    public void remove() {
        if (this.player == null) {
            return;
        }

        Map<String, DisplayHud> huds = HUD_REGISTRY.get(this.player.getUUID());
        if (huds != null) {
            huds.remove(this.id);
            if (huds.isEmpty()) {
                HUD_REGISTRY.remove(this.player.getUUID());
            }
        }

        destroyWithoutRegistryUpdate();
    }

    public void update() {
        if (this.holder != null) {
            this.holder.tick();
        }
    }

    public void teleport() {
        if (this.attachment != null) {
            this.attachment.restartForOwner();
        }
    }

    public void mount() {
        if (this.attachment != null) {
            this.attachment.refreshMount();
        }
    }

    public void removeWhenPlayerDied() {
        this.removeWhenPlayerDied = true;
    }

    public void removeWhenPlayerDied(boolean toggle) {
        this.removeWhenPlayerDied = toggle;
    }

    public boolean isRemovedWhenPlayerDied() {
        return this.removeWhenPlayerDied;
    }

    public ServerPlayer getPlayer() {
        return this.player;
    }

    public String getId() {
        return this.id;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public DisplayElement getDisplay() {
        return this.display;
    }

    public int getEntityId() {
        return this.display.getEntityId();
    }

    public int getNMSid() {
        return getEntityId();
    }

    public void setExtraData(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            this.extraData.remove(key);
        } else {
            this.extraData.put(key, value);
        }
    }

    public Object getExtraData(String key) {
        Objects.requireNonNull(key, "key");
        return this.extraData.get(key);
    }

    public boolean hasExtraData(String key) {
        Objects.requireNonNull(key, "key");
        return this.extraData.containsKey(key);
    }

    public Object removeExtraData(String key) {
        Objects.requireNonNull(key, "key");
        return this.extraData.remove(key);
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

    public void setLocation(Vector3f location, Integer time) {
        this.location = new Vector3f(location);
        Vector3f translated = getLocationVector();
        translated.y -= getAlignmentInt();

        this.display.setTranslation(translated);
        this.display.setStartInterpolation(0);
        this.display.setInterpolationDuration(time);
        update();
    }

    public Vector3f getLocation() {
        return new Vector3f(this.location);
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

    public void setScale(Vector3f scale, Integer time) {
        this.scale = new Vector3f(scale);
        this.display.setScale(getScaleVector());
        this.display.setStartInterpolation(0);
        this.display.setInterpolationDuration(time);
        update();
    }

    public Vector3f getScale() {
        return new Vector3f(this.scale);
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

    public void setLeftRotation(Vector3f vector, Integer time) {
        this.display.setLeftRotation(vecToQuat(vector));
        this.display.setStartInterpolation(0);
        this.display.setInterpolationDuration(time);
        update();
    }

    public Quaternionf getLeftRotation() {
        return new Quaternionf(this.display.getLeftRotation());
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

    public void setRightRotation(Vector3f vector, Integer time) {
        this.display.setRightRotation(vecToQuat(vector));
        this.display.setStartInterpolation(0);
        this.display.setInterpolationDuration(time);
        update();
    }

    public Quaternionf getRightRotation() {
        return new Quaternionf(this.display.getRightRotation());
    }

    public Vector3f getRightRotationVector() {
        return quatToVec(getRightRotation());
    }

    public void setTransformation(Transformation transformation) {
        this.display.setTransformation(transformation);
        update();
    }

    public void setAlignment(HudAlignment alignment) {
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        setLocation(this.location, 0);
    }

    public HudAlignment getAlignment() {
        return this.alignment;
    }

    public int getAlignmentInt() {
        int gap = DisplayHudManager.alignmentGap;
        return switch (this.alignment) {
            case LEFT -> gap;
            case CENTER -> gap * 2;
            case RIGHT -> gap * 3;
            case UNALIGNED -> 0;
        };
    }

    public void setInterpolationDuration(Integer duration) {
        this.display.setInterpolationDuration(duration);
        update();
    }

    public int getInterpolationDuration() {
        return this.display.getInterpolationDuration();
    }

    public void setInterpolationDelay(Integer delay) {
        this.display.setStartInterpolation(delay);
        update();
    }

    public int getInterpolationDelay() {
        return this.display.getStartInterpolation();
    }

    public void setBrightness(int block, int sky) {
        this.display.setBrightness(new Brightness(block, sky));
        update();
    }

    public int getBrightnessBlock() {
        Brightness brightness = this.display.getBrightness();
        return brightness != null ? brightness.block() : -1;
    }

    public int getBrightnessSky() {
        Brightness brightness = this.display.getBrightness();
        return brightness != null ? brightness.sky() : -1;
    }

    public void setHeight(float height) {
        this.display.setDisplayHeight(height);
        update();
    }

    public float getHeight() {
        return this.display.getDisplayHeight();
    }

    public void setWidth(float width) {
        this.display.setDisplayWidth(width);
        update();
    }

    public float getWidth() {
        return this.display.getDisplayWidth();
    }

    public void setGlowColorOverride(int color) {
        this.display.setGlowColorOverride(color);
        update();
    }

    public int getGlowColorOverride() {
        return this.display.getGlowColorOverride();
    }

    public void setViewRange(float viewRange) {
        this.display.setViewRange(viewRange);
        update();
    }

    public float getViewRange() {
        return this.display.getViewRange();
    }

    public Vector3f getLocationVector() {
        return new Vector3f(this.location);
    }

    public Vector3f getScaleVector() {
        float unitX = DisplayHudManager.unitX;
        float unitY = DisplayHudManager.unitY;
        return new Vector3f(this.scale.x * unitX, this.scale.y * unitY, this.scale.z * unitX);
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

    private void reattachPlayer(ServerPlayer newPlayer) {
        if (this.holder == null) {
            return;
        }

        if (this.player != null) {
            this.holder.stopWatching(this.player);
        }
        if (this.attachment != null) {
            this.attachment.destroy();
        }

        this.player = newPlayer;
        this.attachment = new OwnerOnlyEntityAttachment(this.holder, newPlayer);
        this.attachment.enableOwnerWatching();
        update();
    }

    private void destroyWithoutRegistryUpdate() {
        if (this.holder != null) {
            this.holder.destroy();
        }
        this.attachment = null;
        this.holder = null;
        this.player = null;
        this.id = null;
    }
}
