package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.volume.Volumetrics;
import com.wf.gemrender.water.Absorbance;
import com.wf.gemrender.water.WaterSplit;

import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;

@Mixin(value = OitFramebuffer.class, remap = false)
abstract class OitFramebufferMixin {
	@Inject(method = "prepare", at = @At("TAIL"))
	private void gemrender$beginFrame(CallbackInfo ci) {
		Volumetrics.getInstance()
				.beginFrame();
		Absorbance.getInstance()
				.beginFrame();
	}

	@Inject(method = "depthRange", at = @At("HEAD"))
	private void gemrender$leaveEvaluateOnDepthRange(CallbackInfo ci) {
		Absorbance.getInstance()
				.endEvaluate();
	}

	@Inject(method = "renderTransmittance", at = @At("HEAD"))
	private void gemrender$leaveEvaluateOnTransmittance(CallbackInfo ci) {
		Absorbance.getInstance()
				.endEvaluate();
	}

	@Inject(method = "renderDepthFromTransmittance", at = @At("HEAD"), cancellable = true)
	private void gemrender$skipTransmittanceDepth(CallbackInfo ci) {
		if (Absorbance.getInstance()
				.exclusive()) {
			ci.cancel();
		}
	}

	@Inject(method = "accumulate", at = @At("TAIL"))
	private void gemrender$beginEvaluate(CallbackInfo ci) {
		Absorbance.getInstance()
				.beginEvaluate();
	}

	@Inject(method = "composite", at = @At("HEAD"), cancellable = true)
	private void gemrender$splitComposite(CallbackInfo ci) {
		Absorbance.getInstance()
				.endFrame();

		if (WaterSplit.getInstance()
				.compositeInstead((OitFramebuffer) (Object) this)) {
			ci.cancel();
		}
	}
}
