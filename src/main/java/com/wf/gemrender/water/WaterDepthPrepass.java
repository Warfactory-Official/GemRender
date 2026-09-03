package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDrawBuffer;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;

import org.lwjgl.opengl.GL11C;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.mixin.LevelRendererAccessor;
import com.wf.gemrender.render.GlAudit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class WaterDepthPrepass {
	private int fbo;
	private int depthTexture;
	private int width = -1;
	private int height = -1;

	private boolean rendering;

	private final PassState state = new PassState();

	int textureId() {
		return depthTexture;
	}

	boolean isRendering() {
		return rendering;
	}

	void run(RenderLevelStageEvent event, WaterSplitPrograms programs) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.getMainRenderTarget();
		ensureSize(main.width, main.height);

		GlAudit.Scope audit = GlAudit.open("water:prepass");
		state.save();
		try {
			GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);

			RenderSystem.disableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11C.GL_ALWAYS);
			RenderSystem.depthMask(true);
			programs.drawDepthCopy(main.getDepthTextureId());
			RenderSystem.depthFunc(GL_LEQUAL);

			var camera = event.getCamera()
					.getPosition();
			rendering = true;
			try {
				((LevelRendererAccessor) event.getLevelRenderer()).gemrender$renderSectionLayer(
						RenderType.translucent(), camera.x, camera.y, camera.z,
						event.getModelViewMatrix(), event.getProjectionMatrix());
			} finally {
				rendering = false;
			}
		} finally {
			// bindWrite first for the viewport, then hand the target back to whoever had it. This event
			// does not always fire with the main target bound, and assuming it does was a leak: the
			// caller's framebuffer stayed unbound for the rest of the frame.
			main.bindWrite(false);
			state.restore();
			audit.close();
		}
	}

	private void ensureSize(int newWidth, int newHeight) {
		if (width == newWidth && height == newHeight) {
			return;
		}
		width = newWidth;
		height = newHeight;

		if (depthTexture != 0) {
			glDeleteTextures(depthTexture);
			glDeleteFramebuffers(fbo);
		}

		depthTexture = glGenTextures();

		// Restored rather than zeroed; see the same note in WaterSplit.ensureFrontTexture.
		int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
		try {
			GlStateManager._bindTexture(depthTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT,
					GL_FLOAT, (java.nio.ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		} finally {
			GlStateManager._bindTexture(previousTexture);
		}

		fbo = glGenFramebuffers();
		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0);

		glDrawBuffer(GL_NONE);
		glReadBuffer(GL_NONE);
	}
}
