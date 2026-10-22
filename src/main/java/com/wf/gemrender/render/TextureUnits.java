package com.wf.gemrender.render;

import org.lwjgl.opengl.GL13C;

import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;

public final class TextureUnits {
    public static final int BONES = Integer.getInteger("gemrender.boneunit", 14);

    public static final int MORPHS = Integer.getInteger("gemrender.morphunit", 15);

    public static final int PARTICLES = Integer.getInteger("gemrender.particleunit", 13);

    public static final int VOLUMES = Integer.getInteger("gemrender.volumeunit", 12);

    public static final int VOLUME_NOISE = Integer.getInteger("gemrender.volumenoiseunit", 16);

    public static final int SCENE_DEPTH = Integer.getInteger("gemrender.scenedepthunit", 17);

    public static final int VOLUME_FIELD = Integer.getInteger("gemrender.volumefieldunit", 18);

    public static final int PAINT = Integer.getInteger("gemrender.paintunit", 19);

    public static final int LIGHT_COOKIES = Integer.getInteger("gemrender.lightcookieunit", 20);

    public static final int LIGHT_GRID = Integer.getInteger("gemrender.lightgridunit", 21);

    public static final int LIGHT_OCCUPANCY = Integer.getInteger("gemrender.lightoccupancyunit", 22);

    public static final int LIGHT_SHADOWS = Integer.getInteger("gemrender.lightshadowunit", 23);

    /** Above guest coefficient sets 26... */
    public static final int ABSORBANCE_BAND = Integer.getInteger("gemrender.absorbancebandunit", 31);

    /** Iris guest OIT producers; reserved from the pack's sampler allocator. Coefficient sets 26.. */
    public static final int GUEST_OIT_DEPTH_RANGE = 24;

    public static final int GUEST_OIT_BLUE_NOISE = 25;

    public static final int GUEST_OIT_COEFFICIENTS = 26;

    private TextureUnits() {
    }

    public static int activate(int unit) {
        int previous = GlState.activeTexture();
        GL13C.glActiveTexture(GL_TEXTURE0 + unit);
        return previous;
    }

    public static void restore(int previous) {
        GL13C.glActiveTexture(previous);
    }
}
