package com.wf.gemrender.gltf;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.texture.VariantUv;
import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4fc;

import java.util.List;
import java.util.Map;

/**
 * An imported asset: one Flywheel {@link Model}, plus its palette layout, bounds, morphs and clips.
 *
 * <p>{@code paintReference}: {@code 0xRRGGBB} the masked texels were painted in (coverage-weighted mean of
 * the masks' rgb), or -1 = not paintable: no mask, or a mesh outside the PBR material, the only one that
 * reads {@link com.wf.gemrender.render.GemRenderInstance#paint}. Painting a non-paintable model draws
 * garbage.
 *
 * <p>{@code slotMeshes}: primitives whose material names a texture slot ({@code extras.textureSlot}); outside
 * {@link #model} (world path draws none), direct path only, texture bound per submit
 * ({@link com.wf.gemrender.direct.TextureSlots}).
 */
public record GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
                                 GltfMorphLayout morphs, Map<String, GltfAnimation> animations,
                                 @Nullable ResourceLocation atlas,
                                 List<ResourceLocation> textures, List<VariantUv> variants, int paintReference,
                                 List<SlotMesh> slotMeshes) {
    public GemRenderGltfModel {
        textures = List.copyOf(textures);
        variants = variants.isEmpty() ? List.of(VariantUv.NONE) : List.copyOf(variants);
        slotMeshes = List.copyOf(slotMeshes);
    }

    public GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
                              GltfMorphLayout morphs, Map<String, GltfAnimation> animations,
                              @Nullable ResourceLocation atlas,
                              List<ResourceLocation> textures, List<VariantUv> variants) {
        this(model, layout, bounds, morphs, animations, atlas, textures, variants, -1, List.of());
    }

    /** One slot's primitives under one material, merged. */
    public record SlotMesh(String slot, GltfMaterial material, GltfMesh mesh) {
    }

    /**
     * Physical frame of a slot quad, from its authored UVs: {@code width} / {@code height} = model length of one
     * U / V unit (first triangle), {@code centre} = mesh bounds centre in the owner node's space, {@code node} =
     * that node's palette slot. Null: no such slot, or a degenerate first triangle.
     */
    @Nullable
    public SlotFrame slotFrame(String slot) {
        for (SlotMesh slotMesh : slotMeshes) {
            if (!slotMesh.slot().equals(slot)) {
                continue;
            }
            MeshGeometry g = slotMesh.mesh().geometry();
            if (g.indexCount() < 3) {
                return null;
            }
            int a = g.index(0);
            int b = g.index(1);
            int c = g.index(2);
            float[] ab = delta(g, a, b);
            float[] ac = delta(g, a, c);
            float du1 = g.texCoord(b, 0) - g.texCoord(a, 0);
            float dv1 = g.texCoord(b, 1) - g.texCoord(a, 1);
            float du2 = g.texCoord(c, 0) - g.texCoord(a, 0);
            float dv2 = g.texCoord(c, 1) - g.texCoord(a, 1);
            float det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1.0E-9f) {
                return null;
            }
            double du = 0;
            double dv = 0;
            for (int k = 0; k < 3; k++) {
                float pu = (ab[k] * dv2 - ac[k] * dv1) / det;
                float pv = (ac[k] * du1 - ab[k] * du2) / det;
                du += pu * pu;
                dv += pv * pv;
            }
           Vector4fc sphere = g.boundingSphere();
            return new SlotFrame((float) Math.sqrt(du), (float) Math.sqrt(dv),
                    new Vector3f(sphere.x(), sphere.y(), sphere.z()),
                   BoneAttributeCodec.unpackJoint(g.packedJoints(a), 0));
        }
        return null;
    }

    private static float[] delta(MeshGeometry g, int from, int to) {
        return new float[]{g.position(to, 0) - g.position(from, 0), g.position(to, 1) - g.position(from, 1),
                g.position(to, 2) - g.position(from, 2)};
    }

    /** See {@link #slotFrame}. */
    public record SlotFrame(float width, float height, org.joml.Vector3f centre, int node) {
    }

    /** Distinct slot names, in mesh order. */
    public List<String> slots() {
        return slotMeshes.stream().map(SlotMesh::slot).distinct().toList();
    }

    public boolean paintable() {
        return paintReference >= 0;
    }

    /**
     * A model with the one variant its own textures give it.
     */
    public GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
                              GltfMorphLayout morphs, Map<String, GltfAnimation> animations,
                              @Nullable ResourceLocation atlas, List<ResourceLocation> textures) {
        this(model, layout, bounds, morphs, animations, atlas, textures, List.of(VariantUv.NONE));
    }

    /**
     * The texture-coordinate offset for one of this model's variants, to put on an instance.
     *
     * <p>Index 0 is the model as its own file describes it and is what an asset with no declared
     * variants has. An index past the end is clamped rather than thrown: a variant is content, the
     * count can change when a pack is swapped, and a mob briefly wearing skin 0 is a better failure
     * than a crash in a visual.
     */
    public VariantUv variant(int index) {
        return variants.get(Mth.clamp(index, 0, variants.size() - 1));
    }

    public int variantCount() {
        return variants.size();
    }

    public int jointCount() {
        return layout.size();
    }

    /** Pose data alone: what a server evaluating the same file sees. */
    public GltfSkeleton skeleton() {
        return new GltfSkeleton(layout, animations);
    }

    /**
     * Palette at rest: pins paint to each part. New array per call; cache it.
     */
    public Matrix4f[] restPalette() {
        Matrix4f[] rest = newPalette();
        GltfPose.evaluate(layout, null, 0.0f, rest);
        return rest;
    }

    public Matrix4f[] newPalette() {
        Matrix4f[] palette = new Matrix4f[jointCount()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Matrix4f();
        }
        return palette;
    }

    public GltfAnimation animation(String name) {
        return animations.get(name);
    }

    public GltfAnimation animationOrAny(String name) {
        GltfAnimation named = animations.get(name);
        if (named != null) {
            return named;
        }
        return animations.isEmpty() ? null : animations.values()
                .iterator()
                .next();
    }
}
