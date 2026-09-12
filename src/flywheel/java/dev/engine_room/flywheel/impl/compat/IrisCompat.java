package dev.engine_room.flywheel.impl.compat;

/**
 * Stubbed on 26.1: Iris has no 26.1 build, so its API is not on the classpath. See NOTICE.md.
 */
public final class IrisCompat {
	public static final boolean ACTIVE = false;

	private IrisCompat() {
	}

	public static boolean isShaderPackInUse() {
		return false;
	}

	public static boolean isRenderingShadowPass() {
		return false;
	}
}
