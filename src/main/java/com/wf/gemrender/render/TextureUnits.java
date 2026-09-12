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
