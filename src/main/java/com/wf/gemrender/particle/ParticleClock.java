package com.wf.gemrender.particle;

import com.wf.gemrender.mixin.LevelRendererAccessor;
import com.wf.gemrender.render.Vanilla;

import net.minecraft.client.Minecraft;

public final class ParticleClock {
    private ParticleClock() {
    }

    /** Tolerates partial-tick jitter; a seek or level swap resets the renderer's tick count by far more. */
    private static final float REWIND = 0.1f;
    private static float last;

    public static float seconds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer == null) {
            return 0.0f;
        }

        //? if <26.2 {
        int ticks = ((LevelRendererAccessor) minecraft.levelRenderer).gemrender$getTicks();
        //?} else {
        /*// getGameTime(): world age => float loses partial ticks. Hud ticks: unpaused, monotonic, small.
        int ticks = minecraft.gui.hud.getGuiTicks();
        *///?}
        float now = (ticks + Vanilla.partialTick()) / 20.0f;
        // Particles are timed against this clock: after it rewinds, older ones would reappear as it catches up
        // (a replay's shots drawn twice, once from the first pass and once from the replayed one).
        if (now < last - REWIND) {
            ParticleBuffer.getInstance().clearAll();
        }
        last = now;
        return now;
    }
}
