package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.water.Absorbance;
import com.wf.gemrender.water.WaterSplit;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDrawManager;

@Mixin(value = InstancedDrawManager.class, remap = false)
abstract class InstancedDrawManagerMixin {
	@Shadow
	@Final
	private OitFramebuffer oitFramebuffer;

	@Shadow
	protected abstract void submitOitDraws(PipelineCompiler.OitMode mode);

	@Inject(method = "submitOitDraws", at = @At("HEAD"), cancellable = true)
	private void gemrender$skipCoefficientPasses(PipelineCompiler.OitMode mode, CallbackInfo ci) {
		if (mode != PipelineCompiler.OitMode.EVALUATE && Absorbance.getInstance()
				.exclusive()) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Ldev/engine_room/flywheel/backend/engine/indirect/OitFramebuffer;composite()V"))
	private void gemrender$beforeComposite(LightStorage lightStorage, EnvironmentStorage environmentStorage,
			CallbackInfo ci) {
		WaterSplit.getInstance()
				.beforeOitComposite(oitFramebuffer, () -> submitOitDraws(PipelineCompiler.OitMode.EVALUATE));
	}
}
