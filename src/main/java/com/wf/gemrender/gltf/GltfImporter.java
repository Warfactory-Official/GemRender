package com.wf.gemrender.gltf;

import com.wf.gemrender.Ids;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.morph.MorphTargets;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.texture.MaterialMaps;
import com.wf.gemrender.texture.ModelAtlas;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.texture.SpriteUv;
import com.wf.gemrender.vendor.jgltf.GltfResourceHook;
import com.wf.gemrender.vendor.jgltf.model.*;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import com.wf.gemrender.vendor.jgltf.model.v2.MaterialModelV2;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GltfImporter {
    private static final Gson GSON = new Gson();

    private static final boolean ATLAS = !"false".equalsIgnoreCase(System.getProperty("gemrender.atlas"));

    private static final boolean MERGE = !"false".equalsIgnoreCase(System.getProperty("gemrender.merge"));

    private static final ResourceLocation UNTEXTURED = GltfMaterial.UNTEXTURED;

    private GltfImporter() {
    }

    public static GemRenderGltfModel load(ResourceLocation location) throws IOException {
        return load(location, location, List.of(Map.of()));
    }

    public static GemRenderGltfModel load(ResourceLocation location, ResourceLocation id,
                                          List<Map<ResourceLocation, ResourceLocation>> variants) throws IOException {
        return load(location, id, variants, null);
    }

    public static GemRenderGltfModel load(ResourceLocation location, ResourceLocation id,
                                          List<Map<ResourceLocation, ResourceLocation>> variants,
                                          @org.jetbrains.annotations.Nullable RigPatch patch) throws IOException {
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .getResourceOrThrow(location)
                .open()) {
            GltfModel gltf = new GltfModelReader().readWithoutReferences(in);
            return convert(gltf, location, id, variants, patch == null ? Map.of() : patch(gltf, patch));
        }
    }

    private static Map<NodeModel, RigPatch.Weigher> patch(GltfModel gltf, RigPatch patch) {
        if (!(gltf instanceof com.wf.gemrender.vendor.jgltf.model.impl.DefaultGltfModel model)) {
            throw new IllegalArgumentException("rig patch needs a mutable glTF model, got " + gltf.getClass());
        }
        Map<NodeModel, RigPatch.Weigher> weighers = new java.util.IdentityHashMap<>();
        patch.patch(new RigPatch.Rig() {
            @Override
            public NodeModel node(String name) {
                for (NodeModel node : model.getNodeModels()) {
                    if (name.equals(node.getName())) {
                        return node;
                    }
                }
                return null;
            }

            @Override
            public NodeModel addJoint(NodeModel parent, String name) {
                var joint = new com.wf.gemrender.vendor.jgltf.model.impl.DefaultNodeModel();
                joint.setName(name);
                ((com.wf.gemrender.vendor.jgltf.model.impl.DefaultNodeModel) parent).addChild(joint);
                model.addNodeModel(joint);
                return joint;
            }

            @Override
            public void slice(NodeModel owner, RigPatch.Plane plane) {
                RigSlicer.slice(owner, plane);
            }

            @Override
            public void weigh(NodeModel owner, RigPatch.Weigher weigher) {
                weighers.put(owner, weigher);
            }
        });
        return weighers;
    }

    private static GemRenderGltfModel convert(GltfModel gltf, ResourceLocation source,
                                              ResourceLocation id, List<Map<ResourceLocation, ResourceLocation>> variants,
                                              Map<NodeModel, RigPatch.Weigher> weighers) {
        GltfPaletteLayout layout = GltfPaletteLayout.of(gltf);

        List<Primitive> primitives = new ArrayList<>();
        for (SceneModel scene : gltf.getSceneModels()) {
            for (NodeModel root : scene.getNodeModels()) {
                collectPrimitives(root, layout, weighers, primitives);
            }
        }

        if (primitives.isEmpty()) {
            throw new IllegalArgumentException("glTF " + source + " produced no meshes");
        }

        List<ResourceLocation> ownedTextures = new ArrayList<>();

        Map<ResourceLocation, ResourceLocation> resolved = new LinkedHashMap<>();

        ModelAtlas atlas = atlas(source, id, primitives, variants);
        // Off the sheet there is one variant at most: its swaps name the texture drawn directly.
        Map<ResourceLocation, ResourceLocation> swaps = variants.isEmpty() ? Map.of() : variants.get(0);
        if (atlas != null) {
            ownedTextures.add(atlas.texture());
        }

        SkinnedBounds.Builder bounds = new SkinnedBounds.Builder();
        GltfMorphLayout.Builder morphs = GltfMorphLayout.builder();

        Map<GltfMaterial, List<MeshGeometry>> byMaterial = new LinkedHashMap<>();
        Map<Map.Entry<String, GltfMaterial>, List<MeshGeometry>> bySlot = new LinkedHashMap<>();
        int maxInfluences = 0;
        java.util.Set<ResourceLocation> masks = new java.util.LinkedHashSet<>();
        boolean allPbr = true;

        for (Primitive primitive : primitives) {
            boolean atlased = primitive.slot() == null && atlas != null && atlas.contains(primitive.maps());

            int morphSet = primitive.targets() == null ? 0
                    : morphs.add(layout.nodeTable(), primitive.node(), primitive.mesh(), primitive.targets(),
                    MorphBuffer.getInstance()
                            .register(primitive.targets()
                                    .deltas()));

            MeshGeometry geometry = MeshGeometry.of(primitive.primitive(), primitive.skinning(),
                    atlased ? atlas.uv(primitive.maps()) : SpriteUv.IDENTITY, morphSet);
            maxInfluences = Math.max(maxInfluences, primitive.skinning()
                    .maxInfluences());

            bounds.add(geometry.positions(), geometry.vertexCount(), primitive.skinning(),
                    morphExtent(primitive.targets(), geometry.vertexCount()));

            if (primitive.slot() != null) {
                bySlot.computeIfAbsent(Map.entry(primitive.slot(), primitive.material()), key -> new ArrayList<>())
                        .add(geometry);
                continue;
            }

            ResourceLocation texture = atlased ? atlas.texture()
                    : primitive.texture() == null ? UNTEXTURED
                    : resolved.computeIfAbsent(swaps.getOrDefault(primitive.texture(), primitive.texture()),
                    key -> ModelTextures.materialTexture(key, ownedTextures));

            boolean banded = atlased && atlas.bands() > 1;
            if (atlased && primitive.maps()
                    .paint() != null) {
                masks.add(primitive.maps()
                        .paint());
            }
            if (primitive.material()
                    .pbr() && !banded) {
                GemRender.LOGGER.warn("{} wants PBR maps but its sheet has no bands; drawing base colour only",
                        primitive.texture());
            }
            GltfMaterial material = primitive.material()
                    .onTexture(texture, banded);
            allPbr &= material.pbr();
            byMaterial.computeIfAbsent(material, key -> new ArrayList<>())
                    .add(geometry);
        }

        List<Model.ConfiguredMesh> meshes = new ArrayList<>();

        List<Model.ConfiguredMesh> depthPass = new ArrayList<>();
        for (Map.Entry<GltfMaterial, List<MeshGeometry>> group : byMaterial.entrySet()) {
            Material material = group.getKey()
                    .toFlywheel();
            Material depthOnly = GltfMaterial.depthPassFor(material);
            if (MERGE) {

                GltfMesh mesh = new GltfMesh(MeshGeometry.concat(group.getValue(),
                        (part, vertexBase) -> {
                            if (part.vertexCount() > 0 && part.morphSet(0) != 0) {
                                morphs.rebase(part.morphSet(0), vertexBase);
                            }
                        }));
                meshes.add(new Model.ConfiguredMesh(material, mesh));
                if (depthOnly != null) {
                    depthPass.add(new Model.ConfiguredMesh(depthOnly, mesh));
                }
            } else {
                for (MeshGeometry geometry : group.getValue()) {
                    GltfMesh mesh = new GltfMesh(geometry);
                    meshes.add(new Model.ConfiguredMesh(material, mesh));
                    if (depthOnly != null) {
                        depthPass.add(new Model.ConfiguredMesh(depthOnly, mesh));
                    }
                }
            }
        }
        int colourMeshes = meshes.size();
        meshes.addAll(depthPass);

        List<GemRenderGltfModel.SlotMesh> slotMeshes = new ArrayList<>();
        for (Map.Entry<Map.Entry<String, GltfMaterial>, List<MeshGeometry>> group : bySlot.entrySet()) {
            GltfMesh mesh = new GltfMesh(MeshGeometry.concat(group.getValue(), (part, vertexBase) -> {
                if (part.vertexCount() > 0 && part.morphSet(0) != 0) {
                    morphs.rebase(part.morphSet(0), vertexBase);
                }
            }));
            slotMeshes.add(new GemRenderGltfModel.SlotMesh(group.getKey().getKey(), group.getKey().getValue(), mesh));
        }

        Map<String, GltfAnimation> animations = GltfSkeletonReader.animations(gltf, layout.nodeTable());

        GltfMorphLayout morphLayout = morphs.build();
        GemRenderGltfModel converted = new GemRenderGltfModel(new SimpleModel(meshes), layout, bounds.build(),
                morphLayout, animations, atlas == null ? null : atlas.texture(), ownedTextures,
                atlas == null ? java.util.List.of(com.wf.gemrender.texture.VariantUv.NONE)
                        : atlas.variants(), masks.isEmpty() || !allPbr ? -1 : paintReference(masks), slotMeshes);
        if (!masks.isEmpty() && !allPbr) {
            GemRender.LOGGER.warn("{} has a paint mask but a mesh outside the banded sheet; not paintable", source);
        }

        GemRender.LOGGER.info(
                "Loaded glTF {}: {} palette slots ({} nodes + {} skinned joints in {} skins), "
                        + "{} primitives, max {} influences/vertex, {} animations {}",
                source, layout.size(), layout.nodes().size(), layout.size() - layout.nodes().size(),
                layout.skins().size(), primitives.size(), maxInfluences, animations.size(),
                animations.keySet());

        GemRender.LOGGER.info("  draws: {} primitives -> {} materials -> {} meshes ({} draws per batch{}){}{}",
                primitives.size(), byMaterial.size(), colourMeshes, meshes.size(),
                depthPass.isEmpty() ? "" : ", " + depthPass.size() + " of them depth-only",
                atlas == null ? ", not atlased"
                        : ", atlas " + atlas.texture() + " " + atlas.width() + "x" + atlas.height(),
                slotMeshes.isEmpty() ? "" : ", texture slots " + bySlot.keySet().stream()
                        .map(Map.Entry::getKey).toList());

        for (Map.Entry<GltfMaterial, List<MeshGeometry>> group : byMaterial.entrySet()) {
            GltfMaterial key = group.getKey();
            GemRender.LOGGER.info("    material {}: {} ({} primitive{})", key.texture(),
                    key.describe(),
                    group.getValue()
                            .size(),
                    group.getValue()
                            .size() == 1 ? "" : "s");
        }

        if (!morphLayout.isEmpty()) {
            int targets = 0;
            for (GltfMorphLayout.MorphSet set : morphLayout.sets()) {
                targets += set.targets()
                        .targetCount();
            }
            GemRender.LOGGER.info("  morphs: {} sets, {} targets, {} floats of deltas, "
                            + "{} floats per instance per frame",
                    morphLayout.sets()
                            .size(),
                    targets, MorphBuffer.getInstance()
                            .floatCount(),
                    morphLayout.blockFloats());
        }
        logBounds(converted);

        return converted;
    }

    private static int paintReference(java.util.Set<ResourceLocation> masks) {
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        double weight = 0.0;
        for (ResourceLocation mask : masks) {
            com.mojang.blaze3d.platform.NativeImage image = ModelTextures.read(mask);
            if (image == null) {
                continue;
            }
            try {
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = com.wf.gemrender.texture.Pixels.get(image, x, y);
                        double coverage = (pixel >>> 24) / 255.0;
                        r += coverage * (pixel & 0xFF);
                        g += coverage * (pixel >> 8 & 0xFF);
                        b += coverage * (pixel >> 16 & 0xFF);
                        weight += coverage;
                    }
                }
            } finally {
                image.close();
            }
        }
        if (weight == 0.0) {
            return -1;
        }
        return (int) Math.round(r / weight) << 16 | (int) Math.round(g / weight) << 8 | (int) Math.round(b / weight);
    }

    private static float[] morphExtent(MorphTargets targets, int vertexCount) {
        if (targets == null) {
            return null;
        }

        float[] extent = new float[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            extent[v] = targets.maxDisplacement(v);
        }
        return extent;
    }

    private static void logBounds(GemRenderGltfModel model) {
        Matrix4f[] rest = model.newPalette();
        GltfPose.evaluate(model.layout(), null, 0.0f, rest);

        Vector4f posed = new Vector4f();
        model.bounds()
                .evaluate(rest, posed);
        Vector4fc flywheel = model.model()
                .boundingSphere();

        GemRender.LOGGER.info(String.format(java.util.Locale.ROOT,
                "  cull bounds: %d bone boxes, rest sphere r=%.2f about (%.2f, %.2f, %.2f); "
                        + "Flywheel's mesh-space sphere is r=%.2f about (%.2f, %.2f, %.2f) and is not used",
                model.bounds()
                        .size(),
                posed.w, posed.x, posed.y, posed.z,
                flywheel.w(), flywheel.x(), flywheel.y(), flywheel.z()));
    }

    /** Node-name prefixes of consumer-side data (hit volumes, attach markers): never drawn, subtree included. */
    public static final List<String> HIDDEN_PREFIXES = List.of("hit_", "socket_");
    /** Node {@code extras.gemrenderHidden: true}: same as a hidden prefix. */
    public static final String HIDDEN = "gemrenderHidden";

    private static void collectPrimitives(NodeModel node, GltfPaletteLayout layout,
                                          Map<NodeModel, RigPatch.Weigher> weighers, List<Primitive> out) {
        if (node.getName() != null) {
            String name = node.getName().toLowerCase(java.util.Locale.ROOT);
            if (HIDDEN_PREFIXES.stream().anyMatch(name::startsWith)) {
                return;
            }
        }
        if (node.getExtras() != null) {
            JsonElement hidden = GSON.toJsonTree(node.getExtras()).getAsJsonObject().get(HIDDEN);
            if (hidden != null && hidden.getAsBoolean()) {
                return;
            }
        }
        for (MeshModel mesh : node.getMeshModels()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                if (primitive.getMode() != 4) {
                    GemRender.LOGGER.warn("Skipping glTF primitive with unsupported mode {} (only TRIANGLES)",
                            primitive.getMode());
                    continue;
                }

                int vertexCount = primitive.getAttributes()
                        .get("POSITION")
                        .getCount();

                MaterialMaps maps = maps(primitive.getMaterialModel());
                out.add(new Primitive(node, mesh, primitive, skinning(node, primitive, layout, weighers.get(node)),
                        material(primitive.getMaterialModel(), maps), maps, uvInUnitSquare(primitive),
                        MorphTargets.of(primitive, vertexCount), slot(primitive.getMaterialModel())));
            }
        }

        for (NodeModel child : node.getChildren()) {
            collectPrimitives(child, layout, weighers, out);
        }
    }

    private static ModelAtlas atlas(ResourceLocation source, ResourceLocation id,
                                    List<Primitive> primitives, List<Map<ResourceLocation, ResourceLocation>> variants) {
        if (!ATLAS) {
            if (variants.size() > 1) {
                throw new IllegalStateException(source + " declares " + variants.size() + " variants, which "
                        + "are tiles of a stitched sheet; -Dgemrender.atlas=false turns the sheet off and "
                        + "there is nowhere for them to go");
            }
            return null;
        }

        Map<MaterialMaps, Boolean> eligible = new LinkedHashMap<>();
        for (Primitive primitive : primitives) {
            if (primitive.slot() == null && (primitive.texture() != null || primitive.maps()
                    .pbr() || !primitive.maps().baseColorUnmodified())) {
                eligible.merge(primitive.maps(), primitive.uvInUnitSquare(), Boolean::logicalAnd);
            }
        }

        List<MaterialMaps> stitch = new ArrayList<>();
        for (Map.Entry<MaterialMaps, Boolean> entry : eligible.entrySet()) {
            if (entry.getValue()) {
                stitch.add(entry.getKey());
            } else {
                GemRender.LOGGER.info("Not atlasing {}: geometry uses it outside [0,1], so it has to wrap",
                        entry.getKey()
                                .baseColor());
            }
        }

        for (MaterialMaps material : stitch) {
            if (material.pbr() || !material.baseColorUnmodified()) {
                GemRender.LOGGER.info("  surface {}:{}", material.baseColor(), material.describe());
            }
        }

        return ModelAtlas.stitch(atlasId(id), stitch, variants);
    }

    private static ResourceLocation atlasId(ResourceLocation source) {
        return Ids.of(GemRender.MOD_ID,
                "atlas/" + source.getNamespace() + "/" + source.getPath());
    }

    private static boolean uvInUnitSquare(MeshPrimitiveModel primitive) {
        AccessorModel accessor = primitive.getAttributes()
                .get("TEXCOORD_0");
        if (accessor == null) {
            return true;
        }

        AccessorFloatData data = AccessorDatas.createFloat(accessor);
        for (int v = 0; v < accessor.getCount(); v++) {
            for (int c = 0; c < 2; c++) {
                float value = data.get(v, c);

                if (value < -1e-4f || value > 1.0f + 1e-4f) {
                    return false;
                }
            }
        }
        return true;
    }

    private static VertexSkinning skinning(NodeModel node, MeshPrimitiveModel primitive,
                                           GltfPaletteLayout layout, RigPatch.Weigher weigher) {
        int vertexCount = primitive.getAttributes()
                .get("POSITION")
                .getCount();

        SkinModel skin = node.getSkinModel();
        if (skin != null) {
            VertexSkinning skinned = VertexSkinning.of(primitive, vertexCount, layout.jointSlots(skin));
            if (skinned != null) {
                return skinned;
            }
            GemRender.LOGGER.warn("glTF node '{}' has a skin but a primitive with no JOINTS_0; "
                    + "binding it rigidly to the node instead.", node.getName());
        }

        if (weigher != null) {
            return weighed(primitive, vertexCount, layout, weigher);
        }
        return VertexSkinning.rigid(vertexCount, layout.nodeSlot(node));
    }

    private static VertexSkinning weighed(MeshPrimitiveModel primitive, int vertexCount, GltfPaletteLayout layout,
                                          RigPatch.Weigher weigher) {
        AccessorFloatData pos = AccessorDatas.createFloat(primitive.getAttributes().get("POSITION"));
        int[] slots = new int[vertexCount * 4];
        float[] weights = new float[vertexCount * 4];
        NodeModel[] joints = new NodeModel[4];
        float[] w = new float[4];
        for (int v = 0; v < vertexCount; v++) {
            java.util.Arrays.fill(w, 0);
            int n = weigher.weigh(pos.get(v, 0), pos.get(v, 1), pos.get(v, 2), joints, w);
            for (int i = 0; i < 4; i++) {
                slots[v * 4 + i] = layout.nodeSlot(joints[i < n ? i : 0]);
                weights[v * 4 + i] = i < n ? w[i] : 0;
            }
        }
        return VertexSkinning.weighted(vertexCount, slots, weights);
    }

    private static GltfMaterial material(MaterialModel materialModel, MaterialMaps maps) {
        if (!(materialModel instanceof MaterialModelV2 v2)) {
            return new GltfMaterial(null, GltfMaterial.AlphaMode.OPAQUE, 0.5f, false);
        }

        GltfMaterial.AlphaMode mode = switch (v2.getAlphaMode()) {
            case MASK -> GltfMaterial.AlphaMode.MASK;
            case BLEND -> GltfMaterial.AlphaMode.BLEND;
            default -> GltfMaterial.AlphaMode.OPAQUE;
        };

        return new GltfMaterial(maps.baseColor(), mode, v2.getAlphaCutoff(), v2.isDoubleSided(),
                maps.pbr());
    }

    private static MaterialMaps maps(MaterialModel materialModel) {
        if (!(materialModel instanceof MaterialModelV2 v2)) {
            return MaterialMaps.plain(null);
        }

        float[] base = v2.getBaseColorFactor();
        float[] emissive = v2.getEmissiveFactor();
        ResourceLocation baseColor = texture(v2.getBaseColorTexture(), true);

        return new MaterialMaps(baseColor,
                secondaryUv(v2) ? null : texture(v2.getNormalTexture(), false),
                secondaryUv(v2) ? null : texture(v2.getMetallicRoughnessTexture(), false),
                secondaryUv(v2) ? null : texture(v2.getOcclusionTexture(), false),
                secondaryUv(v2) ? null : texture(v2.getEmissiveTexture(), false),
                factor(base, 0), factor(base, 1), factor(base, 2), factor(base, 3),
                v2.getMetallicFactor(), v2.getRoughnessFactor(), v2.getNormalScale(),
                v2.getOcclusionStrength(), factor(emissive, 0, 0.0f), factor(emissive, 1, 0.0f),
                factor(emissive, 2, 0.0f),
                ModelTextures.sibling(baseColor, MaterialMaps.PAINT_SUFFIX));
    }

    private static boolean secondaryUv(MaterialModelV2 v2) {
        boolean secondary = nonZero(v2.getNormalTexcoord()) || nonZero(v2.getMetallicRoughnessTexcoord())
                || nonZero(v2.getOcclusionTexcoord()) || nonZero(v2.getEmissiveTexcoord())
                || nonZero(v2.getBaseColorTexcoord());
        if (secondary) {
            GemRender.LOGGER.warn("glTF material '{}' addresses a map with TEXCOORD_1; GemRender carries one "
                    + "set of texture coordinates, so its PBR maps are dropped rather than baked against "
                    + "the wrong ones.", v2.getName());
        }
        return secondary;
    }

    private static boolean nonZero(Integer texcoord) {
        return texcoord != null && texcoord != 0;
    }

    private static float factor(float[] values, int index) {
        return factor(values, index, 1.0f);
    }

    private static float factor(float[] values, int index, float fallback) {
        return values == null || values.length <= index ? fallback : values[index];
    }

    private static ResourceLocation texture(TextureModel textureModel, boolean warn) {
        if (textureModel == null || textureModel.getImageModel() == null) {
            return null;
        }

        Object extras = textureModel.getImageModel()
                .getExtras();
        if (extras == null) {
            if (warn) {
                GemRender.LOGGER.warn("glTF image has no '{}' in extras; it will render untextured. "
                                + "Add it so the texture can come from a resource pack.",
                        GltfResourceHook.RESOURCE_LOCATION);
            }
            return null;
        }

        JsonElement element = GSON.toJsonTree(extras)
                .getAsJsonObject()
                .get(GltfResourceHook.RESOURCE_LOCATION);
        if (element == null) {
            return null;
        }
        return GltfResourceHook.resourceLocationFromString(element.getAsString());
    }

    /** Material {@code extras.textureSlot}: texture bound per submit, see {@link GemRenderGltfModel#slotMeshes}. */
    public static final String TEXTURE_SLOT = "textureSlot";

    @org.jetbrains.annotations.Nullable
    private static String slot(MaterialModel materialModel) {
        Object extras = materialModel == null ? null : materialModel.getExtras();
        if (extras == null) {
            return null;
        }
        JsonElement element = GSON.toJsonTree(extras)
                .getAsJsonObject()
                .get(TEXTURE_SLOT);
        return element == null ? null : element.getAsString();
    }

    private record Primitive(NodeModel node, MeshModel mesh, MeshPrimitiveModel primitive,
                             VertexSkinning skinning, GltfMaterial material, MaterialMaps maps, boolean uvInUnitSquare,
                             MorphTargets targets, @org.jetbrains.annotations.Nullable String slot) {
        @org.jetbrains.annotations.Nullable
        ResourceLocation texture() {
            return maps.baseColor();
        }
    }
}
