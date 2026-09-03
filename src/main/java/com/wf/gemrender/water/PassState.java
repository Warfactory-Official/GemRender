package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL33C.GL_BLEND;
import static org.lwjgl.opengl.GL33C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL33C.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL33C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL33C.glGetInteger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * The blend, depth and framebuffer state a full-screen pass changes, saved on the way in and put back
 * on the way out.
 *
 * <p>Writing the defaults back instead – {@code disableBlend}, {@code depthMask(true)},
 * {@code depthFunc(GL_LEQUAL)} – is the mistake this class exists to stop. These passes run inside
 * Minecraft's level render at a point where blend is on, the depth mask is off and the bound target is
 * not the main one; restoring the defaults there is a leak that happens to look like tidying up.
 *
 * <p>Every write goes through {@link RenderSystem} or {@link GlStateManager} so Minecraft's shadow copy
 * of this state follows the real thing. One instance per pass, reused; the reads are plain state
 * queries, which drivers answer from their own CPU-side copy.
 */
final class PassState {
	private int drawFramebuffer;

	private boolean blend;
	private int blendSrcRgb;
	private int blendDstRgb;
	private int blendSrcAlpha;
	private int blendDstAlpha;
	private int blendEquationRgb;
	private int blendEquationAlpha;

	private boolean depthTest;
	private boolean depthMask;
	private int depthFunc;

	void save() {
		drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);

		blend = glGetInteger(GL_BLEND) != 0;
		blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
		blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
		blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
		blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
		blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
		blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);

		depthTest = glGetInteger(GL_DEPTH_TEST) != 0;
		depthMask = glGetInteger(GL_DEPTH_WRITEMASK) != 0;
		depthFunc = glGetInteger(GL_DEPTH_FUNC);
	}

	void restore() {
		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer);

		GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
		if (blendEquationRgb == blendEquationAlpha) {
			RenderSystem.blendEquation(blendEquationRgb);
		}
		if (blend) {
			RenderSystem.enableBlend();
		} else {
			RenderSystem.disableBlend();
		}

		RenderSystem.depthFunc(depthFunc);
		RenderSystem.depthMask(depthMask);
		if (depthTest) {
			RenderSystem.enableDepthTest();
		} else {
			RenderSystem.disableDepthTest();
		}
	}
}
