package com.wf.gemrender.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.debug.SamplerProbe;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.FrameCost;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.render.PoseCache;

import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;

@Mixin(value = DrawManager.class, remap = false)
public abstract class DrawManagerMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void gemrender$bindBoneBuffer(LightStorage lightStorage, EnvironmentStorage environmentStorage, CallbackInfo ci) {
		SamplerProbe.sample();
		long uploadStart = System.nanoTime();

		// The palette buffers are bound to units above Minecraft's tracked range and left bound on
		// purpose, so what the audit is watching here is everything else: the array buffer this borrows,
		// and the active unit, which must come back or Minecraft's next _bindTexture lands on the wrong
		// one. See GlAudit.
		GlAudit.Scope audit = GlAudit.open("gemrender:upload");
		try {
			BoneBuffer.getInstance()
					.uploadAndBind();
			MorphBuffer.getInstance()
					.uploadAndBind();
			ParticleBuffer.getInstance()
					.uploadAndBind();
		} finally {
			audit.close();
		}

		FrameCost.getInstance()
				.addUploadNanos(System.nanoTime() - uploadStart);
		PoseCache.getInstance()
				.endFrame();
		FrameCost.getInstance()
				.endFrame();
	}
}
