package dev.engine_room.flywheel.backend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.GlStateManager;

import dev.engine_room.flywheel.backend.gl.GlStateTracker;
import dev.engine_room.flywheel.backend.gl.buffer.GlBufferType;

/**
 * Tracks the GL state vanilla changes but does not shadow, so the backend can put it back.
 * <p>
 * Upstream also injects into {@code setupLevelDiffuseLighting} to capture the two level light
 * directions before vanilla transforms them into screen space. 26.1 deleted that method: lighting is a
 * uniform buffer now, written once per dimension by {@code Lighting#updateLevel} from constants it
 * keeps private. {@code LevelUniforms} computes the same two vectors from the dimension's own
 * {@code CardinalLighting.Type}, which is the input vanilla selects on, so there is nothing to hook.
 */
@Mixin(value = GlStateManager.class, remap = false)
abstract class GlStateManagerMixin {
	@Inject(method = "_glBindBuffer(II)V", at = @At("RETURN"))
	private static void flywheel$onBindBuffer(int target, int buffer, CallbackInfo ci) {
		GlStateTracker._setBuffer(GlBufferType.fromTarget(target), buffer);
	}

	@Inject(method = "_glBindVertexArray(I)V", at = @At("RETURN"))
	private static void flywheel$onBindVertexArray(int array, CallbackInfo ci) {
		GlStateTracker._setVertexArray(array);
	}

	@Inject(method = "_glUseProgram(I)V", at = @At("RETURN"))
	private static void flywheel$onUseProgram(int program, CallbackInfo ci) {
		GlStateTracker._setProgram(program);
	}
}
