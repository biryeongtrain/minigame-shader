package kim.biryeong.dantashader.shaderfx.api;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kim.biryeong.dantashader.DantaShaderInitializer.MODID;
import static kim.biryeong.dantashader.shaderfx.api.FileUtil.loadSnippet;

public class ShaderEffects {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(MODID, "fx");
    public static final ResourceLocation FONT_TRANSITIONS = ResourceLocation.fromNamespaceAndPath(MODID, "transition");
    public static final ResourceLocation FONT_LOCAL = ResourceLocation.fromNamespaceAndPath(MODID, "fx_local");

    public static final Map<ResourceLocation, ShaderEffect> EFFECTS = new Object2ObjectArrayMap<>();
    public static final Map<ResourceLocation, ImageTransition> TRANSITIONS = new Object2ObjectArrayMap<>();

    public static final List<ResourceLocation> IMPORTS = new ArrayList<>();

    public static void addImport(ResourceLocation mojimport) {
        IMPORTS.add(mojimport);
    }

    public static MutableComponent transitionComponent(ResourceLocation id, int color) {
        return Component.literal(TRANSITIONS.get(id).character()).withStyle(Style.EMPTY.withFont(FONT_TRANSITIONS).withColor(color).withShadowColor(0));
    }

    public static MutableComponent transitionComponent(ResourceLocation id) {
        return transitionComponent(id, 0xFF_FF_FF);
    }

    public static MutableComponent effectComponent(ResourceLocation effectId, int color) {
        return Component.literal(EFFECTS.get(effectId).asChar()).withStyle(Style.EMPTY.withFont(FONT).withColor(color).withShadowColor(0));
    }

    public static MutableComponent effectComponentLocal(ResourceLocation effectId, int color) {
        return Component.literal(EFFECTS.get(effectId).asChar()).withStyle(Style.EMPTY.withFont(FONT_LOCAL).withColor(color).withShadowColor(0));
    }

    public static MutableComponent effectComponentLocal(ResourceLocation effectId) {
        return effectComponentLocal(effectId, 0xFF_FF_FF);
    }

    public static MutableComponent effectComponent(ResourceLocation effectId) {
        return effectComponent(effectId, 0xFF_FF_FF);
    }

    public static ShaderEffect effect(ResourceLocation resourceLocation) {
        return EFFECTS.get(resourceLocation);
    }

    public static ShaderEffect register(ResourceLocation id, String snippet, boolean addFont) {
        ShaderEffect effect = new ShaderEffect(id, EFFECTS.size() + 1, snippet);
        EFFECTS.put(id, effect);
        return effect;
    }

    public static ImageTransition registerTransition(ResourceLocation id, String character) {
        ImageTransition effect = new ImageTransition(id, character);
        TRANSITIONS.put(id, effect);
        return effect;
    }

    public static ShaderEffect register(ResourceLocation id, String snippet) {
        return register(id, snippet, true);
    }

    public static final ShaderEffect CIRCLE = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "circle"), loadSnippet("circle.glsl"));
    public static final ShaderEffect FADE = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "fade"), "color = vec4(vertexColor.rgb, vertexColor.a);");
    public static final ShaderEffect DIRECTIONAL_GRID = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "directional_grid"), loadSnippet("dir_grid_impl.glsl"));
    public static final ShaderEffect NOISE_GRID = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "noise_grid"), "ivec2 grid = ivec2(gl_FragCoord.xy / 32) * 32; color = (abs(hash(grid.x ^ hash(grid.y)) % 0x100) + 10 < int(vertexColor.a * (length(grid / ScreenSize.xy - 0.5) * 2 + 1) * 0x100)) ? vec4(vertexColor.rgb, 1) : vec4(0);");
    public static final ShaderEffect FRACTAL1 = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "fractal1"), "color = fractal1();");
    public static final ShaderEffect FRACTAL2 = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "fractal2"), "color = fractal2();");
    public static final ShaderEffect APERTURE = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "aperture"), loadSnippet("rot_impl.glsl"), false);
    public static final ShaderEffect SPIKE = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "spike"), "color = spikes(vertexColor, centerUV, 0.09);");
    public static final ShaderEffect VIGNETTE = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "vignette"), "color = vec4(vertexColor.rgb, clamp(length(centerUV * vec2(0.8, 0.5 / (1 - vertexColor.a))) - 0.6, 0, 1));");
    public static final ShaderEffect IMAGE_TRANSITION = ShaderEffects.register(ResourceLocation.fromNamespaceAndPath(MODID, "image_transition"), "float mask = texture(Sampler0, centerUV-0.5*vec2(1,-1)).r; color = vec4(vertexColor.rgb, step(mask, vertexColor.a));", false);

    public static final ImageTransition TRANSITION_HBARS = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-hbars"), "A");
    public static final ImageTransition TRANSITION_MECHADOOR = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-mechadoor"), "B");
    public static final ImageTransition TRANSITION_MOTION_PIXEL = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-motion-pixel"), "C");
    public static final ImageTransition TRANSITION_NOISE = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-noise"), "D");
    public static final ImageTransition TRANSITION_PIXEL = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-pixel"), "E");
    public static final ImageTransition TRANSITION_PIXEL_SWIRL = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-pixel-swirl"), "F");
    public static final ImageTransition TRANSITION_SLASHES = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-slashes"), "G");
    public static final ImageTransition TRANSITION_STRIPES = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-stripes"), "H");
    public static final ImageTransition TRANSITION_SWIRL = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-swirl"), "I");
    public static final ImageTransition TRANSITION_VBARS = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-vbars"), "J");
    public static final ImageTransition TRANSITION_GRID = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-grid"), "K");
    public static final ImageTransition TRANSITION_CHESS_THEN_CIRCLES = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-chess-then-circles"), "L");
    public static final ImageTransition TRANSITION_CIRCLES_THEN_CHESS_THEN_CIRCLES = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-circles-then-chess-then-circles"), "M");
    public static final ImageTransition TRANSITION_CRASHING_WAVE = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-crashing-wave"), "N");
    public static final ImageTransition TRANSITION_ENCLOSING_TRIANGLES = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-enclosing-triangles"), "O");
    public static final ImageTransition TRANSITION_GOOEY = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-gooey"), "P");
    public static final ImageTransition TRANSITION_POKE_ARENA = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-poke-arena"), "Q");
    public static final ImageTransition TRANSITION_SPINNING_SPIRAL = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-spinning-spiral"), "R");
    public static final ImageTransition TRANSITION_TRAPPED = ShaderEffects.registerTransition(ResourceLocation.fromNamespaceAndPath(MODID, "transition-trapped"), "S");
}
