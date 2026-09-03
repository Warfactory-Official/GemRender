package com.wf.gemrender.render;

import static org.lwjgl.opengl.GL31C.GL_R32F;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glTexBuffer;
import static org.lwjgl.opengl.GL43C.GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL43C.glTexBufferRange;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
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

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

public final class BoneBuffer {
	public static final int TEXTURE_UNIT = TextureUnits.BONES;

	public static final int FLOATS_PER_MATRIX = 16;
	private static final int INITIAL_MATRICES = 256;

	private static final int RING_REGIONS = 3;

	private static final boolean RING = !"false".equalsIgnoreCase(System.getProperty("gemrender.ring"));

	private static final boolean ORPHAN = !"false".equalsIgnoreCase(System.getProperty("gemrender.orphan"));

	private static final BoneBuffer INSTANCE = new BoneBuffer();

	private final Object stagingLock = new Object();

	private int bufferId;
	private int textureId;
	private int capacityMatrices;

	private boolean ringActive;

	private int regionStrideBytes;

	private int region;

	private FloatBuffer staging;

	private int floatCount;

	private volatile int lastUploadedCount;

	private BoneBuffer() {
	}

	public static BoneBuffer getInstance() {
		return INSTANCE;
	}

	public int addPalette(Matrix4fc[] palette) {
		return addPalette(palette, palette.length);
	}

	public int addPalette(Matrix4fc[] palette, int count) {
		synchronized (stagingLock) {
			ensureStagingCapacity(floatCount + count * FLOATS_PER_MATRIX);

			int base = floatCount / FLOATS_PER_MATRIX;
			for (int i = 0; i < count; i++) {
				palette[i].get(floatCount, staging);
				floatCount += FLOATS_PER_MATRIX;
			}
			return base;
		}
	}

	public int addMorphBlock(float[] block, int length) {
		synchronized (stagingLock) {
			ensureStagingCapacity(floatCount + length + FLOATS_PER_MATRIX);

			int base = floatCount;
			for (int i = 0; i < length; i++) {
				staging.put(base + i, block[i]);
			}

			floatCount = alignToMatrix(floatCount + length);
			return base;
		}
	}

	public int lastUploadedCount() {
		return lastUploadedCount;
	}

	private static int alignToMatrix(int floats) {
		int remainder = floats % FLOATS_PER_MATRIX;
		return remainder == 0 ? floats : floats + FLOATS_PER_MATRIX - remainder;
	}

	public boolean isInitialized() {
		return textureId != 0;
	}

	public int textureId() {
		return textureId;
	}

	public void uploadAndBind() {
		RenderSystem.assertOnRenderThread();

		int count;
		synchronized (stagingLock) {
			count = alignToMatrix(floatCount) / FLOATS_PER_MATRIX;
			floatCount = 0;

			if (count == 0) {
				lastUploadedCount = 0;
				return;
			}

			// Restored rather than zeroed: this runs inside Flywheel's render, which has its own array
			// buffer bound and does not re-bind it after every call it makes. Read before create(),
			// because create() binds ours – snapshotting after it would record our own buffer as the one
			// to go back to, and Flywheel's would stay unbound for the rest of the run. The GL audit
			// caught exactly that, on the one frame per launch where create() runs.
			int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			try {
				if (textureId == 0) {
					create();
				}

				glBindBuffer(GL_ARRAY_BUFFER, bufferId);

				if (count > capacityMatrices) {
					capacityMatrices = Integer.highestOneBit(count - 1) * 2;
					allocate();
					GemRender.LOGGER.debug("Bone buffer grown to {} matrices per region", capacityMatrices);
				}

				if (ringActive) {
					region = (region + 1) % RING_REGIONS;
				} else if (ORPHAN) {
					glBufferData(GL_ARRAY_BUFFER, regionStrideBytes, GL_DYNAMIC_DRAW);
				}

				staging.position(0)
						.limit(count * FLOATS_PER_MATRIX);
				glBufferSubData(GL_ARRAY_BUFFER, (long) region * regionStrideBytes, staging);
				staging.clear();
			} finally {
				glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
			}
		}

		bind();
		lastUploadedCount = count;
	}

	public void bind() {
		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_BUFFER, textureId);
			if (ringActive) {
				glTexBufferRange(GL_TEXTURE_BUFFER, GL_R32F, bufferId,
						(long) region * regionStrideBytes, regionStrideBytes);
			}
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	private void create() {
		bufferId = glGenBuffers();
		textureId = glGenTextures();

		ringActive = RING && GL.getCapabilities().OpenGL43;

		capacityMatrices = INITIAL_MATRICES;
		allocate();

		GemRender.LOGGER.info("Bone buffer created on texture unit {}, capacity {} matrices x {} region(s){}",
				TEXTURE_UNIT, capacityMatrices, ringActive ? RING_REGIONS : 1,
				ringActive ? "" : RING ? " (no glTexBufferRange; ring disabled)" : " (ring switched off)");
	}

	private void allocate() {
		int regionBytes = capacityMatrices * FLOATS_PER_MATRIX * Float.BYTES;

		int alignment = ringActive ? Math.max(1, glGetInteger(GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT)) : 1;
		regionStrideBytes = (regionBytes + alignment - 1) / alignment * alignment;

		// Leaves ours bound on purpose: uploadAndBind writes into it immediately afterwards, and owns
		// putting the caller's back.
		glBindBuffer(GL_ARRAY_BUFFER, bufferId);
		glBufferData(GL_ARRAY_BUFFER, (long) regionStrideBytes * (ringActive ? RING_REGIONS : 1),
				GL_DYNAMIC_DRAW);

		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_BUFFER, textureId);
			if (!ringActive) {
				glTexBuffer(GL_TEXTURE_BUFFER, GL_R32F, bufferId);
			}
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	private void ensureStagingCapacity(int floats) {
		if (staging != null && staging.capacity() >= floats) {
			return;
		}

		int newCapacity = Math.max(floats, INITIAL_MATRICES * FLOATS_PER_MATRIX);
		FloatBuffer grown = MemoryUtil.memAllocFloat(newCapacity);
		if (staging != null) {
			staging.position(0)
					.limit(floatCount);
			grown.put(staging);
			MemoryUtil.memFree(staging);
		}
		grown.clear();
		staging = grown;
	}

	public void delete() {
		if (textureId != 0) {
			glDeleteTextures(textureId);
			glDeleteBuffers(bufferId);
			textureId = 0;
			bufferId = 0;
			capacityMatrices = 0;
			regionStrideBytes = 0;
			region = 0;
		}
		synchronized (stagingLock) {
			if (staging != null) {
				MemoryUtil.memFree(staging);
				staging = null;
			}
			floatCount = 0;
		}
	}
}
