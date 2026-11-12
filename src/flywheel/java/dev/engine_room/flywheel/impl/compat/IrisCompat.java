package dev.engine_room.flywheel.impl.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import dev.engine_room.flywheel.impl.FlwImpl;

/**
 * Upstream's Iris integration, reached through Iris's stable {@code IrisApi} by method handle: GemRender does not compile
 * against Iris on 26.1. Without it Flywheel's backends never stand down under a pack and draw outside the pack's targets.
 */
public final class IrisCompat {
	public static final boolean ACTIVE;
	private static final MethodHandle GET_INSTANCE;
	private static final MethodHandle IN_USE;
	private static final MethodHandle SHADOW_PASS;

	static {
		MethodHandle getInstance = null, inUse = null, shadowPass = null;
		if (CompatMod.IRIS.isLoaded) {
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				getInstance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
				inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
				shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
				FlwImpl.LOGGER.debug("Detected Iris");
			} catch (ReflectiveOperationException | LinkageError e) {
				FlwImpl.LOGGER.error("Iris is loaded but its API could not be reached; shader packs will not be detected", e);
				getInstance = null;
			}
		}
		ACTIVE = getInstance != null;
		GET_INSTANCE = getInstance;
		IN_USE = inUse;
		SHADOW_PASS = shadowPass;
	}

	private IrisCompat() {
	}

	public static boolean isShaderPackInUse() {
		return ACTIVE && ask(IN_USE);
	}

	public static boolean isRenderingShadowPass() {
		return ACTIVE && ask(SHADOW_PASS);
	}

	private static boolean ask(MethodHandle query) {
		try {
			return (boolean) query.invoke(GET_INSTANCE.invoke());
		} catch (Throwable e) {
			// IrisApi's getters do not throw; a failure here means Iris changed its API
			return false;
		}
	}
}
