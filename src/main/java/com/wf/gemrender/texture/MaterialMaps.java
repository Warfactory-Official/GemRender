package com.wf.gemrender.texture;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record MaterialMaps(@Nullable ResourceLocation baseColor, @Nullable ResourceLocation normal,
                           @Nullable ResourceLocation metallicRoughness, @Nullable ResourceLocation occlusion,
                           @Nullable ResourceLocation emissive, float baseColorR, float baseColorG, float baseColorB,
                           float baseColorA, float metallicFactor, float roughnessFactor, float normalScale,
                           float occlusionStrength, float emissiveR, float emissiveG, float emissiveB,
                           @Nullable ResourceLocation paint) {
    /**
     * Sibling of a base colour naming its paint mask: alpha = coverage, rgb = colour the base was painted
     * in. A variant's base keeps the model's mask unless it has its own sibling.
     */
    public static final String PAINT_SUFFIX = "_paint";

    /**
     * Sibling of a paint mask: red >= 128 => the paint's mean colour there instead of its pattern.
     */
    public static final String SOLID_SUFFIX = "_solid";

    public MaterialMaps(@Nullable ResourceLocation baseColor, @Nullable ResourceLocation normal,
                        @Nullable ResourceLocation metallicRoughness, @Nullable ResourceLocation occlusion,
                        @Nullable ResourceLocation emissive, float baseColorR, float baseColorG, float baseColorB,
                        float baseColorA, float metallicFactor, float roughnessFactor, float normalScale,
                        float occlusionStrength, float emissiveR, float emissiveG, float emissiveB) {
        this(baseColor, normal, metallicRoughness, occlusion, emissive, baseColorR, baseColorG, baseColorB,
                baseColorA, metallicFactor, roughnessFactor, normalScale, occlusionStrength, emissiveR, emissiveG,
                emissiveB, null);
    }

    public static MaterialMaps plain(@Nullable ResourceLocation baseColor) {
        return new MaterialMaps(baseColor, null, null, null, null, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 0.0f, 0.0f, 0.0f);
    }

    @Nullable
    private static ResourceLocation swap(@Nullable ResourceLocation texture,
                                         Map<ResourceLocation, ResourceLocation> swaps) {
        return texture == null ? null : swaps.getOrDefault(texture, texture);
    }

    public boolean pbr() {
        return paint != null || normal != null || metallicRoughness != null || emissive != null || emissiveR > 0.0f
                || emissiveG > 0.0f || emissiveB > 0.0f;
    }

    public MaterialMaps swapped(Map<ResourceLocation, ResourceLocation> swaps) {
        if (swaps.isEmpty()) {
            return this;
        }
        ResourceLocation swappedBase = swap(baseColor, swaps);
        ResourceLocation ownPaint = paint == null || swappedBase == baseColor ? null
                : ModelTextures.sibling(swappedBase, PAINT_SUFFIX);
        return new MaterialMaps(swappedBase, swap(normal, swaps),
                swap(metallicRoughness, swaps), swap(occlusion, swaps), swap(emissive, swaps),
                baseColorR, baseColorG, baseColorB, baseColorA, metallicFactor, roughnessFactor,
                normalScale, occlusionStrength, emissiveR, emissiveG, emissiveB,
                ownPaint != null ? ownPaint : swap(paint, swaps));
    }

    public MaterialMaps withoutMaps() {
        return new MaterialMaps(baseColor, null, null, null, null, baseColorR, baseColorG, baseColorB,
                baseColorA, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, null);
    }

    public boolean baseColorUnmodified() {
        return occlusion == null && baseColorR == 1.0f && baseColorG == 1.0f && baseColorB == 1.0f
                && baseColorA == 1.0f;
    }

    public String describe() {
        StringBuilder out = new StringBuilder();
        if (normal != null) {
            out.append(" +normal");
        }
        if (metallicRoughness != null) {
            out.append(" +metallicRoughness");
        }
        if (occlusion != null) {
            out.append(" +occlusion");
        }
        if (emissive != null) {
            out.append(" +emissive");
        } else if (emissiveR > 0.0f || emissiveG > 0.0f || emissiveB > 0.0f) {
            out.append(" +emissiveFactor");
        }
        if (paint != null) {
            out.append(" +paint");
        }
        if (!baseColorUnmodified()) {
            out.append(" +baked");
        }
        return out.toString();
    }
}
