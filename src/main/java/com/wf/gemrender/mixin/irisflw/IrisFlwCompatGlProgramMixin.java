package com.wf.gemrender.mixin.irisflw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.render.SamplerBindings;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;

@Mixin(targets = "top.leonx.irisflw.flywheel.IrisFlwCompatGlProgram", remap = false)
abstract class IrisFlwCompatGlProgramMixin {
	@Inject(method = "bind", at = @At("TAIL"))
	private void gemrender$bindSamplers(CallbackInfo ci) {
		SamplerBindings.apply((GlProgram) (Object) this);
	}
}
