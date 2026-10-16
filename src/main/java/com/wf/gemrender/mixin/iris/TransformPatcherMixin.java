package com.wf.gemrender.mixin.iris;

import com.wf.gemrender.iris.guest.GuestBuild;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = TransformPatcher.class, remap = false)
abstract class TransformPatcherMixin {
    /** Iris caches what it returned before this runs; the guest copy is a new map. */
    @Inject(method = "patchVanilla", at = @At("RETURN"), cancellable = true)
    private static void gemrender$guest(CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        GuestBuild build = GuestBuild.current();
        if (build != null) {
            cir.setReturnValue(build.transform()
                    .apply(cir.getReturnValue()));
        }
    }
}
