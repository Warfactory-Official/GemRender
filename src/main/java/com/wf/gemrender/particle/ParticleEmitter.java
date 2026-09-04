package com.wf.gemrender.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public final class ParticleEmitter {
	private final int style;

	private final int base;

	private final int capacity;

	private final Vec3i origin;

	private int cursor;

	private float latestDeath;

	private boolean closed;

	private ParticleEmitter(int style, int base, int capacity, Vec3i origin) {
		this.style = style;
		this.base = base;
		this.capacity = capacity;
		this.origin = origin;
	}

	public static ParticleEmitter create(int style, int capacity, double x, double y, double z) {
		Vec3i origin = BlockPos.containing(x, y, z);
		int base = ParticleBuffer.getInstance()
				.allocate(capacity);
		return new ParticleEmitter(style, base, capacity, origin);
	}

	public int style() {
		return style;
	}

	public int slotBase() {
		return base;
	}

	public int capacity() {
		return capacity;
	}

	public Vec3i origin() {
		return origin;
	}

	public void spawn(double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			float life, float sizeScale) {
		spawn(x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, 0.0f, 1.0f);
	}

	public void spawn(double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			float life, float sizeScale, float spinPhase, float tintScale) {
		if (closed || life <= 0.0f) {
			return;
		}

		float now = ParticleClock.seconds();

		ParticleBuffer.getInstance()
				.write(base + cursor,
						(float) (x - origin.getX()),
						(float) (y - origin.getY()),
						(float) (z - origin.getZ()),
						now,
						(float) velocityX, (float) velocityY, (float) velocityZ,
						life, style, sizeScale, spinPhase, tintScale);

		cursor = (cursor + 1) % capacity;
		latestDeath = Math.max(latestDeath, now + life);
	}

	public boolean isIdle() {
		return ParticleClock.seconds() >= latestDeath;
	}

	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		ParticleBuffer.getInstance()
				.release(base, capacity);
	}
}
