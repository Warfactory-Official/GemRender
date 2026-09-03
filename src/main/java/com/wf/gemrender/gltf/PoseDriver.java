package com.wf.gemrender.gltf;

/**
 * Writes into a pose; glTF keyframe channels and procedural drivers are peers.
 *
 * <p>Implementations must be a pure function of {@code timeSeconds} and have value equality.
 * Instances landing on one instant share a single evaluation, so a driver that reads mutable
 * state hands its value to every instance in that bucket.
 *
 * <p>Purity is the whole of the rule; being a clock is not. A driver may read {@code timeSeconds} as
 * any scalar the caller has agreed to -- a steering angle, a wheel's rotation, how far a door has
 * travelled -- because the pose cache never interprets it, only buckets it. What the rule forbids is
 * the shape that invites: reaching a per-instance quantity through a field rather than receiving it
 * through the argument. A driver that does is no longer a value, two callers holding equal drivers no
 * longer mean the same pose, and the instance that shares a bucket with it silently gets somebody
 * else's answer.
 *
 * @see com.wf.gemrender.render.PoseCache#pose
 */
public interface PoseDriver {
	void apply(float timeSeconds, float[] scratch);

	float cycleSeconds();

	/**
	 * Where in the pose this writes, so a caller can work out what the clip can and cannot move; -1
	 * for a driver that will not say, which callers must read as "anywhere".
	 *
	 * @see NodeTable#slotOfOffset(int)
	 */
	default int offset() {
		return -1;
	}
}
