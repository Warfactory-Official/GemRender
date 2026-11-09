package com.wf.gemrender.mixin;

import com.wf.gemrender.volume.VolumeShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(targets = "dev.engine_room.flywheel.backend.glsl.ShaderSources$SourceFinder", remap = false)
abstract class VolumeSourcesMixin {
    @ModifyArg(method = "readResource", at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/backend/glsl/SourceFile;parse"), index = 2)
    private String gemrender$volumeDepth(Function<?, ?> loader, ResourceLocation location, String source) {
        return VolumeShaders.patch(location, source);
    }
}
