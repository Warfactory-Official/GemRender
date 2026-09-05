package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_RGBA16F;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.render.GlAudit;

import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class WaterSplit {
	private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.watersplit"));

	private static final WaterSplit INSTANCE = new WaterSplit();

	private final WaterSplitPrograms programs = new WaterSplitPrograms();
	private final WaterDepthPrepass prepass = new WaterDepthPrepass();

	private final PassState compositeState = new PassState();
	private final PassState frontState = new PassState();

	private final GpuPassTimer prepassTimer = new GpuPassTimer();
	private final GpuPassTimer midTimer = new GpuPassTimer();
	private final GpuPassTimer lateTimer = new GpuPassTimer();

	private int frontTexture;
	private int frontWidth = -1;
	private int frontHeight = -1;

	private boolean prepassValid;

	private boolean armedComposite;

	private boolean pendingFront;

	private boolean absorbanceFrame;

	private boolean waveletFrame;

	private boolean oitDrawsThisFrame;
	private boolean oitDrawsLastFrame;

	private int stashedAccumulate;
	private int stashedDepthBounds;
	private int stashedCoefficients;

	private long framesSplit;

	private WaterSplit() {
	}

	public static WaterSplit getInstance() {
		return INSTANCE;
	}

	private static boolean modeActive() {
		return ENABLED && !Minecraft.useShaderTransparency();
	}

	public void onAfterEntities(RenderLevelStageEvent event) {
		oitDrawsLastFrame = oitDrawsThisFrame;
		oitDrawsThisFrame = false;
		prepassValid = false;
		armedComposite = false;
		pendingFront = false;

		if (!modeActive() || !oitDrawsLastFrame || !programs.ensureCreated()) {
			return;
		}

		prepassTimer.begin();
		prepass.run(event, programs);
		prepassTimer.end();
		prepassValid = true;
	}

	public void beforeOitComposite(OitFramebuffer oit, Runnable resubmit) {
		oitDrawsThisFrame = true;

		if (!prepassValid || !modeActive()) {
			return;
		}

		midTimer.begin();

		RenderTarget main = Minecraft.getInstance()
				.getMainRenderTarget();
		ensureFrontTexture(main.width, main.height);

		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, oit.fbo);
		glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 5, frontTexture, 0);
		glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, prepass.textureId(), 0);
		RenderSystem.clearColor(0f, 0f, 0f, 0f);
		RenderSystem.clear(GL_COLOR_BUFFER_BIT, false);

		// Flywheel's OIT framebuffer is borrowed here, not owned. If the resubmitted draws throw, its
		// attachments must still go back: leaving them pointing at our front and prepass textures is
		// invisible until every later frame composites the wrong image, with nothing naming the cause.
		Absorbance.getInstance()
				.beginFrontResubmit();

		GlAudit.Scope audit = GlAudit.open("water:oit-front");
		try {
			resubmit.run();
		} finally {
			audit.close();
			Absorbance.getInstance()
					.endFrontResubmit();
			glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 5, oit.accumulate, 0);
			glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, main.getDepthTextureId(), 0);
		}

		stashedAccumulate = oit.accumulate;
		stashedDepthBounds = oit.depthBounds;
		stashedCoefficients = oit.coefficients;
		armedComposite = true;
	}

	public boolean compositeInstead(OitFramebuffer oit) {
		Absorbance absorbance = Absorbance.getInstance();

		if (!armedComposite) {
			if (!absorbance.present() || !programs.ensureCreated()) {
				return false;
			}
			compositeAbsorbance(absorbance);
			return absorbance.exclusive();
		}
		armedComposite = false;
		absorbanceFrame = absorbance.present();
		waveletFrame = !absorbance.exclusive();

		GlAudit.Scope audit = GlAudit.open("water:composite");
		compositeState.save();
		try {
			Minecraft.getInstance()
					.getMainRenderTarget()
					.bindWrite(false);

			RenderSystem.depthMask(false);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
			RenderSystem.depthFunc(GL_ALWAYS);

			if (absorbanceFrame) {
				programs.drawAbsorbanceBehind(absorbance.accumulateTexture(), absorbance.frontTexture());
			}
			if (waveletFrame) {
				programs.drawBehind(oit.accumulate, frontTexture, oit.depthBounds, oit.coefficients,
						prepass.textureId());
			}
		} finally {
			compositeState.restore();
			Minecraft.getInstance()
					.getMainRenderTarget()
					.bindWrite(false);
			audit.close();
		}
		midTimer.end();

		pendingFront = true;
		framesSplit++;
		return true;
	}

	public void onAfterTranslucent(RenderLevelStageEvent event) {
		if (prepass.isRendering() || !pendingFront) {
			return;
		}
		pendingFront = false;

		lateTimer.begin();

		GlAudit.Scope audit = GlAudit.open("water:front");
		frontState.save();
		try {
			Minecraft.getInstance()
					.getMainRenderTarget()
					.bindWrite(false);

			RenderSystem.depthMask(true);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL_LEQUAL);

			if (absorbanceFrame) {
				RenderSystem.depthMask(false);
				programs.drawAbsorbanceFront(Absorbance.getInstance()
						.frontTexture());
			}
			if (waveletFrame) {
				RenderSystem.depthMask(true);
				programs.drawFront(stashedAccumulate, frontTexture, stashedDepthBounds, stashedCoefficients,
						prepass.textureId());
			}
		} finally {
			frontState.restore();
			GlStateManager._activeTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0);
			audit.close();
		}

		lateTimer.end();
	}

	private void compositeAbsorbance(Absorbance absorbance) {
		GlAudit.Scope audit = GlAudit.open("absorbance:composite");
		compositeState.save();
		try {
			Minecraft.getInstance()
					.getMainRenderTarget()
					.bindWrite(false);

			RenderSystem.depthMask(false);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
			RenderSystem.depthFunc(GL_ALWAYS);

			programs.drawAbsorbanceComposite(absorbance.accumulateTexture());
		} finally {
			compositeState.restore();
			Minecraft.getInstance()
					.getMainRenderTarget()
					.bindWrite(false);
			audit.close();
		}
	}

	private void ensureFrontTexture(int width, int height) {
		if (frontWidth == width && frontHeight == height) {
			return;
		}
		frontWidth = width;
		frontHeight = height;

		if (frontTexture != 0) {
			glDeleteTextures(frontTexture);
		}
		frontTexture = glGenTextures();

		// Put back whatever the active unit had, rather than zeroing it: this runs mid-frame on a window
		// resize, and unbinding the caller's texture instead of restoring it is a one-frame glitch that
		// only ever reproduces while dragging a window edge.
		int previousTexture = org.lwjgl.opengl.GL11C.glGetInteger(
				org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
		try {
			GlStateManager._bindTexture(frontTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0,
					org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_FLOAT,
					(java.nio.ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		} finally {
			GlStateManager._bindTexture(previousTexture);
		}
	}

	public void resetRun() {
		prepassTimer.reset();
		midTimer.reset();
		lateTimer.reset();
		framesSplit = 0;
	}

	public String report() {
		if (!ENABLED) {
			return "off";
		}
		if (framesSplit == 0) {
			return "idle";
		}
		return String.format(java.util.Locale.ROOT,
				"active(frames=%d,prepassGpu=%dus,extraOitGpu=%dus,frontGpu=%dus,cpu=%dus)",
				framesSplit,
				prepassTimer.meanGpuMicros(),
				midTimer.meanGpuMicros(),
				lateTimer.meanGpuMicros(),
				prepassTimer.meanCpuMicros() + midTimer.meanCpuMicros() + lateTimer.meanCpuMicros());
	}
}
