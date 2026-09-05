package com.wf.gemrender.volume;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL12C.glTexImage3D;
import static org.lwjgl.opengl.GL12C.glTexSubImage3D;
import static org.lwjgl.opengl.GL30C.GL_R8;
import static org.lwjgl.opengl.GL33C.glBindTexture;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.TextureUnits;

public final class VolumeAtlas {
	public static final int TEXTURE_UNIT = TextureUnits.VOLUME_FIELD;

	public static final int TILE = 32;

	public static final int BORDER = 1;

	public static final int INTERIOR = TILE - BORDER * 2;

	private static final int TILES_PER_AXIS = 4;

	private static final int SIZE = TILE * TILES_PER_AXIS;

	private static final int TILE_COUNT = TILES_PER_AXIS * TILES_PER_AXIS * TILES_PER_AXIS;

	private static final VolumeAtlas INSTANCE = new VolumeAtlas();

	private final Object lock = new Object();

	private final BitSet used = new BitSet(TILE_COUNT);

	private final Queue<VolumeField> queued = new ConcurrentLinkedQueue<>();

	private int textureId;

	private VolumeAtlas() {
	}

	public static VolumeAtlas getInstance() {
		return INSTANCE;
	}

	public int allocate() {
		synchronized (lock) {
			int tile = used.nextClearBit(0);
			if (tile >= TILE_COUNT) {
				return -1;
			}
			used.set(tile);
			return tile;
		}
	}

	public void release(int tile) {
		synchronized (lock) {
			if (tile >= 0 && tile < TILE_COUNT) {
				used.clear(tile);
			}
		}
	}

	public float originU(int tile) {
		return (tileX(tile) * TILE + BORDER) / (float) SIZE;
	}

	public float originV(int tile) {
		return (tileY(tile) * TILE + BORDER) / (float) SIZE;
	}

	public float originW(int tile) {
		return (tileZ(tile) * TILE + BORDER) / (float) SIZE;
	}

	public float scale() {
		return INTERIOR / (float) SIZE;
	}

	void enqueue(VolumeField field) {
		queued.add(field);
	}

	public void flushPending() {
		VolumeField field;
		while ((field = queued.poll()) != null) {
			field.uploadPending();
		}
	}

	public void upload(int tile, ByteBuffer voxels) {
		if (tile < 0) {
			return;
		}

		ensureCreated();

		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_3D, textureId);
			glTexSubImage3D(GL_TEXTURE_3D, 0, tileX(tile) * TILE, tileY(tile) * TILE, tileZ(tile) * TILE,
					TILE, TILE, TILE, GL_RED, GL_UNSIGNED_BYTE, voxels);
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	public void bind() {
		if (textureId == 0) {
			return;
		}

		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_3D, textureId);
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	public boolean isInitialized() {
		return textureId != 0;
	}

	public void delete() {
		synchronized (lock) {
			if (textureId != 0) {
				glDeleteTextures(textureId);
				textureId = 0;
			}
			used.clear();
		}
	}

	private void ensureCreated() {
		if (textureId != 0) {
			return;
		}

		textureId = glGenTextures();

		ByteBuffer zero = MemoryUtil.memCalloc(SIZE * SIZE * SIZE);
		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			glBindTexture(GL_TEXTURE_3D, textureId);
			glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, SIZE, SIZE, SIZE, 0, GL_RED, GL_UNSIGNED_BYTE, zero);
			glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
		} finally {
			TextureUnits.restore(previousUnit);
			MemoryUtil.memFree(zero);
		}

		GemRender.LOGGER.info("Volume field atlas {}^3 created on texture unit {}, {} tiles of {}^3",
				SIZE, TEXTURE_UNIT, TILE_COUNT, TILE);
	}

	private static int tileX(int tile) {
		return tile % TILES_PER_AXIS;
	}

	private static int tileY(int tile) {
		return (tile / TILES_PER_AXIS) % TILES_PER_AXIS;
	}

	private static int tileZ(int tile) {
		return tile / (TILES_PER_AXIS * TILES_PER_AXIS);
	}
}
