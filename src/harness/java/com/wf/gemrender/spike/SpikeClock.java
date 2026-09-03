package com.wf.gemrender.spike;

import net.minecraft.world.level.Level;

public final class SpikeClock {
	private static final float FROZEN = Float.parseFloat(System.getProperty("gemrender.freeze", "-1"));

	private SpikeClock() {
	}

	public static boolean isFrozen() {
		return FROZEN >= 0.0f;
	}

	public static float frozenAt() {
		return FROZEN;
	}

	public static float seconds(Level level, float partialTick) {
		if (FROZEN >= 0.0f) {
			return FROZEN;
		}
		return (level.getGameTime() + partialTick) / 20.0f;
	}
}
