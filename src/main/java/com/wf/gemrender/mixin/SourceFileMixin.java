package com.wf.gemrender.mixin;

//? if >=26.2 {
/*import com.wf.gemrender.render.CrankShaftShaders;
import dev.engine_room.flywheel.backend.glsl.LoadResult;
import dev.engine_room.flywheel.backend.glsl.SourceFile;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = SourceFile.class, remap = false)
abstract class SourceFileMixin {
    @Inject(method = "parse", at = @At("HEAD"), cancellable = true)
    private static void gemrender$crankShaftShim(Function<ResourceLocation, LoadResult> sourceFinder, ResourceLocation name,
                                                 String stringSource, CallbackInfoReturnable<LoadResult> cir) {
        String patched = CrankShaftShaders.patch(name, stringSource);
        if (patched != stringSource) {
            cir.setReturnValue(SourceFile.parse(sourceFinder, name, patched));
        }
    }
}
*///?}
