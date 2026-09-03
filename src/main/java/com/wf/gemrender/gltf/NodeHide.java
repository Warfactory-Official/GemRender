package com.wf.gemrender.gltf;

import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.SCALE_PATH;

/**
 * Collapses one node, and everything hanging off it, to nothing.
 *
 * <p>Damage state, hardpoints, a variant that is missing a part: geometry a copy of the asset does not
 * have this frame. On the skinned path there is no per-part instance to leave out, so removing a part
 * means posing it to zero size, and a bone whose scale is zero takes its whole subtree with it because
 * the palette composes down the chain.
 *
 * <p>Its cycle is zero, so a clip made only of these has no duration and every instance carrying it
 * lands in one time bucket. That is what makes a set of damage states cheap: the pose cache separates
 * them by clip identity, not by clock, so a swarm showing five different states costs five poses rather
 * than five per instant.
 *
 * <p>Nothing is culled by this. The vertices still go through the vertex shader and collapse to a point,
 * which the rasteriser then throws away; it is the same trade as Flywheel's own zero transform.
 */
public record NodeHide(int offset) implements PoseDriver {
	public static NodeHide of(NodeTable table, int slot) {
		int offset = table.offsetFor(slot, SCALE_PATH);
		if (offset < 0) {
			throw new IllegalArgumentException("no node in slot " + slot + " to hide");
		}
		return new NodeHide(offset);
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		scratch[offset] = 0.0f;
		scratch[offset + 1] = 0.0f;
		scratch[offset + 2] = 0.0f;
	}

	@Override
	public float cycleSeconds() {
		return 0.0f;
	}
}
