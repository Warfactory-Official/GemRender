package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.GL_COLOR;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_RGBA16F;
import static org.lwjgl.opengl.GL30C.glClearBufferfv;
import static org.lwjgl.opengl.GL30C.glDrawBuffers;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

import com.mojang.blaze3d.platform.GlStateManager;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import net.minecraft.client.Minecraft;

public final class Absorbance {
	private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.absorbance"));

	static final int WAVELET_SLOT = 5;
	static final int ABSORBANCE_SLOT = 6;

	private static final int[] DRAW_WAVELET = { GL_COLOR_ATTACHMENT0 + WAVELET_SLOT };
	private static final int[] DRAW_ABSORBANCE = { GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT };

	private static final float[] ZERO = { 0f, 0f, 0f, 0f };

	private static final Absorbance INSTANCE = new Absorbance();

	private volatile MaterialShaders[] shaders = new MaterialShaders[0];

	private boolean sawAbsorbance;
	private boolean sawOther;

	private boolean present;
	private boolean exclusive;

	private boolean inEvaluate;
	private boolean routedToAbsorbance;

	private int accumulate;
	private int front;
	private int width = -1;
	private int height = -1;

	private long framesPresent;
	private long framesExclusive;
	private long framesMixed;

	private final GpuStampTimer chainTimer = new GpuStampTimer();

	private Absorbance() {
	}

	public static Absorbance getInstance() {
		return INSTANCE;
	}

	public synchronized void register(MaterialShaders value) {
		MaterialShaders[] current = shaders;
		for (MaterialShaders existing : current) {
			if (existing.equals(value)) {
				return;
			}
		}

		MaterialShaders[] next = java.util.Arrays.copyOf(current, current.length + 1);
		next[current.length] = value;
		shaders = next;
	}

	private boolean isAbsorbance(Material material) {
		MaterialShaders[] ours = shaders;
		if (ours.length == 0) {
			return false;
		}

		MaterialShaders theirs = material.shaders();
		for (MaterialShaders candidate : ours) {
			if (candidate.equals(theirs)) {
				return true;
			}
		}
		return false;
	}

	public void observe(Material material) {
		boolean mine = isAbsorbance(material);

		if (mine) {
			sawAbsorbance = true;
		} else {
			sawOther = true;
		}

		if (!inEvaluate) {
			return;
		}

		if (mine != routedToAbsorbance) {
			routedToAbsorbance = mine;
			glDrawBuffers(mine ? DRAW_ABSORBANCE : DRAW_WAVELET);
		}
	}

	public void beginFrame() {
		present = ENABLED && sawAbsorbance;
		exclusive = present && !sawOther;

		sawAbsorbance = false;
		sawOther = false;
		inEvaluate = false;

		if (present) {
			framesPresent++;
			if (exclusive) {
				framesExclusive++;
			} else {
				framesMixed++;
			}
		}

		ensureTextures();
		glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT, accumulate, 0);

		chainTimer.begin();
	}

	public void beginEvaluate() {
		inEvaluate = true;
		routedToAbsorbance = false;

		glDrawBuffers(DRAW_ABSORBANCE);
		glClearBufferfv(GL_COLOR, 0, ZERO);
		glDrawBuffers(DRAW_WAVELET);
	}

	public void endEvaluate() {
		if (!inEvaluate) {
			return;
		}
		inEvaluate = false;

		if (routedToAbsorbance) {
			routedToAbsorbance = false;
			glDrawBuffers(DRAW_WAVELET);
		}
	}

	public void endFrame() {
		endEvaluate();
		chainTimer.end();
	}

	public void beginFrontResubmit() {
		if (!present) {
			return;
		}

		glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT, front, 0);

		glDrawBuffers(DRAW_ABSORBANCE);
		glClearBufferfv(GL_COLOR, 0, ZERO);
		glDrawBuffers(DRAW_WAVELET);
		routedToAbsorbance = false;
	}

	public void endFrontResubmit() {
		if (!present) {
			return;
		}

		if (routedToAbsorbance) {
			routedToAbsorbance = false;
			glDrawBuffers(DRAW_WAVELET);
		}

		glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT, accumulate, 0);
	}

	public boolean present() {
		return present;
	}

	public boolean exclusive() {
		return exclusive;
	}

	public int accumulateTexture() {
		return accumulate;
	}

	public int frontTexture() {
		return front;
	}

	private void ensureTextures() {
		Minecraft mc = Minecraft.getInstance();
		int newWidth = mc.getMainRenderTarget().width;
		int newHeight = mc.getMainRenderTarget().height;

		if (width == newWidth && height == newHeight) {
			return;
		}
		width = newWidth;
		height = newHeight;

		if (accumulate != 0) {
			glDeleteTextures(accumulate);
			glDeleteTextures(front);
		}

		int previousTexture = org.lwjgl.opengl.GL11C.glGetInteger(
				org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
		try {
			accumulate = allocate();
			front = allocate();
		} finally {
			GlStateManager._bindTexture(previousTexture);
		}
	}

	private int allocate() {
		int texture = glGenTextures();
		GlStateManager._bindTexture(texture);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT,
				(java.nio.ByteBuffer) null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		return texture;
	}

	public void resetRun() {
		framesPresent = 0;
		framesExclusive = 0;
		framesMixed = 0;
		chainTimer.reset();
	}

	public String report() {
		String chain = "chainGpu=" + chainTimer.meanMicros() + "us";

		if (!ENABLED) {
			return "off(" + chain + ")";
		}
		if (framesPresent == 0) {
			return "idle(" + chain + ")";
		}
		return "active(frames=" + framesPresent + ",exclusive=" + framesExclusive + ",mixed="
				+ framesMixed + "," + chain + ")";
	}
}
