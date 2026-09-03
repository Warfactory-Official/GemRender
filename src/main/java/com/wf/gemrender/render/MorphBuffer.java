package com.wf.gemrender.render;

import static org.lwjgl.opengl.GL31C.GL_R32F;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glTexBuffer;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL33C.glBindBuffer;
import static org.lwjgl.opengl.GL33C.glGetInteger;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glBufferData;
import static org.lwjgl.opengl.GL33C.glDeleteBuffers;
import static org.lwjgl.opengl.GL33C.glDeleteTextures;
import static org.lwjgl.opengl.GL33C.glGenBuffers;
import static org.lwjgl.opengl.GL33C.glGenTextures;

import java.nio.FloatBuffer;
import java.util.Arrays;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

public final class MorphBuffer {
	public static final int TEXTURE_UNIT = TextureUnits.MORPHS;

	public static final int MAX_FLOATS = 1 << 24;

	private static final MorphBuffer INSTANCE = new MorphBuffer();

	private float[] deltas = new float[0];
	private int floatCount;
	private boolean dirty;

	private int bufferId;
	private int textureId;

	private MorphBuffer() {
	}

	public static MorphBuffer getInstance() {
		return INSTANCE;
	}

	public int textureId() {
		return textureId;
	}

	public synchronized int register(float[] block) {
		if ((long) floatCount + block.length > MAX_FLOATS) {
			throw new IllegalStateException("Morph buffer would exceed " + MAX_FLOATS
					+ " floats, past which a set's base index is no longer exact as a float. "
					+ "Reduce the number or size of morph targets.");
		}

		if (floatCount + block.length > deltas.length) {
			deltas = Arrays.copyOf(deltas, Math.max(floatCount + block.length, Math.max(4096, deltas.length * 2)));
		}

		int base = floatCount;
		System.arraycopy(block, 0, deltas, base, block.length);
		floatCount += block.length;
		dirty = true;
		return base;
	}

	public synchronized void reset() {
		floatCount = 0;

		dirty = true;
	}

	public synchronized int floatCount() {
		return floatCount;
	}

	public boolean isInitialized() {
		return textureId != 0;
	}

	public synchronized void uploadAndBind() {
		RenderSystem.assertOnRenderThread();

		if (floatCount == 0) {
			return;
		}

		if (textureId == 0) {
			bufferId = glGenBuffers();
			textureId = glGenTextures();
		}

		if (dirty) {
			FloatBuffer staging = MemoryUtil.memAllocFloat(floatCount);
			int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			try {
				staging.put(deltas, 0, floatCount)
						.flip();
				glBindBuffer(GL_ARRAY_BUFFER, bufferId);
				glBufferData(GL_ARRAY_BUFFER, staging, GL_STATIC_DRAW);
			} finally {
				glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
				MemoryUtil.memFree(staging);
			}

			int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
			try {
				glBindTexture(GL_TEXTURE_BUFFER, textureId);
				glTexBuffer(GL_TEXTURE_BUFFER, GL_R32F, bufferId);
			} finally {
				TextureUnits.restore(previousUnit);
			}

			dirty = false;
			GemRender.LOGGER.info("Morph buffer uploaded: {} floats ({} KB) on texture unit {}",
					floatCount, floatCount * 4 / 1024, TEXTURE_UNIT);
			return;
		}

		bind();
	}

	public void bind() {
		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_BUFFER, textureId);
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	public synchronized void delete() {
		if (textureId != 0) {
			glDeleteTextures(textureId);
			glDeleteBuffers(bufferId);
			textureId = 0;
			bufferId = 0;
		}
		deltas = new float[0];
		floatCount = 0;
		dirty = false;
	}
}
