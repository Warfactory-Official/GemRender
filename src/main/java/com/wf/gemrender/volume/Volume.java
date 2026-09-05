package com.wf.gemrender.volume;

public final class Volume {
	private final int slot;

	private VolumeStyle style;

	private float extentX = 1.0f;

	private float extentY = 1.0f;

	private float extentZ = 1.0f;

	private float fade = 1.0f;

	private float seed;

	private boolean closed;

	private Volume(int slot, VolumeStyle style) {
		this.slot = slot;
		this.style = style;
	}

	public static Volume create(VolumeStyle style) {
		Volume volume = new Volume(VolumeBuffer.getInstance()
				.allocate(), style);
		volume.flush();
		return volume;
	}

	public int slot() {
		return slot;
	}

	public Volume style(VolumeStyle value) {
		style = value;
		flush();
		return this;
	}

	public Volume extent(float x, float y, float z) {
		extentX = x;
		extentY = y;
		extentZ = z;
		flush();
		return this;
	}

	public Volume fade(float value) {
		fade = value;
		flush();
		return this;
	}

	public Volume seed(float value) {
		seed = value;
		flush();
		return this;
	}

	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		VolumeBuffer.getInstance()
				.release(slot);
	}

	private void flush() {
		if (closed) {
			return;
		}

		VolumeBuffer.getInstance()
				.write(slot, style, extentX, extentY, extentZ, fade, seed);
	}
}
