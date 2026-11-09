package com.wf.gemrender.volume;

import com.wf.gemrender.Ids;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleQuad;
import com.wf.gemrender.water.Absorbance;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

public final class VolumeModels {
    public static final MaterialShaders VOLUME_SHADERS = new SimpleMaterialShaders(
            Ids.of(GemRender.MOD_ID, "material/absorbance.vert"),
            Ids.of(GemRender.MOD_ID, "material/volume.frag"));

    public static final MaterialShaders EFFECT_SHADERS = new SimpleMaterialShaders(
            Ids.of(GemRender.MOD_ID, "material/absorbance.vert"),
            Ids.of(GemRender.MOD_ID, "material/volume_effect.frag"));

    private static final ResourceLocation WHITE =
            Ids.vanilla("textures/misc/white.png");

    private static final Object LOCK = new Object();

    private static Model cloud;
    private static Model effect;

    static {
        Absorbance.getInstance()
                .register(VOLUME_SHADERS);
        Absorbance.getInstance()
                .register(EFFECT_SHADERS, true);
    }

    private VolumeModels() {
    }

    public static Model cloud() {
        synchronized (LOCK) {
            if (cloud == null) {
                cloud = model(WHITE, VOLUME_SHADERS);
            }
            return cloud;
        }
    }

    /** Procedural smoke, fire and energy shells; use with {@link GemRenderVolumeTypes#VOLUME}. */
    public static Model effect() {
        synchronized (LOCK) {
            if (effect == null) {
                effect = model(Ids.of(GemRender.MOD_ID, "textures/fx/volume_noise.png"), EFFECT_SHADERS);
            }
            return effect;
        }
    }

    private static Model model(ResourceLocation texture, MaterialShaders shaders) {
        return new SingleMeshModel(ParticleQuad.INSTANCE, SimpleMaterial.builder()
                        .texture(texture)
                        .transparency(Transparency.ORDER_INDEPENDENT)
                        // The marcher discards empty pixels; a cutout adds a late gl_FragDepth declaration.
                        .cutout(CutoutShaders.OFF)
                        .shaders(shaders)
                        .writeMask(WriteMask.COLOR)
                        .fog(FogShaders.NONE)
                        .blur(true)
                        .mipmap(false)
                        .backfaceCulling(false)
                        .useOverlay(false)
                        .useLight(true)
                        .cardinalLightingMode(CardinalLightingMode.OFF)
                        .build());
    }
}
