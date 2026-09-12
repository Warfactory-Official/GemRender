package com.wf.gemrender.particle;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.water.Absorbance;
import dev.engine_room.flywheel.api.material.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.*;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleModels {
    public static final MaterialShaders ABSORBANCE_SHADERS = new SimpleMaterialShaders(
            ResourceLocation.fromNamespaceAndPath("flywheel", "material/default.vert"),
            ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "material/absorbance.frag"));

    private static final Map<Key, Model> BILLBOARDS = new ConcurrentHashMap<>();

    static {
        Absorbance.getInstance()
                .register(ABSORBANCE_SHADERS);
    }

    private ParticleModels() {
    }

    public static Model additive(ResourceLocation texture) {
        return billboard(texture, Transparency.ADDITIVE);
    }

    public static Model translucent(ResourceLocation texture) {
        return billboard(texture, Transparency.ORDER_INDEPENDENT);
    }

    public static Model absorbance(ResourceLocation texture) {
        return BILLBOARDS.computeIfAbsent(
                new Key(texture, Transparency.ORDER_INDEPENDENT, CutoutShaders.EPSILON, ABSORBANCE_SHADERS),
                key -> new SingleMeshModel(ParticleQuad.INSTANCE, SimpleMaterial.builder()
                        .texture(key.texture())
                        .transparency(key.transparency())
                        .cutout(key.cutout())
                        .shaders(key.shaders())
                        .fog(FogShaders.NONE)
                        .blur(true)
                        .backfaceCulling(false)
                        .cardinalLightingMode(CardinalLightingMode.OFF)
                        .mipmap(false)
                        .build()));
    }

    public static Model cutout(ResourceLocation texture) {
        return billboard(texture, Transparency.OPAQUE, CutoutShaders.ONE_TENTH);
    }

    public static Model blended(ResourceLocation texture) {
        return BILLBOARDS.computeIfAbsent(new Key(texture, Transparency.TRANSLUCENT, CutoutShaders.EPSILON,
                        StandardMaterialShaders.DEFAULT),
                key -> new SingleMeshModel(ParticleQuad.INSTANCE,
                        SimpleMaterial.builder()
                                .texture(key.texture())
                                .transparency(Transparency.TRANSLUCENT)
                                .cutout(key.cutout())
                                .writeMask(WriteMask.COLOR)
                                .backfaceCulling(false)
                                .cardinalLightingMode(CardinalLightingMode.OFF)
                                .mipmap(false)
                                .build()));
    }

    public static Model billboard(ResourceLocation texture, Transparency transparency) {
        return billboard(texture, transparency, CutoutShaders.EPSILON);
    }

    public static Model billboard(ResourceLocation texture, Transparency transparency, CutoutShader cutout) {
        return BILLBOARDS.computeIfAbsent(new Key(texture, transparency, cutout, StandardMaterialShaders.DEFAULT),
                key -> new SingleMeshModel(ParticleQuad.INSTANCE,
                        material(key.texture(), key.transparency(), key.cutout())));
    }

    public static Material material(ResourceLocation texture, Transparency transparency) {
        return material(texture, transparency, CutoutShaders.EPSILON);
    }

    public static Material material(ResourceLocation texture, Transparency transparency, CutoutShader cutout) {
        return SimpleMaterial.builder()
                .texture(texture)
                .transparency(transparency)
                .cutout(cutout)
                .backfaceCulling(false)
                .cardinalLightingMode(CardinalLightingMode.OFF)
                .mipmap(false)
                .build();
    }

    private record Key(ResourceLocation texture, Transparency transparency, CutoutShader cutout,
                       MaterialShaders shaders) {
    }
}
