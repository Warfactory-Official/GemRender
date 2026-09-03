package com.wf.gemrender.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.water.WaterSplit;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectDrawManager;
import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;

@Mixin(value = IndirectDrawManager.class, remap = false)
abstract class IndirectDrawManagerMixin {
	@Shadow
	@Final
	private Map<InstanceType<?>, IndirectCullingGroup<?>> cullingGroups;

	@Shadow
	@Final
	private OitFramebuffer oitFramebuffer;

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Ldev/engine_room/flywheel/backend/engine/indirect/OitFramebuffer;composite()V"))
	private void gemrender$beforeComposite(LightStorage lightStorage, EnvironmentStorage environmentStorage,
			CallbackInfo ci) {
		WaterSplit.getInstance()
				.beforeOitComposite(oitFramebuffer, () -> {
					for (IndirectCullingGroup<?> group : cullingGroups.values()) {
						group.submitTransparent(PipelineCompiler.OitMode.EVALUATE);
					}
				});
	}
}
