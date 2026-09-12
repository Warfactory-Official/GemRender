package com.wf.gemrender.direct;

import com.wf.gemrender.GemRender;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record DirectMaterial(ResourceLocation texture, Mode mode, float alphaCutoff, boolean doubleSided) {
    public static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    @Nullable
    public static DirectMaterial of(Material material) {
        if (!material.writeMask()
                .color()) {
            return null;
        }

        Mode mode;
        float cutoff = 0.0f;
        if (material.transparency() == Transparency.OPAQUE) {
            if (material.cutout() == CutoutShaders.OFF) {
                mode = Mode.OPAQUE;
            } else {
                mode = Mode.MASK;
                cutoff = cutoffOf(material);
            }
        } else {
            mode = Mode.BLEND;
        }

        ResourceLocation texture = material.texture();

        if (texture == null || TextureAtlas.LOCATION_BLOCKS.equals(texture)) {
            texture = WHITE;
        }

        return new DirectMaterial(texture, mode, cutoff, !material.backfaceCulling());
    }

    private static float cutoffOf(Material material) {
        if (material.cutout() == CutoutShaders.HALF) {
            return 0.5f;
        }
        if (material.cutout() == CutoutShaders.ONE_TENTH) {
            return 0.1f;
        }
        if (material.cutout() == CutoutShaders.EPSILON) {
            return 0.0001f;
        }

        GemRender.LOGGER.warn("Material has cutout shader {}, which the direct path does not know a "
                + "threshold for; masking at 0.1 instead", material.cutout());
        return 0.1f;
    }

    public boolean blended() {
        return mode == Mode.BLEND;
    }

    public enum Mode {

        OPAQUE,

        MASK,

        BLEND
    }
}
