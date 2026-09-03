package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator;

/**
 * The one thing every rotational {@link PoseDriver} does: turn a node about an axis, on top of whatever
 * the drivers before it left there.
 *
 * <p>Public because {@code PoseDriver} is an extension point and a driver written outside GemRender
 * needs this to write a rotation correctly. The pose scratch holds a node's rotation as a raw
 * quaternion, and the two mistakes that shape invites -- overwriting it rather than composing onto it,
 * and composing on the wrong side -- both produce a rig that is right until a second driver touches the
 * same node.
 *
 * <p>{@link #compose} multiplies on the <em>right</em>, so drivers stack in the order they were added
 * the same way {@code matrix.rotateY(a).rotateZ(b)} does. A node that needs two axes takes two drivers,
 * declared in that order.
 */
public final class NodeRotation {
	private NodeRotation() {
	}

	/**
	 * The offset into the pose scratch where {@code slot}'s rotation quaternion lives.
	 *
	 * @throws IllegalArgumentException if the slot is not a node of this table
	 */
	public static int offsetOf(NodeTable table, int slot) {
		int offset = table.offsetFor(slot, GltfAnimationCreator.ROTATION_PATH);
		if (offset < 0) {
			throw new IllegalArgumentException("no node in slot " + slot + " to rotate");
		}
		return offset;
	}

	/** A unit axis, or a thrown exception for one of length zero, which would rotate about nothing. */
	public static float[] axis(float x, float y, float z) {
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		if (length < 1.0e-6f) {
			throw new IllegalArgumentException("rotation axis is zero-length");
		}
		return new float[] { x / length, y / length, z / length };
	}

	/**
	 * Turns the node at {@code offset} by {@code angleRadians} about a <b>unit</b> axis, composing onto
	 * the rotation already in the scratch rather than replacing it.
	 */
	public static void compose(float[] scratch, int offset, float axisX, float axisY, float axisZ,
			float angleRadians) {
		float half = angleRadians * 0.5f;
		float sin = (float) Math.sin(half);

		float sx = axisX * sin;
		float sy = axisY * sin;
		float sz = axisZ * sin;
		float sw = (float) Math.cos(half);

		float bx = scratch[offset];
		float by = scratch[offset + 1];
		float bz = scratch[offset + 2];
		float bw = scratch[offset + 3];

		scratch[offset] = bw * sx + bx * sw + by * sz - bz * sy;
		scratch[offset + 1] = bw * sy - bx * sz + by * sw + bz * sx;
		scratch[offset + 2] = bw * sz + bx * sy - by * sx + bz * sw;
		scratch[offset + 3] = bw * sw - bx * sx - by * sy - bz * sz;
	}
}
