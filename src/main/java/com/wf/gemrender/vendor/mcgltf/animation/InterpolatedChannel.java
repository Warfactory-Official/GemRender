package com.wf.gemrender.vendor.mcgltf.animation;

import java.util.Arrays;

/**
 * One glTF animation channel, sampled into a caller-supplied buffer.
 *
 * <p>GEMRENDER: upstream MCglTF sampled <em>into the node it targets</em> -- {@code update(float)} called an
 * abstract {@code getListener()} that returned the live {@code NodeModel} translation/rotation/scale
 * array and overwrote it in place. That made the node graph shared mutable state owned by the model, so
 * two instances of one model at different animation times could not be sampled concurrently, or even
 * independently; GemRender serialised them behind a per-model lock.
 *
 * <p>Sampling is a pure function of time, so it is written as one here: {@link #sample} takes the
 * destination. Nothing about the interpolation changed -- the same keys, the same maths, the same
 * results -- only where the answer is written. See {@code com.wf.gemrender.gltf.NodeTable}.
 */
public abstract class InterpolatedChannel {

	/**
	 * The key frame times, in seconds
	 */
	protected final float[] timesS;

	public InterpolatedChannel(float[] timesS) {
		this.timesS = timesS;
	}

	public float[] getKeys() {
		return timesS;
	}

	/**
	 * Samples this channel at {@code timeS}.
	 *
	 * <p>GEMRENDER: replaces upstream's {@code update(float)}, which wrote into the target node.
	 *
	 * @param out    receives the sampled value
	 * @param offset where in {@code out} it starts
	 * @param count  how many components to write; never more than the channel's own component count, so
	 *               a channel bound to a shorter destination truncates rather than overrunning it
	 */
	public abstract void sample(float timeS, float[] out, int offset, int count);

	/**
	 * Compute the index of the segment that the given key belongs to.
	 * If the given key is smaller than the smallest or larger than
	 * the largest key, then 0 or <code>keys.length-1<code> will be returned,
	 * respectively.
	 *
	 * @param key The key
	 * @param keys The sorted keys
	 * @return The index for the key
	 */
	public static int computeIndex(float key, float keys[])
	{
		int index = Arrays.binarySearch(keys, key);
		if (index >= 0)
		{
		    return index;
		}
		return Math.max(0, -index - 2);
	}

	/**
	 * Normalizes four components of a quaternion in place.
	 *
	 * <p>GEMRENDER: upstream normalized on every read, inside jgltf's
	 * {@code MathUtils.quaternionToMatrix4x4}, which put a square root per rotated node per frame on the
	 * hot path. GemRender normalizes the <em>keyframes</em> at construction instead, and the interpolators
	 * preserve unit length from there: slerp of two unit quaternions is unit, and the cubic path
	 * normalizes its own Hermite output. So composing a local transform can assume unit length and skip
	 * the root.
	 */
	protected static void normalize(float[] values, int offset) {
		float x = values[offset];
		float y = values[offset + 1];
		float z = values[offset + 2];
		float w = values[offset + 3];

		float lengthSquared = x * x + y * y + z * z + w * w;
		if (lengthSquared > 1.0E-12F) {
			float inv = (float) (1.0 / Math.sqrt(lengthSquared));
			values[offset] = x * inv;
			values[offset + 1] = y * inv;
			values[offset + 2] = z * inv;
			values[offset + 3] = w * inv;
		}
	}

	/** Normalizes every keyframe of a rotation channel, once, at construction. */
	static void normalizeKeyframes(float[][] values) {
		for (float[] value : values) {
			if (value.length >= 4) {
				normalize(value, 0);
			}
		}
	}

}
