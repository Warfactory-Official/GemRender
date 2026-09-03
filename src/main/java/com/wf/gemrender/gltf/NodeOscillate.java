package com.wf.gemrender.gltf;

/**
 * Rocks one node back and forth about an axis: {@code base + amplitude * sin(2pi(t/period + phase))}.
 *
 * <p>The shape a limb makes. A walk cycle is six legs and two arms on one clock at eight different
 * phases, a pump arm is one node at one phase, and a wing is two nodes half a turn apart; all of them
 * are this driver repeated, which is what lets a whole gait be declared rather than tabulated.
 *
 * <p>Phase is in <em>turns</em>, not radians, because that is the unit a rig is reasoned about in: a
 * limb a quarter of a cycle behind another is {@code 0.25}, whatever the period.
 *
 * <p>Two of these on one node give it two axes, applied in the order they were added -- see
 * {@link NodeRotation#compose}. Put the constant part of an angle in {@code base} rather than in the
 * bone's rest rotation when a node has more than one axis: rest rotations compose before every driver,
 * so {@code Ry(a)Rz(b)} at rest with drivers on Y and Z gives {@code Ry(a)Rz(b)Ry(dy)Rz(dz)}, which is
 * not the {@code Ry(a+dy)Rz(b+dz)} the rig meant.
 */
public record NodeOscillate(int offset, float axisX, float axisY, float axisZ, float baseRadians,
		float amplitudeRadians, float periodSeconds, float phaseTurns) implements PoseDriver {
	private static final float TAU = (float) (Math.PI * 2.0);

	public static NodeOscillate about(NodeTable table, int slot, float axisX, float axisY, float axisZ,
			float baseRadians, float amplitudeRadians, float periodSeconds, float phaseTurns) {
		if (periodSeconds <= 0.0f) {
			throw new IllegalArgumentException("oscillation period must be positive, was " + periodSeconds);
		}

		float[] axis = NodeRotation.axis(axisX, axisY, axisZ);
		return new NodeOscillate(NodeRotation.offsetOf(table, slot), axis[0], axis[1], axis[2], baseRadians,
				amplitudeRadians, periodSeconds, phaseTurns);
	}

	/** A fixed angle: the same node, the same axis, nothing moving. */
	public static NodeOscillate fixed(NodeTable table, int slot, float axisX, float axisY, float axisZ,
			float radians) {
		return about(table, slot, axisX, axisY, axisZ, radians, 0.0f, 1.0f, 0.0f);
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		float angle = baseRadians;
		if (amplitudeRadians != 0.0f) {
			angle += amplitudeRadians * (float) Math.sin(TAU * (timeSeconds / periodSeconds + phaseTurns));
		}
		NodeRotation.compose(scratch, offset, axisX, axisY, axisZ, angle);
	}

	@Override
	public float cycleSeconds() {
		return amplitudeRadians == 0.0f ? 0.0f : periodSeconds;
	}
}
