package com.wf.gemrender.direct;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Texture per slot name ({@link com.wf.gemrender.gltf.GemRenderGltfModel#slotMeshes}) for one submit; {@code null} =>
 * that slot's meshes not drawn. Batched per (mesh, texture).
 */
@FunctionalInterface
public interface TextureSlots {
    TextureSlots NONE = slot -> null;

    @Nullable
    ResourceLocation texture(String slot);

    /** {@code true} => {@code slot}'s meshes unlit: texture colour as is, no shading, lightmap or dynamic lights. */
    default boolean emissive(String slot) {
        return false;
    }

    /**
     * {@code true} (and not {@link #emissive}) => {@code slot}'s texture alpha is a per-pixel emission mask: 1 = unlit,
     * 0 = lit as the vehicle is; alpha is not coverage.
     */
    default boolean emissionMask(String slot) {
        return false;
    }
}
