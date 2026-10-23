package com.wf.gemrender.particle;

import com.wf.gemrender.Ids;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.water.Absorbance;
import dev.engine_room.flywheel.api.material.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.*;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfMesh;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleModels {
    public static final MaterialShaders ABSORBANCE_SHADERS = new SimpleMaterialShaders(
            Ids.of(GemRender.MOD_ID, "material/absorbance.vert"),
            Ids.of(GemRender.MOD_ID, "material/absorbance.frag"));

    private static final Map<Key, Model> BILLBOARDS = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, Model> DECALS = new ConcurrentHashMap<>();

    private static final Map<GemRenderGltfModel, Map<Matrix4fc, Model>> RIGID =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static final MaterialShaders GLOW_SHADERS = new SimpleMaterialShaders(
            Ids.of(GemRender.MOD_ID, "material/absorbance.vert"),
            Ids.of(GemRender.MOD_ID, "material/absorbance_glow.frag"));

    static {
        Absorbance.getInstance()
                .register(ABSORBANCE_SHADERS);
        Absorbance.getInstance()
                .register(GLOW_SHADERS, true);
    }

    private ParticleModels() {
    }

    /**
     * A glow: the fragment is added to what is already there, so it can only ever brighten.
     *
     * <p>Which is why it writes no depth; see {@link #writeMaskFor}.
     */
    public static Model additive(ResourceLocation texture) {
        return billboard(texture, Transparency.ADDITIVE);
    }

    /**
     * Smoke, steam, dust: alpha-composited, and order-independent so the backend resolves the stack per
     * pixel rather than in submission order.
     *
     * <p>The water split orders this blend around translucent terrain. {@code ADDITIVE} particles are drawn before water.
     */
    public static Model translucent(ResourceLocation texture) {
        return billboard(texture, Transparency.ORDER_INDEPENDENT);
    }

    /**
     * Smoke, steam, dust, foam. Sums
     * {@code tau = -ln(1 - a)} per pixel: colour is the tau-weighted mean rather than layer-ordered.
     */
    public static Model absorbance(ResourceLocation texture) {
        return absorbance(texture, ABSORBANCE_SHADERS);
    }

    /**
     * {@link #absorbance} plus emission (fire, flash, sparks, exhaust): {@code tau = -ln(1 - a (1 - heat))},
     * emission {@code rgb a heat}, heat from {@link ParticleStyle.Builder#glow}; not lightmapped.
     * Uses a separate RGBA16F emission target.
     */
    public static Model glowing(ResourceLocation texture) {
        return absorbance(texture, GLOW_SHADERS);
    }

    /** A cooling glow on an arbitrary mesh, with the supplied material's texture and raster settings. */

    private static Model absorbance(ResourceLocation texture, MaterialShaders shaders) {
        return BILLBOARDS.computeIfAbsent(
                new Key(ParticleQuad.INSTANCE, texture, Transparency.ORDER_INDEPENDENT, CutoutShaders.OFF,
                        shaders),
                key -> {
                    var material = SimpleMaterial.builder()
                        .texture(key.texture())
                        .transparency(key.transparency())
                        .cutout(key.cutout())
                        .shaders(key.shaders())
                        .writeMask(WriteMask.COLOR)
                        .useOverlay(false)
                        .fog(FogShaders.NONE)
                        .blur(true)
                        .backfaceCulling(false)
                        .cardinalLightingMode(CardinalLightingMode.OFF)
                        .mipmap(false)
                        .build();
                    return new SingleMeshModel(key.mesh(), material);
                });
    }

    public static Model cutout(ResourceLocation texture) {
        return billboard(texture, Transparency.OPAQUE, CutoutShaders.ONE_TENTH);
    }

    /**
     * A cutout billboard drawn from a window of an atlas rather than from a texture of its own; see
     * {@link ParticleQuad#ofUv}. Opaque with a cutout, because that is what a chip of a solid block is.
     */
    public static Model sprite(QuadMesh mesh, ResourceLocation atlas) {
        return billboard(mesh, atlas, Transparency.OPAQUE, CutoutShaders.ONE_TENTH);
    }

    /**
     * For {@link GemRenderParticleTypes#DECAL}: blended, no depth write, polygon offset against the surface it
     * lies on, back faces culled so it does not show through thin blocks.
     */
    public static Model decal(ResourceLocation texture) {
        return DECALS.computeIfAbsent(texture, key -> new SingleMeshModel(ParticleQuad.INSTANCE,
                SimpleMaterial.builder()
                        .texture(key)
                        .transparency(Transparency.TRANSLUCENT)
                        .cutout(CutoutShaders.EPSILON)
                        .writeMask(WriteMask.COLOR)
                        .polygonOffset(true)
                        .backfaceCulling(true)
                        .cardinalLightingMode(CardinalLightingMode.OFF)
                        .mipmap(false)
                        .build()));
    }

    /** {@link #rigid(GemRenderGltfModel, Matrix4fc)} with no extra transform. */
    public static Model rigid(GemRenderGltfModel model) {
        return rigid(model, IDENTITY);
    }

    /**
     * A glTF model frozen at its rest pose, with {@code bake} applied after, as one particle mesh keeping the
     * model's materials. For {@link GemRenderParticleTypes#BODY} and {@link GemRenderParticleTypes#MESH}; the
     * origin is what the particle position tracks, +Y is the axis those types orient. Cached per model and
     * bake; a reloaded model is a new key.
     */
    public static Model rigid(GemRenderGltfModel model, Matrix4fc bake) {
        Map<Matrix4fc, Model> byBake;
        synchronized (RIGID) {
            byBake = RIGID.computeIfAbsent(model, key -> new ConcurrentHashMap<>());
        }
        return byBake.computeIfAbsent(new Matrix4f(bake), key -> bakeRigid(model, key));
    }

    private static Model bakeRigid(GemRenderGltfModel model, Matrix4fc bake) {
        Matrix4f[] palette = model.restPalette();
        Map<Mesh, Mesh> baked = new IdentityHashMap<>();
        List<Model.ConfiguredMesh> meshes = new ArrayList<>();
        for (Model.ConfiguredMesh configured : model.model()
                .meshes()) {
            if (!(configured.mesh() instanceof GltfMesh source)) {
                throw new IllegalArgumentException("rigid() bakes glTF meshes; got " + configured.mesh()
                        .getClass()
                        .getName());
            }
            Mesh mesh = baked.computeIfAbsent(source, key -> ParticleMesh.bake(source.geometry(), palette, bake));
            meshes.add(new Model.ConfiguredMesh(configured.material(), mesh));
        }
        return new SimpleModel(meshes);
    }

    private static final Matrix4fc IDENTITY = new Matrix4f();

    public static Model blended(ResourceLocation texture) {
        return BILLBOARDS.computeIfAbsent(new Key(ParticleQuad.INSTANCE, texture, Transparency.TRANSLUCENT,
                        CutoutShaders.EPSILON, StandardMaterialShaders.DEFAULT),
                key -> new SingleMeshModel(key.mesh(),
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
        return billboard(ParticleQuad.INSTANCE, texture, transparency, cutout);
    }

    public static Model billboard(QuadMesh mesh, ResourceLocation texture, Transparency transparency,
                                  CutoutShader cutout) {
        return BILLBOARDS.computeIfAbsent(new Key(mesh, texture, transparency, cutout,
                        StandardMaterialShaders.DEFAULT),
                key -> new SingleMeshModel(key.mesh(),
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
                .writeMask(writeMaskFor(transparency))
                .useOverlay(false)
                .backfaceCulling(false)
                .cardinalLightingMode(CardinalLightingMode.OFF)
                .mipmap(false)
                .build();
    }

    /**
     * Depth for geometry that hides what is behind it, colour only for anything blended.
     *
     * <p>Flywheel draws its instances after entities and <em>before</em> translucent terrain, so a blended
     * particle that writes depth deletes the water behind it: the water pass is rejected against a depth the
     * particle had no business writing, and a puff of smoke over a lake is a puff-shaped hole in the lake
     * with the bed showing through. Additive is the clearest case (a fragment that can only add light must
     * never occlude anything), but it is equally true of translucent, whose depth is written by the OIT
     * composite instead, where it can be split around the water.
     *
     * <p>The cost of writing none is that a blended particle in front of translucent terrain is painted over
     * by it rather than drawn on top of it. That is a misordering; the depth write was an erasure.
     */
    private static WriteMask writeMaskFor(Transparency transparency) {
        return transparency == Transparency.OPAQUE ? WriteMask.COLOR_DEPTH : WriteMask.COLOR;
    }

    private record Key(QuadMesh mesh, ResourceLocation texture, Transparency transparency, CutoutShader cutout,
                       MaterialShaders shaders) {
    }
}
