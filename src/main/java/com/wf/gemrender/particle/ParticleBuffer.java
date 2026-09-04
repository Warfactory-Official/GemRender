package com.wf.gemrender.particle;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.TextureUnits;

public final class ParticleBuffer {
	public static final int TEXTURE_UNIT = TextureUnits.PARTICLES;

	public static final int MAX_STYLES = 64;

	public static final int PARTICLE_FLOATS = 12;

	public static final int STYLE_REGION_FLOATS = MAX_STYLES * ParticleStyle.FLOATS;

	public static final int PAGE_FLOATS = 256;

	private static final int INITIAL_SLOTS = 1024;

	private static final ParticleBuffer INSTANCE = new ParticleBuffer();

	private final Object lock = new Object();

	private final List<ParticleStyle> styles = new ArrayList<>();

	private final SlotAllocator allocator = new SlotAllocator(INITIAL_SLOTS);

	private final BitSet dirtyPages = new BitSet();

	private float[] data = new float[STYLE_REGION_FLOATS + INITIAL_SLOTS * PARTICLE_FLOATS];

	private boolean reallocate = true;

	private int bufferId;

	private int textureId;

	private FloatBuffer staging;

	private long uploadFrames;

	private long uploadCalls;

	private long uploadBytes;

	private ParticleBuffer() {
	}

	private void stage(int from, int count) {
		if (staging == null || staging.capacity() < count) {
			if (staging != null) {
				MemoryUtil.memFree(staging);
			}
			staging = MemoryUtil.memAllocFloat(Math.max(count, STYLE_REGION_FLOATS));
		}

		staging.clear();
		staging.put(data, from, count)
				.flip();
	}

	public static ParticleBuffer getInstance() {
		return INSTANCE;
	}

	public int registerStyle(ParticleStyle style) {
		synchronized (lock) {
			if (styles.size() >= MAX_STYLES) {
				throw new IllegalStateException("Particle styles exhausted; the buffer holds " + MAX_STYLES);
			}

			int index = styles.size();
			styles.add(style);
			style.write(data, index * ParticleStyle.FLOATS);
			markDirty(index * ParticleStyle.FLOATS, ParticleStyle.FLOATS);
			return index;
		}
	}

	public int styleCount() {
		synchronized (lock) {
			return styles.size();
		}
	}

	public int capacitySlots() {
		synchronized (lock) {
			return allocator.capacity();
		}
	}

	public int allocate(int slots) {
		synchronized (lock) {
			int before = allocator.capacity();
			int base = allocator.allocate(slots);

			if (allocator.capacity() != before) {
				data = Arrays.copyOf(data, STYLE_REGION_FLOATS + allocator.capacity() * PARTICLE_FLOATS);
				reallocate = true;
				GemRender.LOGGER.debug("Particle buffer grown to {} slots", allocator.capacity());
			}

			int from = STYLE_REGION_FLOATS + base * PARTICLE_FLOATS;
			Arrays.fill(data, from, from + slots * PARTICLE_FLOATS, 0.0f);
			markDirty(from, slots * PARTICLE_FLOATS);
			return base;
		}
	}

	public void release(int base, int slots) {
		synchronized (lock) {
			int from = STYLE_REGION_FLOATS + base * PARTICLE_FLOATS;
			Arrays.fill(data, from, from + slots * PARTICLE_FLOATS, 0.0f);
			markDirty(from, slots * PARTICLE_FLOATS);

			allocator.release(base, slots);
		}
	}

	public void write(int slot, float x, float y, float z, float spawnTime, float velocityX, float velocityY,
			float velocityZ, float life, int style, float sizeScale, float spinPhase, float tintScale) {
		synchronized (lock) {
			int at = STYLE_REGION_FLOATS + slot * PARTICLE_FLOATS;

			data[at] = x;
			data[at + 1] = y;
			data[at + 2] = z;
			data[at + 3] = spawnTime;
			data[at + 4] = velocityX;
			data[at + 5] = velocityY;
			data[at + 6] = velocityZ;
			data[at + 7] = life;
			data[at + 8] = style;
			data[at + 9] = sizeScale;
			data[at + 10] = spinPhase;
			data[at + 11] = tintScale;

			markDirty(at, PARTICLE_FLOATS);
		}
	}

	public void clearSlot(int slot) {
		synchronized (lock) {
			int at = STYLE_REGION_FLOATS + slot * PARTICLE_FLOATS;
			Arrays.fill(data, at, at + PARTICLE_FLOATS, 0.0f);
			markDirty(at, PARTICLE_FLOATS);
		}
	}

	public int aliveCount(float now) {
		synchronized (lock) {
			int alive = 0;

			for (int slot = 0; slot < allocator.capacity(); slot++) {
				int at = STYLE_REGION_FLOATS + slot * PARTICLE_FLOATS;
				if (ParticleMotion.alive(now - data[at + 3], data[at + 7])) {
					alive++;
				}
			}

			return alive;
		}
	}

	public void resetRun() {
		synchronized (lock) {
			uploadFrames = 0;
			uploadCalls = 0;
			uploadBytes = 0;
		}
	}

	public long uploadFrames() {
		synchronized (lock) {
			return uploadFrames;
		}
	}

	public long uploadCalls() {
		synchronized (lock) {
			return uploadCalls;
		}
	}

	public long uploadBytes() {
		synchronized (lock) {
			return uploadBytes;
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
			if (styles.isEmpty()) {
				return;
			}

			uploadFrames++;

			int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			try {
				if (textureId == 0) {
					bufferId = glGenBuffers();
					textureId = glGenTextures();
					GemRender.LOGGER.info("Particle buffer created on texture unit {}, capacity {} slots",
							TEXTURE_UNIT, allocator.capacity());
				}

				glBindBuffer(GL_ARRAY_BUFFER, bufferId);

				if (reallocate) {
					stage(0, data.length);
					glBufferData(GL_ARRAY_BUFFER, staging, GL_DYNAMIC_DRAW);

					uploadCalls++;
					uploadBytes += (long) data.length * Float.BYTES;

					reallocate = false;
					dirtyPages.clear();

					int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
					try {
						glBindTexture(GL_TEXTURE_BUFFER, textureId);
						glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, bufferId);
					} finally {
						TextureUnits.restore(previousUnit);
					}
				} else {
					for (int page = dirtyPages.nextSetBit(0); page >= 0; page = dirtyPages.nextSetBit(page + 1)) {
						int end = dirtyPages.nextClearBit(page);

						int from = page * PAGE_FLOATS;
						int count = Math.min(end * PAGE_FLOATS, data.length) - from;

						stage(from, count);
						glBufferSubData(GL_ARRAY_BUFFER, (long) from * Float.BYTES, staging);

						uploadCalls++;
						uploadBytes += (long) count * Float.BYTES;

						page = end;
					}

					dirtyPages.clear();
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

	private void markDirty(int from, int count) {
		dirtyPages.set(from / PAGE_FLOATS, (from + count - 1) / PAGE_FLOATS + 1);
	}

}
