package com.wf.gemrender.gltf;

/**
 * Sweeps one node between two stops: the hinge. A gripper opening, a hatch, a landing gear, a barrel
 * elevating.
 *
 * <p>The clip is one second long by construction, so its clip-local time <em>is</em> the fraction of
 * the sweep, and {@link AnimationDrive#ranged} maps whatever the game actually measures -- a byte of
 * deploy progress, a target elevation in degrees -- onto it. Beyond either end it holds the stop, which
 * is what a hinge does.
 *
 * @see NodeSpin for a part that turns without end rather than between stops
 */
public record NodeSwing(int offset, float axisX, float axisY, float axisZ, float fromRadians,
		float toRadians) implements PoseDriver {
	public static NodeSwing about(NodeTable table, int slot, float axisX, float axisY, float axisZ,
			float fromRadians, float toRadians) {
		float[] axis = NodeRotation.axis(axisX, axisY, axisZ);
		return new NodeSwing(NodeRotation.offsetOf(table, slot), axis[0], axis[1], axis[2], fromRadians,
				toRadians);
	}

	/** A hinge that starts closed, which is how most of them are modelled. */
	public static NodeSwing open(NodeTable table, int slot, float axisX, float axisY, float axisZ,
			float openRadians) {
		return about(table, slot, axisX, axisY, axisZ, 0.0f, openRadians);
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		float unit = Math.min(1.0f, Math.max(0.0f, timeSeconds));
		NodeRotation.compose(scratch, offset, axisX, axisY, axisZ,
				fromRadians + (toRadians - fromRadians) * unit);
	}

	@Override
	public float cycleSeconds() {
		return 1.0f;
	}
}
