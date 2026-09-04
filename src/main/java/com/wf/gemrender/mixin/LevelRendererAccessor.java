package com.wf.gemrender.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Invoker("renderSectionLayer")
	void gemrender$renderSectionLayer(RenderType renderType, double camX, double camY, double camZ,
			Matrix4f frustumMatrix, Matrix4f projectionMatrix);

	@Accessor("ticks")
	int gemrender$getTicks();
}
