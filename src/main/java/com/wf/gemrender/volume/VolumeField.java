package com.wf.gemrender.volume;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.lwjgl.system.MemoryUtil;

public final class VolumeField {
	private static final int TILE = VolumeAtlas.TILE;

	private static final int BORDER = VolumeAtlas.BORDER;

	private static final int INTERIOR = VolumeAtlas.INTERIOR;

	private static final int VOXELS = TILE * TILE * TILE;

	private final int tile;

	private final float[] coverage = new float[VOXELS];

	private final float[] scratch = new float[VOXELS];

	private double minX;

	private double minY;

	private double minZ;

	private double spanX = 1.0;

	private double spanY = 1.0;

	private double spanZ = 1.0;

	private ByteBuffer staging;

	private volatile boolean pending;

	private volatile boolean closed;

	private VolumeField(int tile) {
		this.tile = tile;
	}

	public static VolumeField create() {
		int tile = VolumeAtlas.getInstance()
				.allocate();
		return tile < 0 ? null : new VolumeField(tile);
	}

	public int tile() {
		return tile;
	}

	public void begin(double x0, double y0, double z0, double x1, double y1, double z1) {
		minX = x0;
		minY = y0;
		minZ = z0;
		spanX = Math.max(x1 - x0, 1.0e-3);
		spanY = Math.max(y1 - y0, 1.0e-3);
		spanZ = Math.max(z1 - z0, 1.0e-3);
		Arrays.fill(coverage, 0.0f);
	}

	public void addBox(double x0, double y0, double z0, double x1, double y1, double z1) {
		float ax = voxel(x0, minX, spanX);
		float ay = voxel(y0, minY, spanY);
		float az = voxel(z0, minZ, spanZ);
		float bx = voxel(x1, minX, spanX);
		float by = voxel(y1, minY, spanY);
		float bz = voxel(z1, minZ, spanZ);

		int lowX = Math.max(BORDER, (int) Math.floor(ax));
		int lowY = Math.max(BORDER, (int) Math.floor(ay));
		int lowZ = Math.max(BORDER, (int) Math.floor(az));
		int highX = Math.min(TILE - BORDER - 1, (int) Math.ceil(bx));
		int highY = Math.min(TILE - BORDER - 1, (int) Math.ceil(by));
		int highZ = Math.min(TILE - BORDER - 1, (int) Math.ceil(bz));

		for (int z = lowZ; z <= highZ; z++) {
			float overlapZ = overlap(z, az, bz);
			if (overlapZ <= 0.0f) {
				continue;
			}
			for (int y = lowY; y <= highY; y++) {
				float overlapY = overlap(y, ay, by);
				if (overlapY <= 0.0f) {
					continue;
				}
				int row = (z * TILE + y) * TILE;
				for (int x = lowX; x <= highX; x++) {
					float overlapX = overlap(x, ax, bx);
					if (overlapX <= 0.0f) {
						continue;
					}
					int at = row + x;
					coverage[at] = Math.min(1.0f, coverage[at] + overlapX * overlapY * overlapZ);
				}
			}
		}
	}

	public void commit() {
		if (closed) {
			return;
		}

		blur();

		synchronized (this) {
			if (closed) {
				return;
			}
			if (staging == null) {
				staging = MemoryUtil.memAlloc(VOXELS);
			}
			for (int i = 0; i < VOXELS; i++) {
				staging.put(i, (byte) Math.round(Math.min(1.0f, Math.max(0.0f, coverage[i])) * 255.0f));
			}
			staging.position(0)
					.limit(VOXELS);
		}

		pending = true;
		VolumeAtlas.getInstance()
				.enqueue(this);
	}

	boolean pending() {
		return pending && !closed;
	}

	synchronized void uploadPending() {
		if (closed || !pending) {
			return;
		}
		pending = false;
		if (staging != null) {
			staging.position(0)
					.limit(VOXELS);
			VolumeAtlas.getInstance()
					.upload(tile, staging);
		}
	}

	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		pending = false;
		if (staging != null) {
			MemoryUtil.memFree(staging);
			staging = null;
		}
		VolumeAtlas.getInstance()
				.release(tile);
	}

	private void blur() {
		pass(coverage, scratch, 1);
		pass(scratch, coverage, TILE);
		pass(coverage, scratch, TILE * TILE);
		System.arraycopy(scratch, 0, coverage, 0, VOXELS);
	}

	private static void pass(float[] src, float[] dst, int stride) {
		for (int z = 1; z < TILE - 1; z++) {
			for (int y = 1; y < TILE - 1; y++) {
				int row = (z * TILE + y) * TILE;
				for (int x = 1; x < TILE - 1; x++) {
					int at = row + x;
					dst[at] = (src[at - stride] + src[at] * 2.0f + src[at + stride]) * 0.25f;
				}
			}
		}
	}

	private static float voxel(double world, double min, double span) {
		return (float) (BORDER + (world - min) / span * INTERIOR);
	}

	private static float overlap(int index, float low, float high) {
		float start = Math.max(index, low);
		float end = Math.min(index + 1.0f, high);
		return Math.max(0.0f, end - start);
	}
}
