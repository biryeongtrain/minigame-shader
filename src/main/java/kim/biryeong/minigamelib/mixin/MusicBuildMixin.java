package kim.biryeong.minigamelib.mixin;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.minigamelib.music.MusicPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(value = PolymerResourcePackUtils.class, remap = false)
public abstract class MusicBuildMixin {
    @Inject(method = "buildMain(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Z", at = @At("HEAD"))
    private static void minigamelib$begin(Path output, Consumer<String> status, CallbackInfoReturnable<Boolean> ci) { MusicPacks.begin(); }
    @Inject(method = "buildMain(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Z", at = @At("RETURN"))
    private static void minigamelib$finish(Path output, Consumer<String> status, CallbackInfoReturnable<Boolean> ci) { MusicPacks.finish(output, ci.getReturnValueZ()); }
}
