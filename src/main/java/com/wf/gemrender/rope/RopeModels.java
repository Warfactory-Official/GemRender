package com.wf.gemrender.rope;

import com.wf.gemrender.particle.ParticleModels;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.CutoutShader;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.StandardMaterialShaders;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ready-made rope models. One per (texture, shape, material): every rope sharing one of these is one
 * draw call however many of them there are and however far apart they hang.
 */
public final class RopeModels {
    private static final Map<Key, Model> MODELS = new ConcurrentHashMap<>();

    private static final Map<Shape, RopeMesh> MESHES = new ConcurrentHashMap<>();

    private RopeModels() {
    }

    /** An opaque rope at the default tessellation. Cables, leads, mooring chain. */
    public static Model solid(ResourceLocation texture) {
        return solid(texture, RopeMesh.DEFAULT_RINGS, RopeMesh.DEFAULT_SIDES);
    }

    public static Model solid(ResourceLocation texture, int rings, int sides) {
        return model(texture, rings, sides, Transparency.OPAQUE, CutoutShaders.OFF,
                StandardMaterialShaders.DEFAULT);
    }

    /**
     * A rope whose texture has holes in it — chain link, rigging, a frayed cable. Cheaper than
     * modelling the gaps and it is what a chain texture wants.
     */
    public static Model cutout(ResourceLocation texture) {
        return cutout(texture, RopeMesh.DEFAULT_RINGS, RopeMesh.DEFAULT_SIDES);
    }

    public static Model cutout(ResourceLocation texture, int rings, int sides) {
        return model(texture, rings, sides, Transparency.OPAQUE, CutoutShaders.ONE_TENTH,
                StandardMaterialShaders.DEFAULT);
    }

    /**
     * A rope that takes on the colour of the water it hangs in, for anything moored below a surface.
     * Shares the water split's absorbance pass with {@link ParticleModels#absorbance}, so it is tinted
     * and faded by depth rather than drawn flat through a hundred blocks of ocean.
     */
    public static Model absorbance(ResourceLocation texture) {
        return absorbance(texture, RopeMesh.DEFAULT_RINGS, RopeMesh.DEFAULT_SIDES);
    }

    public static Model absorbance(ResourceLocation texture, int rings, int sides) {
        return model(texture, rings, sides, Transparency.ORDER_INDEPENDENT, CutoutShaders.EPSILON,
                ParticleModels.ABSORBANCE_SHADERS);
    }

    public static Model model(ResourceLocation texture, int rings, int sides,
                              Transparency transparency, CutoutShader cutout, MaterialShaders shaders) {
        return MODELS.computeIfAbsent(new Key(texture, rings, sides, transparency, cutout, shaders),
                key -> new SingleMeshModel(mesh(key.rings(), key.sides()),
                        material(key.texture(), key.transparency(), key.cutout(), key.shaders())));
    }

    /**
     * Shared, because a mesh is decided entirely by its tessellation — a rope's length, shape and
     * thickness are all on the instance.
     */
    public static RopeMesh mesh(int rings, int sides) {
        return MESHES.computeIfAbsent(new Shape(rings, sides), shape -> RopeMesh.of(shape.rings(), shape.sides()));
    }

    private static Material material(ResourceLocation texture, Transparency transparency,
                                     CutoutShader cutout, MaterialShaders shaders) {
        return SimpleMaterial.builder()
                .texture(texture)
                .transparency(transparency)
                .cutout(cutout)
                .shaders(shaders)
                // A capped tube is closed, so the far wall is never wanted and culling it is free.
                .backfaceCulling(true)
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                // Absorbance overlay.y = band share.
                .useOverlay(shaders != ParticleModels.ABSORBANCE_SHADERS && shaders != ParticleModels.GLOW_SHADERS)
                .mipmap(false)
                .build();
    }

    private record Key(ResourceLocation texture, int rings, int sides, Transparency transparency,
                       CutoutShader cutout, MaterialShaders shaders) {
    }

    private record Shape(int rings, int sides) {
    }
}
