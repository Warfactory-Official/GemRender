package com.wf.gemrender.gltf;

import com.wf.gemrender.GemRender;
import dev.engine_room.flywheel.api.material.*;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record GltfMaterial(@Nullable ResourceLocation texture, AlphaMode alphaMode, float alphaCutoff,
                           boolean doubleSided, boolean pbr) {
    public static final ResourceLocation UNTEXTURED =
            ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "untextured");
    public static final float[] CUTOUTS = {0.0f, 0.1f, 0.5f};
    private static final boolean OIT = !"false".equalsIgnoreCase(System.getProperty("gemrender.oit"));
    private static final MaterialShaders PBR_SHADERS = new SimpleMaterialShaders(
            ResourceLocation.fromNamespaceAndPath("flywheel", "material/default.vert"),
            ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "material/pbr.frag"));

    public GltfMaterial(@Nullable ResourceLocation texture, AlphaMode alphaMode, float alphaCutoff,
                        boolean doubleSided) {
        this(texture, alphaMode, alphaCutoff, doubleSided, false);
    }

    public GltfMaterial {
        alphaCutoff = alphaMode == AlphaMode.MASK ? quantiseCutoff(alphaCutoff) : 0.0f;
    }

    @Nullable
    public static Material depthPassFor(Material colour) {
        if (colour.transparency() != Transparency.TRANSLUCENT || colour.writeMask()
                .depth()) {
            return null;
        }

        return SimpleMaterial.builderOf(colour)
                .writeMask(WriteMask.DEPTH)
                .cutout(CutoutShaders.EPSILON)
                .build();
    }

    public static float quantiseCutoff(float cutoff) {
        float chosen = CUTOUTS[0];
        for (float candidate : CUTOUTS) {
            if (Math.abs(cutoff - candidate) < Math.abs(cutoff - chosen)) {
                chosen = candidate;
            }
        }

        if (Math.abs(cutoff - chosen) > 0.05f) {
            GemRender.LOGGER.warn("glTF alphaCutoff {} has no Flywheel cutout shader; using {} instead. "
                            + "Fragments between the two thresholds will render as though masked at {}.",
                    cutoff, chosen, chosen);
        }
        return chosen;
    }

    public GltfMaterial onTexture(ResourceLocation replacement, boolean banded) {
        return new GltfMaterial(replacement, alphaMode, alphaCutoff, doubleSided, pbr && banded);
    }

    public boolean orderIndependent() {
        return OIT && alphaMode == AlphaMode.BLEND;
    }

    public Material toFlywheel() {
        SimpleMaterial.Builder builder = SimpleMaterial.builder();
        if (!UNTEXTURED.equals(texture) && texture != null) {
            builder.texture(texture);
        }

        if (pbr) {
            builder.shaders(PBR_SHADERS);

            builder.cardinalLightingMode(CardinalLightingMode.OFF);
        }

        builder.mipmap(false);

        builder.backfaceCulling(!doubleSided);

        switch (alphaMode) {
            case OPAQUE -> {
                builder.transparency(Transparency.OPAQUE);
                builder.cutout(CutoutShaders.OFF);
            }
            case MASK -> {
                builder.transparency(Transparency.OPAQUE);
                builder.cutout(cutout());
            }
            case BLEND -> {
                if (orderIndependent()) {
                    builder.transparency(Transparency.ORDER_INDEPENDENT);
                } else {
                    builder.transparency(Transparency.TRANSLUCENT);
                    builder.writeMask(WriteMask.COLOR);
                }

                builder.cutout(CutoutShaders.OFF);
            }
        }

        return builder.build();
    }

    private CutoutShader cutout() {
        if (alphaCutoff >= 0.5f) {
            return CutoutShaders.HALF;
        }
        return alphaCutoff >= 0.1f ? CutoutShaders.ONE_TENTH : CutoutShaders.EPSILON;
    }

    public String describe() {
        return switch (alphaMode) {
            case OPAQUE -> "opaque";
            case MASK -> "mask@" + alphaCutoff;

            case BLEND -> orderIndependent() ? "blend/oit" : "blend/sorted+depth";
        } + (doubleSided ? " two-sided" : "") + (pbr ? " pbr" : "");
    }

    public enum AlphaMode {
        OPAQUE,

        MASK,

        BLEND
    }
}
