package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.gl.shader.Shader$Builder", remap = false)
abstract class VoxyShaderBuilderMixin {
    @Inject(method = "compileToProgram", at = @At("RETURN"))
    private void gemrender$bind(CallbackInfoReturnable<Integer> cir) {
        LightShaders.bindProgram(cir.getReturnValueI());
    }
}
