package com.wf.gemrender.gltf;

/** A clip plus where in it an instance is, as a pure function of the world clock. */
public record AnimationPhase(GltfAnimation clip, float offsetSeconds, float speed) {
	public static final AnimationPhase REST = new AnimationPhase(null, 0.0f, 0.0f);

	private static final int PHASE_STEPS = Integer.getInteger("gemrender.phasesteps", 0);

	public static AnimationPhase of(GltfAnimation clip) {
		return new AnimationPhase(clip, 0.0f, 1.0f);
	}

	/** Offset by a stable hash of {@code seed}, so copies form a field rather than a chorus line. */
	public static AnimationPhase scattered(GltfAnimation clip, long seed) {
		if (clip == null || clip.duration() <= 0.0f) {
			return REST;
		}
		return new AnimationPhase(clip, scatterOffset(seed, clip.duration()), 1.0f);
	}

	/**
	 * The offset {@link #scattered} would give a clip {@code spanSeconds} long. Layers of one copy have
	 * to share an offset in seconds rather than each scattering into its own length, or the copy is at
	 * two unrelated instants and no single-clock path can draw the same thing.
	 */
	public static float scatterOffset(long seed, float spanSeconds) {
		return spanSeconds <= 0.0f ? 0.0f : unitFraction(seed) * spanSeconds;
	}

	public AnimationPhase withOffset(float offsetSeconds) {
		return new AnimationPhase(clip, offsetSeconds, speed);
	}

	public AnimationPhase withSpeed(float speed) {
		return new AnimationPhase(clip, offsetSeconds, speed);
	}

	/** Clip-local time for {@code seconds}, wrapped into the clip. */
	public float timeAt(float seconds) {
		if (clip == null) {
			return 0.0f;
		}
		return clip.loop(seconds * speed + offsetSeconds);
	}

	public boolean isStatic() {
		return clip == null || speed == 0.0f;
	}

	/**
	 * Rounds a phase in [0, 1) down onto a grid of {@code steps} values, or returns it unchanged for
	 * {@code steps <= 0}.
	 *
	 * <p>A continuous offset gives every copy its own instant, so copies meet in the pose cache only by
	 * birthday: with a cycle divided into B buckets and N copies, the distinct poses a frame asks for
	 * are about B(1 - (1 - 1/B)^N), which is nearly N whenever B is the larger. Drawing offsets from a
	 * fixed set instead makes that a cap rather than a coincidence – at most {@code steps} poses however
	 * large the crowd, and however many layers ride the same clock.
	 */
	public static float snap(float unit, int steps) {
		return steps <= 0 ? unit : Math.min((int) (unit * steps), steps - 1) / (float) steps;
	}

	private static float unitFraction(long seed) {
		long z = seed + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);

		return snap((z >>> 40) / (float) (1 << 24), PHASE_STEPS);
	}
}
