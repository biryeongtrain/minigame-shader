package kim.biryeong.minigamelib.cinematic;

import io.netty.buffer.Unpooled;
import kim.biryeong.minigamelib.displayhud.DisplayHud;
import kim.biryeong.minigamelib.displayhud.DisplayHudManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Screen-fixed text sprites for one cinematic viewer. While a player watches a {@link CameraViewSession} the client
 * renders from the session's camera entity and the server drops ordinary DisplayHud packets, so these displays ride
 * the camera entity instead of the player. They use the DisplayHud shader encoding (camera-relative y far below
 * -1000), so the shaders in minigame-lib's pack draw them in screen space.
 *
 * <p>Every sprite is see-through text (no depth test or write), drawn in the client's see-through pass after all
 * world geometry and translucent entities. Normal HUD text sorts as "far away" because of its huge translation, is
 * drawn first and writes a near depth, so translucent parts (shade, vignette) would cut entities drawn after them.
 * The see-through pass is sorted far to near by the sprite's translation, so {@code z} (0..255, higher on top) is
 * encoded as a translation depth far larger than any screen offset; the HUD shader clamps that depth.
 *
 * <p>Coordinates are pixels of a 1920x1080 reference screen, origin top-left. A sprite's anchor is the
 * bottom-centre of its text block; {@link Sprite#place} converts a target rectangle for a single glyph.
 * Not thread-safe: use on the server thread.
 */
public final class ScreenOverlay {
    /** Native text display layout: 0.025 blocks per font pixel, lines 10 px apart. */
    public static final float BLOCKS_PER_FONT_PX = 0.025f;
    private final CameraViewSession view;
    private final Map<String, Sprite> sprites = new LinkedHashMap<>();
    private boolean mounted;

    ScreenOverlay(CameraViewSession view) { this.view = view; }

    public CameraViewSession view() { return view; }

    /** Existing sprite or a new hidden one (empty text). */
    public Sprite sprite(String key) {
        var sprite = sprites.get(key);
        if (sprite != null) return sprite;
        sprite = new Sprite(key);
        sprites.put(key, sprite);
        if (mounted) { sprite.spawn(); mount(); }
        return sprite;
    }
    public Sprite get(String key) { return sprites.get(key); }
    public Set<String> keys() { return Collections.unmodifiableSet(sprites.keySet()); }

    public void remove(String key) {
        var sprite = sprites.remove(key);
        if (sprite != null && mounted) { view.send(new ClientboundRemoveEntitiesPacket(sprite.display.getId())); mount(); }
    }
    public void clear() {
        if (mounted && !sprites.isEmpty())
            view.send(new ClientboundRemoveEntitiesPacket(sprites.values().stream().mapToInt(s -> s.display.getId()).toArray()));
        sprites.clear();
        if (mounted) mount();
    }

    /** (Re)sends every sprite. Called after the client world or camera entity was recreated. */
    void attach() {
        mounted = true;
        for (var sprite : sprites.values()) sprite.spawn();
        mount();
    }
    /** Sends changed entity data. */
    public void flush() {
        if (!mounted) return;
        for (var sprite : sprites.values()) {
            var dirty = sprite.display.getEntityData().packDirty();
            if (dirty != null && !dirty.isEmpty()) view.send(new ClientboundSetEntityDataPacket(sprite.display.getId(), dirty));
        }
    }
    void detach() {
        if (mounted && !sprites.isEmpty() && !view.player().hasDisconnected())
            view.send(new ClientboundRemoveEntitiesPacket(sprites.values().stream().mapToInt(s -> s.display.getId()).toArray()));
        mounted = false;
    }
    /** Translation z whose square outweighs every screen offset (|xy| < 5e6), so sorting follows {@code z}. */
    static float layerDepth(float z) { return (256 - Math.clamp(Math.round(z), 0, 255)) * LAYER_STEP; }
    static final float LAYER_STEP = 1e7f;

    int[] ids() { return sprites.values().stream().mapToInt(s -> s.display.getId()).toArray(); }

    private void mount() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(view.cameraId());
            buf.writeVarIntArray(ids());
            view.send(ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    public final class Sprite {
        private final String key;
        private final Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, DisplayHudManager.server().overworld());
        private final UUID uuid = UUID.randomUUID();
        private DisplayHud.HudAlignment alignment = DisplayHud.HudAlignment.UNALIGNED;
        private float x = 960, y = 540, z, scaleX = 4, scaleY = 4;

        private Sprite(String key) {
            this.key = key;
            display.setNoGravity(true);
            display.setBackgroundColor(0);
            display.setFlags(Display.TextDisplay.FLAG_SEE_THROUGH);
            display.setBrightnessOverride(new Brightness(15, 15));
            display.setLineWidth(10_000);
            display.setViewRange(16);
            display.setText(Component.empty());
            apply(0);
        }
        public String key() { return key; }
        public Display.TextDisplay display() { return display; }

        public Sprite text(Component text) { if (!text.equals(display.getText())) display.setText(text); return this; }
        /** Screen area: UNALIGNED stretches with the window, LEFT/CENTER/RIGHT keep a 16:9 area on that side. */
        public Sprite alignment(DisplayHud.HudAlignment alignment) {
            if (this.alignment == alignment) return this;
            this.alignment = alignment; return apply(0);
        }
        /**
         * Text opacity 0..255, interpolated over {@code ticks} together with the transformation. Values below 8 are
         * sent as 8: the font draws alpha 0..3 fully opaque, while the text shader discards anything below ~25.
         */
        public Sprite opacity(int opacity, int ticks) {
            int value = Math.clamp(opacity, 8, 255);
            byte b = (byte) (value > 127 ? value - 256 : value);
            if (display.getTextOpacity() != b) {
                display.setTextOpacity(b);
                display.setTransformationInterpolationDelay(0);
                display.setTransformationInterpolationDuration(ticks);
            }
            return this;
        }
        public Sprite background(int argb) { display.setBackgroundColor(argb); return this; }
        /** Text display flags; see-through is always kept (see the class comment). */
        public Sprite flags(byte flags) { display.setFlags((byte) (flags | Display.TextDisplay.FLAG_SEE_THROUGH)); return this; }
        public Sprite leftAligned(boolean left) { return flags(left ? Display.TextDisplay.FLAG_ALIGN_LEFT : 0); }

        /** Anchor = bottom-centre of the text block in reference pixels; {@code px} = screen pixels per font pixel; higher z draws on top. */
        public Sprite at(float x, float y, float z, float pxX, float pxY, int ticks) {
            if (this.x == x && this.y == y && this.z == z && scaleX == pxX && scaleY == pxY) return this;
            this.x = x; this.y = y; this.z = z; scaleX = pxX; scaleY = pxY;
            return apply(ticks);
        }
        public Sprite at(float x, float y, float z, float px, int ticks) { return at(x, y, z, px, px, ticks); }

        /**
         * Places a one-glyph sprite (bitmap glyph of {@code glyphHeight} font px and {@code aspect} = width/height whose
         * ascent is {@code glyphHeight - 2}, see {@link kim.biryeong.minigamelib.cinematic.dialogue.DialogueAssets}) so it
         * fills the rectangle {@code left, top, width, height}.
         */
        public Sprite place(float left, float top, float width, float height, float z, int glyphHeight, float aspect, int ticks) {
            float ky = height / glyphHeight, kx = width / (glyphHeight * aspect);
            return at(left + width / 2f - 0.5f * kx, top + height, z, kx, ky, ticks);
        }

        private Sprite apply(int ticks) {
            int gap = DisplayHudManager.alignmentGap;
            int shift = switch (alignment) { case UNALIGNED -> 0; case LEFT -> gap; case CENTER -> 2 * gap; case RIGHT -> 3 * gap; };
            float unitX = DisplayHudManager.unitX, unitY = DisplayHudManager.unitY;
            float halfW = DisplayHudManager.screenX / 2, halfH = DisplayHudManager.screenY / 2;
            var translation = new Vector3f((halfW - x) * unitX, (float) (-1.5 * gap - 0.178 + (halfH - y) * unitY) - shift, layerDepth(z));
            // Native text: px = scale * 0.025 / unit, mirrored back by the half turn (see TextDisplayHud).
            var scale = new Vector3f(scaleX * unitX / BLOCKS_PER_FONT_PX, scaleY * unitY / BLOCKS_PER_FONT_PX, unitX / BLOCKS_PER_FONT_PX);
            display.setTransformation(new Transformation(translation, new Quaternionf().rotateY((float) Math.PI), scale, new Quaternionf()));
            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(ticks);
            return this;
        }

        private void spawn() {
            apply(0);
            Vec3 pos = view.position() == null ? Vec3.ZERO : view.position();
            view.send(new ClientboundAddEntityPacket(display.getId(), uuid, pos.x, pos.y, pos.z, 0f, 0f, EntityTypes.TEXT_DISPLAY, 0, Vec3.ZERO, 0d));
            display.getEntityData().packDirty();
            List<SynchedEntityData.DataValue<?>> all = display.getEntityData().getNonDefaultValues();
            if (all != null && !all.isEmpty()) view.send(new ClientboundSetEntityDataPacket(display.getId(), all));
        }
    }
}
