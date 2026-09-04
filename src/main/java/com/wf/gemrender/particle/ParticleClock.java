package com.wf.gemrender.particle;

import com.wf.gemrender.mixin.LevelRendererAccessor;

import net.minecraft.client.Minecraft;

public final class ParticleClock {
	private ParticleClock() {
	}

	public static float seconds() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer == null) {
			return 0.0f;
		}

		int ticks = ((LevelRendererAccessor) minecraft.levelRenderer).gemrender$getTicks();
		return (ticks + minecraft.getTimer()
				.getGameTimeDeltaPartialTick(false)) / 20.0f;
	}
}
