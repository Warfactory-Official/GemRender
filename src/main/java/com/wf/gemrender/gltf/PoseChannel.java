package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.mcgltf.animation.InterpolatedChannel;

public record PoseChannel(InterpolatedChannel channel, int offset, int count) implements PoseDriver {
	@Override
	public void apply(float timeSeconds, float[] scratch) {
		channel.sample(timeSeconds, scratch, offset, count);
	}

	@Override
	public float cycleSeconds() {
		float[] keys = channel.getKeys();
		return keys.length == 0 ? 0.0f : keys[keys.length - 1];
	}
}
