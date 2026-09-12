package dev.engine_room.flywheel.backend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.engine_room.flywheel.backend.gl.GlCompat;

/**
 * 26.1 changed {@code initRenderer} to take the {@code GpuDevice} rather than a debug level and a
 * boolean.
 * <p>
 * Upstream also injects into {@code setShaderFogStart/End/Shape/Color} to refresh Flywheel's fog
 * uniforms. None of those methods exist any more -- vanilla packs the fog into a UBO -- so
 * {@code FogUniforms} listens for NeoForge's {@code ViewportEvent.RenderFog} instead and there is
 * nothing to hook here.
 */
@Mixin(value = RenderSystem.class, remap = false)
abstract class RenderSystemMixin {
	@Inject(method = "initRenderer(Lcom/mojang/blaze3d/systems/GpuDevice;)V", at = @At("RETURN"))
	private static void flywheel$onInitRenderer(CallbackInfo ci) {
		GlCompat.init();
	}
}
