package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.water.WaterSplit;

import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;

@Mixin(value = OitFramebuffer.class, remap = false)
abstract class OitFramebufferMixin {
	@Inject(method = "composite", at = @At("HEAD"), cancellable = true)
	private void gemrender$splitComposite(CallbackInfo ci) {
		if (WaterSplit.getInstance()
				.compositeInstead((OitFramebuffer) (Object) this)) {
			ci.cancel();
		}
	}
}
