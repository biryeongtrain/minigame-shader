package kim.biryeong.minigamelib.mixin;

import eu.pb4.polymer.resourcepack.api.OutputGenerator;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackCreator;
import eu.pb4.polymer.resourcepack.api.ResourcePackStatusConsumer;
import kim.biryeong.minigamelib.music.MusicPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/** Tracks builds of Polymer's main pack (26.x: {@code ResourcePackCreator#build(Path, ResourcePackStatusConsumer)}). */
@Mixin(value = ResourcePackCreator.class, remap = false)
public abstract class MusicBuildMixin {
    @Inject(method = "build(Ljava/nio/file/Path;Leu/pb4/polymer/resourcepack/api/ResourcePackStatusConsumer;)Leu/pb4/polymer/resourcepack/api/OutputGenerator$Result;", at = @At("HEAD"))
    private void minigamelib$begin(Path output, ResourcePackStatusConsumer status, CallbackInfoReturnable<OutputGenerator.Result> ci) {
        if ((Object) this == PolymerResourcePackUtils.getInstance()) MusicPacks.begin();
    }

    @Inject(method = "build(Ljava/nio/file/Path;Leu/pb4/polymer/resourcepack/api/ResourcePackStatusConsumer;)Leu/pb4/polymer/resourcepack/api/OutputGenerator$Result;", at = @At("RETURN"))
    private void minigamelib$finish(Path output, ResourcePackStatusConsumer status, CallbackInfoReturnable<OutputGenerator.Result> ci) {
        if ((Object) this != PolymerResourcePackUtils.getInstance()) return;
        var result = ci.getReturnValue();
        MusicPacks.finish(result != null ? result.path() : output, result != null && !result.hadIssues());
    }
}
