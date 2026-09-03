package com.wf.gemrender.gltf;

/**
 * A driver played on a loop of its own, whatever clock it is handed.
 *
 * <p>Needed only where two animations of different lengths have to share one clock. A keyframe
 * channel handed a time past its last key clamps rather than wrapping — correct for a short channel
 * inside a longer clip, which is what glTF asks for — so merging a one-second clip into a
 * twenty-second layer would freeze it after the first second. Wrapping it at its own clip's length
 * first is what a loop means, and restores it.
 */
public record Looped(PoseDriver inner, float periodSeconds) implements PoseDriver {
	public static PoseDriver of(PoseDriver inner, float periodSeconds) {
		return periodSeconds <= 0.0f ? inner : new Looped(inner, periodSeconds);
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		float wrapped = timeSeconds % periodSeconds;
		inner.apply(wrapped < 0.0f ? wrapped + periodSeconds : wrapped, scratch);
	}

	@Override
	public float cycleSeconds() {
		return periodSeconds;
	}

	@Override
	public int offset() {
		return inner.offset();
	}
}
