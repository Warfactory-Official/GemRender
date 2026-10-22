package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.gl.shader.ShaderLoader", remap = false)
abstract class VoxyShaderLoaderMixin {
    @Inject(method = "parse", at = @At("RETURN"), cancellable = true)
    private static void gemrender$patch(String id, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(LightShaders.voxy(id, cir.getReturnValue()));
    }
}
