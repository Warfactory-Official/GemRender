package com.wf.gemrender.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    //? if >=1.21 <26.1 {
    @Invoker("renderSectionLayer")
    void gemrender$renderSectionLayer(RenderType renderType, double camX, double camY, double camZ,
                                      Matrix4f frustumMatrix, Matrix4f projectionMatrix);
    //?}

    //? if <1.21 {
	/*@Invoker("renderChunkLayer")
	void gemrender$renderSectionLayer(RenderType renderType, com.mojang.blaze3d.vertex.PoseStack pose,
			double camX, double camY, double camZ, Matrix4f projectionMatrix);
*///?}

    @Accessor("ticks")
    int gemrender$getTicks();
}
