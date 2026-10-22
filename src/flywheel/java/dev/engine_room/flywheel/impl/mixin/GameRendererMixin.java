package dev.engine_room.flywheel.impl.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.engine_room.flywheel.impl.event.LevelProjection;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 26.1 multiplies hurt tilt, view bob and the nausea warp into a copy of the camera's projection and
 * draws the level with that copy; the camera render state keeps the plain one. Nothing exposes the copy.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@ModifyArg(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)"
							+ "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private Matrix4f flywheel$captureLevelProjection(Matrix4f projection) {
		LevelProjection.capture(projection);
		return projection;
	}
}
