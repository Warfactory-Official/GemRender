package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.water.Absorbance;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;

@Mixin(value = MaterialRenderState.class, remap = false)
abstract class MaterialRenderStateMixin {
	@Inject(method = "setupOit", at = @At("HEAD"))
	private static void gemrender$observeOitMaterial(Material material, CallbackInfo ci) {
		Absorbance.getInstance()
				.observe(material);
	}
}
