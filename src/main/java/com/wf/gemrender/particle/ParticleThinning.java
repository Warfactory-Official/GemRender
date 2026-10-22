package com.wf.gemrender.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Spawn-time thinning by projected size: mid-life radius under {@link #PX} pixels => kept with probability
 * {@code (px / PX)^2}, survivor size x {@code 1/sqrt(keep)} => covered area conserved, count cut. Floor
 * {@link #MIN_KEEP}. {@code -Dgemrender.particleThinPx=0} off.
 */
public final class ParticleThinning {
    private static final float PX = Float.parseFloat(System.getProperty("gemrender.particleThinPx", "3"));
    private static final float MIN_KEEP = 1.0f / 64.0f;

    private static long seed = 0x9E3779B97F4A7C15L;
    public static long kept;
    public static long dropped;

    private ParticleThinning() {
    }

    /** @return keep probability in (0, 1] */
    static float keep(double x, double y, double z, int style, float sizeScale) {
        if (PX <= 0.0f) {
            return 1.0f;
        }
        ParticleStyle s = ParticleBuffer.getInstance().style(style);
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.gameRenderer == null) {
            return 1.0f;
        }
        //? if >=26.2 {
        /*Vec3 eye = mc.gameRenderer.mainCamera().position();
        *///?} else if >=26.1 {
        /*Vec3 eye = mc.gameRenderer.getMainCamera().position();
        *///?} else {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        //?}
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        float radius = ParticleMotion.size(s, sizeScale, 0.5f);
        double focal = mc.getWindow().getHeight() * 0.5 / Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
        double px2 = radius * radius * focal * focal / Math.max(d2, 1.0);
        if (px2 >= PX * PX) {
            kept++;
            return 1.0f;
        }
        return Math.max(MIN_KEEP, (float) (px2 / (PX * PX)));
    }

    static boolean drop(float keep) {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        boolean drop = (seed >>> 40) * 0x1.0p-24f >= keep;
        if (drop) {
            dropped++;
        } else {
            kept++;
        }
        return drop;
    }
}
