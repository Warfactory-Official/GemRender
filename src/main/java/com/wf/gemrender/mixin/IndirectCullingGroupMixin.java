package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.water.Absorbance;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;

@Mixin(value = IndirectCullingGroup.class, remap = false)
abstract class IndirectCullingGroupMixin {
	@Inject(method = "submitTransparent", at = @At("HEAD"), cancellable = true)
	private void gemrender$skipCoefficientPasses(PipelineCompiler.OitMode mode, CallbackInfo ci) {
		if (mode != PipelineCompiler.OitMode.EVALUATE && Absorbance.getInstance()
				.exclusive()) {
			ci.cancel();
		}
	}
}
