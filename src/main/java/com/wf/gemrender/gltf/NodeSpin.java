package com.wf.gemrender.gltf;

/**
 * Spins one node at a constant rate, which can come from throughput, a fluid level or redstone.
 *
 * <p>The angle is {@code rate * time} rather than integrated, so changing the rate is discontinuous.
 */
public record NodeSpin(int offset, float axisX, float axisY, float axisZ, float turnsPerSecond)
		implements PoseDriver {
	private static final float TAU = (float) (Math.PI * 2.0);

	public static NodeSpin about(NodeTable table, int slot, float axisX, float axisY, float axisZ,
			float turnsPerSecond) {
		float[] axis = NodeRotation.axis(axisX, axisY, axisZ);
		return new NodeSpin(NodeRotation.offsetOf(table, slot), axis[0], axis[1], axis[2], turnsPerSecond);
	}

	public static NodeSpin aboutY(NodeTable table, int slot, float turnsPerSecond) {
		return about(table, slot, 0.0f, 1.0f, 0.0f, turnsPerSecond);
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		NodeRotation.compose(scratch, offset, axisX, axisY, axisZ, TAU * turnsPerSecond * timeSeconds);
	}

	@Override
	public float cycleSeconds() {
		return turnsPerSecond == 0.0f ? 0.0f : Math.abs(1.0f / turnsPerSecond);
	}
}
