package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.render.SamplerBindings;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;

@Mixin(value = GlProgram.class, remap = false)
abstract class GlProgramMixin {
	@Inject(method = "bind", at = @At("TAIL"))
	private void gemrender$bindSamplers(CallbackInfo ci) {
		SamplerBindings.apply((GlProgram) (Object) this);
	}
}
