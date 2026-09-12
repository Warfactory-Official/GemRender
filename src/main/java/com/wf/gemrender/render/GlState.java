package com.wf.gemrender.render;

import com.mojang.blaze3d.platform.GlStateManager;

public final class GlState {
    private GlState() {
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {

        //? if >=26.1 {
		/*GlStateManager._colorMask(
				(red ? com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_RED : 0)
						| (green ? com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_GREEN : 0)
						| (blue ? com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_BLUE : 0)
						| (alpha ? com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_ALPHA : 0));
*///?} else {
        GlStateManager._colorMask(red, green, blue, alpha);
        //?}
    }

    public static void blendEquation(int mode) {
        //? if >=26.1 {
        /*GL14C.glBlendEquation(mode);
         *///?} else {
        GlStateManager._blendEquation(mode);
        //?}
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        //? if >=26.1 {
        /*GL11C.glClearColor(red, green, blue, alpha);
         *///?} else {
        GlStateManager._clearColor(red, green, blue, alpha);
        //?}
    }

    public static void clear(int mask) {
        //? if >=26.1 {
        /*GlStateManager._clear(mask);
         *///?} else {
        GlStateManager._clear(mask, false);
        //?}
    }

    public static int pointSampler(int unit) {
        //? if >=26.1 {
		/*int previous = GL11C.glGetInteger(org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING);
		org.lwjgl.opengl.GL33C.glBindSampler(unit,
				((com.mojang.blaze3d.opengl.GlSampler) com.mojang.blaze3d.systems.RenderSystem
						.getSamplerCache()
						.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST)).getId());
		return previous;
*///?} else {
        return 0;
        //?}
    }

    public static void restoreSampler(int unit, int sampler) {
        //? if >=26.1 {
        /*org.lwjgl.opengl.GL33C.glBindSampler(unit, sampler);
         *///?}
    }

    public static int activeTexture() {
        //? if >=26.1 {
        /*return GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
         *///?} else {
        return GlStateManager._getActiveTexture();
        //?}
    }
}
