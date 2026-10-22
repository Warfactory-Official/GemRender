package dev.engine_room.flywheel.impl.event;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The projection the level is drawn with this frame, view bob included, captured by
 * {@code GameRendererMixin} before the level renders.
 */
public final class LevelProjection {
	private static final Matrix4f PROJECTION = new Matrix4f();

	private LevelProjection() {
	}

	public static void capture(Matrix4fc projection) {
		PROJECTION.set(projection);
	}

	public static Matrix4f copy() {
		return new Matrix4f(PROJECTION);
	}
}
