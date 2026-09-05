package com.wf.gemrender.volume;

import static org.lwjgl.opengl.GL30C.GL_RGBA32F;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glTexBuffer;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL33C.glBindBuffer;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glBufferData;
import static org.lwjgl.opengl.GL33C.glBufferSubData;
import static org.lwjgl.opengl.GL33C.glDeleteBuffers;
import static org.lwjgl.opengl.GL33C.glDeleteTextures;
import static org.lwjgl.opengl.GL33C.glGenBuffers;
import static org.lwjgl.opengl.GL33C.glGenTextures;
import static org.lwjgl.opengl.GL33C.glGetInteger;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.BitSet;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.TextureUnits;

public final class VolumeBuffer {
	public static final int TEXTURE_UNIT = TextureUnits.VOLUMES;

	public static final int VOLUME_FLOATS = VolumeStyle.FLOATS;

	private static final int INITIAL_SLOTS = 64;

	private static final VolumeBuffer INSTANCE = new VolumeBuffer();

	private final Object lock = new Object();

	private final BitSet used = new BitSet();

	private float[] data = new float[INITIAL_SLOTS * VOLUME_FLOATS];

	private int capacity = INITIAL_SLOTS;

	private boolean reallocate = true;

	private boolean dirty;

	private int bufferId;

	private int textureId;

	private FloatBuffer staging;

	private VolumeBuffer() {
	}

	public static VolumeBuffer getInstance() {
		return INSTANCE;
	}

	public int allocate() {
		synchronized (lock) {
			int slot = used.nextClearBit(0);
			if (slot >= capacity) {
				capacity = Math.max(capacity * 2, slot + 1);
				data = Arrays.copyOf(data, capacity * VOLUME_FLOATS);
				reallocate = true;
				GemRender.LOGGER.debug("Volume buffer grown to {} slots", capacity);
			}

			used.set(slot);
			int at = slot * VOLUME_FLOATS;
			Arrays.fill(data, at, at + VOLUME_FLOATS, 0.0f);
			dirty = true;
			return slot;
		}
	}

	public void release(int slot) {
		synchronized (lock) {
			if (slot < 0 || slot >= capacity) {
				return;
			}

			used.clear(slot);
			int at = slot * VOLUME_FLOATS;
			Arrays.fill(data, at, at + VOLUME_FLOATS, 0.0f);
			dirty = true;
		}
	}

	public void write(int slot, VolumeStyle style, float extentX, float extentY, float extentZ, float fade,
			float seed) {
		synchronized (lock) {
			if (slot < 0 || slot >= capacity) {
				return;
			}

			int at = slot * VOLUME_FLOATS;

			data[at] = extentX;
			data[at + 1] = extentY;
			data[at + 2] = extentZ;
			data[at + 3] = style.density();
			data[at + 4] = style.red();
			data[at + 5] = style.green();
			data[at + 6] = style.blue();
			data[at + 7] = fade;
			data[at + 8] = style.detail();
			data[at + 9] = style.edge();
			data[at + 10] = style.rise();
			data[at + 11] = seed;
			data[at + 12] = style.blockLight();
			data[at + 13] = style.skyLight();
			data[at + 14] = style.phase();
			data[at + 15] = style.ambient();
			data[at + 16] = style.steps();
			data[at + 17] = style.sunSteps();
			data[at + 18] = style.sunDensity();
			data[at + 19] = style.sunStrength();

			dirty = true;
		}
	}

	public int activeCount() {
		synchronized (lock) {
			return used.cardinality();
		}
	}

	public boolean isInitialized() {
		return textureId != 0;
	}

	public int textureId() {
		return textureId;
	}

	public void uploadAndBind() {
		RenderSystem.assertOnRenderThread();

		synchronized (lock) {
			if (used.isEmpty() && textureId == 0) {
				return;
			}

			int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			try {
				if (textureId == 0) {
					bufferId = glGenBuffers();
					textureId = glGenTextures();
					GemRender.LOGGER.info("Volume buffer created on texture unit {}, capacity {} slots",
							TEXTURE_UNIT, capacity);
				}

				if (reallocate || dirty) {
					glBindBuffer(GL_ARRAY_BUFFER, bufferId);
					stage();

					if (reallocate) {
						glBufferData(GL_ARRAY_BUFFER, staging, GL_DYNAMIC_DRAW);
						reallocate = false;

						int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
						try {
							glBindTexture(GL_TEXTURE_BUFFER, textureId);
							glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, bufferId);
						} finally {
							TextureUnits.restore(previousUnit);
						}
					} else {
						glBufferSubData(GL_ARRAY_BUFFER, 0L, staging);
					}

					dirty = false;
				}
			} finally {
				glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
			}
		}

		bind();
	}

	public void bind() {
		if (textureId == 0) {
			return;
		}

		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_BUFFER, textureId);
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	public void delete() {
		synchronized (lock) {
			if (textureId != 0) {
				glDeleteTextures(textureId);
				glDeleteBuffers(bufferId);
				textureId = 0;
				bufferId = 0;
			}
			if (staging != null) {
				MemoryUtil.memFree(staging);
				staging = null;
			}
			reallocate = true;
		}
	}

	private void stage() {
		if (staging == null || staging.capacity() < data.length) {
			if (staging != null) {
				MemoryUtil.memFree(staging);
			}
			staging = MemoryUtil.memAllocFloat(data.length);
		}

		staging.clear();
		staging.put(data, 0, data.length)
				.flip();
	}
}
