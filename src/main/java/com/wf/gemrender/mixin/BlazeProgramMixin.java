package com.wf.gemrender.mixin;

//? if >=26.2 {
/*import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.wf.gemrender.render.SamplerBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/^* CrankShaft's render-pass programs are vanilla GlPrograms; bind groups name none of GemRender's samplers. ^/
@Mixin(value = GlProgram.class, remap = false)
abstract class BlazeProgramMixin {
    @Inject(method = "setupBindGroupLayouts", at = @At("RETURN"))
    private void gemrender$bindSamplers(List<BindGroupLayout> bindGroupLayouts, CallbackInfo ci) {
        SamplerBindings.apply(((GlProgram) (Object) this).getProgramId());
    }
}
*///?}
