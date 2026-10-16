package com.wf.gemrender.direct;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shaderpack route for {@link DirectPass#LEVEL}, {@link DirectPass#HAND} and {@link DirectPass#SHADOW}: copies draw
 * through the pack's own programs. {@link DirectPass#GUI} never takes it.
 */
public interface DirectGuest {
    boolean active();

    boolean shadowPass();

    /** Bones and morphs uploaded and bound; {@code batches} non-empty. */
    void flush(DirectPass pass, List<? extends Batch> batches);

    /** Resource reload: GemRender's GLSL may have changed. */
    void release();

    interface Batch {
        DirectMaterial material();

        ResourceLocation texture();

        int instances();

        /** Guest VAO: locations 0..14 as {@code direct.vert}, 15 = {@code ResidentMesh} extras. */
        void draw();
    }
}
