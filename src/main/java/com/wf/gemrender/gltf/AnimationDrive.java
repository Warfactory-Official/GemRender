package com.wf.gemrender.gltf;

/**
 * A clip plus how a parameter drives it: the sibling of {@link AnimationPhase} for motion that
 * answers to something other than the world clock. A wheel turns because the vehicle moved, a turret
 * sits where its gunner aimed it, a landing gear is half way down; none of those are a function of
 * what time it is.
 *
 * <p>Both hand back the same currency -- clip-local seconds -- so a drive goes anywhere a phase goes
 * and everything downstream is unchanged. Nothing between here and the palette inspects what the
 * float meant, so a layer driven by a steering angle buckets, shares and skips exactly as one driven
 * by a clock does. See {@link PoseDriver} for why that holds.
 *
 * <p>The two factories are the only two shapes a vehicle turns out to need. {@link #cyclic} is for a
 * quantity that accumulates without bound and means the same thing every cycle: distance travelled,
 * an axle's angle. {@link #ranged} is for one that lives between two stops: a steering angle, an
 * elevation, how far a door has opened. Give either a clip and the parameter, not degrees -- a wheel
 * animated as a full turn is {@code cyclic(spin, 2 * PI * radius)} read straight off the odometer.
 *
 * <p>Scrubbing a clip rather than rotating a bone is what makes this worth having: the parameter can
 * drive any keyframed motion, so a suspension arm that also compresses, or a hatch that rotates and
 * slides, costs no more than a single-axis spin.
 */
public record AnimationDrive(GltfAnimation clip, float from, float to, boolean cyclic) {
	/**
	 * A clip the parameter runs through once every {@code unitsPerCycle}, and keeps running: 2.25
	 * cycles is a quarter of the way in, and driving backwards runs it backwards.
	 */
	public static AnimationDrive cyclic(GltfAnimation clip, float unitsPerCycle) {
		return new AnimationDrive(clip, 0.0f, unitsPerCycle, true);
	}

	/**
	 * A clip the parameter scrubs between two stops, holding the first frame at or below {@code min}
	 * and the last at or above {@code max}. Passing {@code max} below {@code min} runs it the other way,
	 * which is the same thing an inverted binding does.
	 */
	public static AnimationDrive ranged(GltfAnimation clip, float min, float max) {
		return new AnimationDrive(clip, min, max, false);
	}

	/**
	 * Clip-local time for {@code parameter}, in the same units and the same range as
	 * {@link AnimationPhase#timeAt}.
	 */
	public float timeAt(float parameter) {
		if (clip == null || from == to) {
			return 0.0f;
		}

		float unit = (parameter - from) / (to - from);
		if (cyclic) {
			return clip.loop(unit * clip.duration());
		}
		return Math.min(1.0f, Math.max(0.0f, unit)) * clip.duration();
	}

	/** True once the parameter has run past a ranged drive's last stop, so a caller can act on arrival. */
	public boolean isAtEnd(float parameter) {
		return !cyclic && from != to && (parameter - from) / (to - from) >= 1.0f;
	}
}
