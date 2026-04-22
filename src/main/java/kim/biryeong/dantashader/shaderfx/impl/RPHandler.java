package kim.biryeong.dantashader.shaderfx.impl;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.extras.api.format.font.BitmapProvider;
import eu.pb4.polymer.resourcepack.extras.api.format.font.FontAsset;
import kim.biryeong.dantashader.shaderfx.api.FileUtil;
import kim.biryeong.dantashader.shaderfx.api.ShaderEffect;
import kim.biryeong.dantashader.shaderfx.api.ShaderEffects;
import kim.biryeong.dantashader.shaderfx.api.ShaderUtil;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static kim.biryeong.dantashader.DantaShaderInitializer.MODID;

public class RPHandler {
    public static boolean ADD_LOCAL = false;

    public static boolean convertAnimoji = true;
    public static boolean addedAssets = false;

    public static void enableAnimojiConversion() {
        convertAnimoji = true;
    }

    public static void enableAssets() {
        if (addedAssets)
            return;

        addedAssets = true;

        PolymerResourcePackUtils.addModAssets(MODID);

        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            builder.addWriteConverter((path, resource) -> {
                if (convertAnimoji && path.matches(".*/textures/font/.*_animoji[0-9]*\\.png")) {
                    try {
                        Pattern p = Pattern.compile("_animoji([0-9]*)\\.png$");
                        Matcher m = p.matcher(path);
                        int fps = 5;
                        if (m.find() && !m.group(1).isBlank()) {
                            fps = Integer.parseInt(m.group(1));
                        }

                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(resource));
                        image = ShaderUtil.addAnimatedEmojiMarker(image, fps);
                        var out = new ByteArrayOutputStream();
                        ImageIO.write(image, "PNG", out);
                        return out.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (path.matches("assets/shaderfx/textures/font/transition-.*\\.png")) {
                    try {
                        BufferedImage image = ShaderUtil.tintEdges(ImageIO.read(new ByteArrayInputStream(resource)), ShaderEffects.IMAGE_TRANSITION.asFullscreenColor());
                        var out = new ByteArrayOutputStream();
                        ImageIO.write(image, "PNG", out);
                        return out.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return resource;
            });

            String fragmentShader = FileUtil.loadCore("rendertype_text.fsh");
            StringBuilder importList = new StringBuilder();
            for (ResourceLocation location : ShaderEffects.IMPORTS) {
                importList.append(String.format("#moj_import <%s>\n", location));
            }
            fragmentShader = fragmentShader.replace("//%IMPORTS%", importList);

            List<String> chars = new ArrayList<>();
            StringBuilder stringBuilder = new StringBuilder();

            StringBuilder shaderCases = new StringBuilder();
            var img = createFullscreenFontImage(stringBuilder, shaderCases);
            var imgLocal = createLocalEffectFontImage();
            fragmentShader = fragmentShader.replace("//%CASES%", shaderCases);

            chars.add(stringBuilder.toString());

            FontAsset.Builder fontAsset = FontAsset.builder();
            String formatted = String.format("font/%s.png", ShaderEffects.FONT.getPath());
            fontAsset.add(new BitmapProvider(ResourceLocation.fromNamespaceAndPath(MODID, formatted), chars, 0));

            FontAsset.Builder fontAssetLocal = FontAsset.builder();
            String formatted2 = String.format("font/%s_local.png", ShaderEffects.FONT.getPath());
            fontAssetLocal.add(new BitmapProvider(ResourceLocation.fromNamespaceAndPath(MODID, formatted2), chars, 0));

            var out = new ByteArrayOutputStream();
            var outLocal = new ByteArrayOutputStream();
            try {
                ImageIO.write(img, "PNG", out);
                ImageIO.write(imgLocal, "PNG", outLocal);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            builder.addData(String.format("assets/%s/font/%s.json", ShaderEffects.FONT.getNamespace(), ShaderEffects.FONT.getPath()), fontAsset.build().toBytes());
            if (ADD_LOCAL) builder.addData(String.format("assets/%s/font/%s_local.json", ShaderEffects.FONT.getNamespace(), ShaderEffects.FONT.getPath()), fontAssetLocal.build().toBytes());
            builder.addData(String.format("assets/%s/textures/font/%s.png", ShaderEffects.FONT.getNamespace(), ShaderEffects.FONT.getPath()), out.toByteArray());
            if (ADD_LOCAL) builder.addData(String.format("assets/%s/textures/font/%s_local.png", ShaderEffects.FONT.getNamespace(), ShaderEffects.FONT.getPath()), outLocal.toByteArray());
            builder.addData("assets/minecraft/shaders/core/rendertype_text.fsh", fragmentShader.getBytes(StandardCharsets.UTF_8));
        });
    }

    private static @NotNull BufferedImage createFullscreenFontImage(StringBuilder stringBuilder, StringBuilder shaderCases) {
        var img = new BufferedImage(ShaderEffects.EFFECTS.size(), 1, BufferedImage.TYPE_INT_ARGB);
        for (Map.Entry<ResourceLocation, ShaderEffect> entry : ShaderEffects.EFFECTS.entrySet()) {
            var effect = entry.getValue();
            stringBuilder.append(effect.asChar());
            img.setRGB(effect.id() - 1, 0, effect.asFullscreenColor());

            shaderCases.append(FileUtil.wrapCase(effect.snippet(), effect.id()));
        }
        return img;
    }

    private static @NotNull BufferedImage createLocalEffectFontImage() {
        var img = new BufferedImage(ShaderEffects.EFFECTS.size(), 1, BufferedImage.TYPE_INT_ARGB);
        for (Map.Entry<ResourceLocation, ShaderEffect> entry : ShaderEffects.EFFECTS.entrySet()) {
            var effect = entry.getValue();
            img.setRGB(effect.id() - 1, 0, effect.asLocalEffectColor());
        }
        return img;
    }
}
