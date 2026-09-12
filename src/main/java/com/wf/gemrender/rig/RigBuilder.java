package com.wf.gemrender.rig;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.*;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.*;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a rigged asset out of loose meshes and a skeleton declared in code.
 *
 * <p>The third way into GemRender, beside glTF and Bedrock geometry, and the one for a model that
 * already exists without a rig. A mod with a folder of {@code .obj} parts and a hand-written animation
 * has the geometry and the motion but nothing joining them; this is that join. Declare the bones, hang
 * the meshes on them, and what comes out is an ordinary {@link GemRenderGltfModel}: one Flywheel model,
 * one instance a copy, posed through the shared bone palette like any other.
 *
 * <p>Every mesh is bound <b>rigidly</b> to exactly one bone -- there is no vertex weighting here, which
 * is what makes it usable with formats that carry none. A part deforms by being split into more parts,
 * the way a hard-surface model is built anyway.
 *
 * <h2>The order to call things in</h2>
 *
 * <ol>
 * <li>{@link #bone} for every bone, parents before children. Each call returns the bone's palette slot.
 * <li>{@link #table()}, which freezes the skeleton and hands back the {@link NodeTable} the driver
 * factories need. Clips are built against it.
 * <li>{@link #attach} for every mesh, and {@link #build} at the end.
 * </ol>
 *
 * <pre>{@code
 * Map<String, RigGeometry> groups = WavefrontObj.load(MESH);
 *
 * RigBuilder rig = new RigBuilder("crab");
 * int body = rig.bone("body", RigBuilder.ROOT, 0, 0, 0);
 * int claw = rig.bone("claw", body, 0.25f, 0.625f, 0.0625f);
 *
 * NodeTable table = rig.table();
 * GltfAnimation walk = GltfAnimation.procedural("walk",
 *         NodeOscillate.about(table, claw, 1, 0, 0, 0, 0.35f, 1.0f, 0.0f));
 *
 * rig.attach(body, groups, "Body")
 *    .attach(claw, groups, "Claw");
 * GemRenderGltfModel model = rig.build(material, Map.of("walk", walk));
 * }</pre>
 *
 * <h2>What a bone's rest transform means</h2>
 *
 * <p>A bone's translation is its pivot <em>relative to its parent's pivot</em>, in the frame the meshes
 * were authored in; the same convention a Bedrock model uses. Attached geometry stays where the artist
 * put it: {@link #attach} moves each mesh into its bone's frame for you, by the inverse of the bone's
 * rest transform, so a rig with no clip running draws exactly the model you started with.
 *
 * <p>Give a bone a rest rotation for a part that is mounted at an angle. Do <em>not</em> use it for the
 * constant term of an angle a driver also moves, when that bone turns about more than one axis -- see
 * {@link com.wf.gemrender.gltf.NodeOscillate} for why that composes in the wrong order.
 *
 * <h2>Reloads</h2>
 *
 * <p>A built model owns textures and has to be rebuilt when resources reload, exactly like an imported
 * one. Register the builder with {@link com.wf.gemrender.asset.GemRenderModels#built} and hold the
 * handle rather than the model.
 */
public final class RigBuilder {
    /**
     * The parent of a bone that has none.
     */
    public static final int ROOT = -1;
    private final String name;
    private final List<String> boneNames = new ArrayList<>();
    private final List<Integer> parents = new ArrayList<>();
    private final List<float[]> transforms = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    @Nullable
    private NodeTable table;
    private List<ResourceLocation> skins = List.of();
    @Nullable
    private ResourceLocation atlasId;

    /**
     * @param name what this rig is, for the log line and for error messages
     */
    public RigBuilder(String name) {
        this.name = name;
    }

    /**
     * Moves a mesh out of the frame it was authored in and into its bone's, by the inverse of the bone's
     * rest transform. This is the inverse bind matrix a glTF skin would have carried; here the rig knows
     * where each bone rests, so it can be worked out rather than shipped.
     */
    private static MeshGeometry bake(RigGeometry geometry, Matrix4f restWorld, VertexSkinning skinning,
                                     SpriteUv uv) {
        int vertices = geometry.vertexCount();

        Matrix4f inverse = new Matrix4f(restWorld).invert();
        Matrix3f normalMatrix = inverse.normal(new Matrix3f());

        float[] positions = new float[vertices * 3];
        float[] normals = geometry.normals() == null ? null : new float[vertices * 3];
        Vector3f scratch = new Vector3f();

        for (int v = 0; v < vertices; v++) {
            inverse.transformPosition(scratch.set(geometry.positions()[v * 3],
                    geometry.positions()[v * 3 + 1], geometry.positions()[v * 3 + 2]));
            positions[v * 3] = scratch.x;
            positions[v * 3 + 1] = scratch.y;
            positions[v * 3 + 2] = scratch.z;

            if (normals != null) {
                normalMatrix.transform(scratch.set(geometry.normals()[v * 3],
                        geometry.normals()[v * 3 + 1], geometry.normals()[v * 3 + 2]));
                if (scratch.lengthSquared() > 1.0e-12f) {
                    scratch.normalize();
                }
                normals[v * 3] = scratch.x;
                normals[v * 3 + 1] = scratch.y;
                normals[v * 3 + 2] = scratch.z;
            }
        }

        return MeshGeometry.of(positions, normals, geometry.texCoords(), geometry.indices(), skinning,
                uv, 0);
    }

    /**
     * A bone at rest with no rotation, which is nearly all of them.
     */
    public int bone(String boneName, int parent, float x, float y, float z) {
        return bone(boneName, parent, x, y, z, null);
    }

    /**
     * @param parent the slot {@link #bone} returned for this bone's parent, or {@link #ROOT}
     * @param rest   the bone's rest rotation, or null for none
     * @return this bone's palette slot, which is what the driver factories and {@link #attach} take
     */
    public int bone(String boneName, int parent, float x, float y, float z, @Nullable Quaternionfc rest) {
        if (table != null) {
            throw new IllegalStateException(name + ": the skeleton was frozen by table(); bone '" + boneName
                    + "' is too late. Declare every bone before building clips against it.");
        }
        if (parent >= boneNames.size()) {
            throw new IllegalArgumentException(name + ": bone '" + boneName + "' names parent " + parent
                    + ", which has not been declared yet. Parents come before their children.");
        }

        float[] trs = new float[NodeTable.TRS_STRIDE];
        trs[NodeTable.TRANSLATION] = x;
        trs[NodeTable.TRANSLATION + 1] = y;
        trs[NodeTable.TRANSLATION + 2] = z;
        trs[NodeTable.ROTATION] = rest == null ? 0.0f : rest.x();
        trs[NodeTable.ROTATION + 1] = rest == null ? 0.0f : rest.y();
        trs[NodeTable.ROTATION + 2] = rest == null ? 0.0f : rest.z();
        trs[NodeTable.ROTATION + 3] = rest == null ? 1.0f : rest.w();
        trs[NodeTable.SCALE] = 1.0f;
        trs[NodeTable.SCALE + 1] = 1.0f;
        trs[NodeTable.SCALE + 2] = 1.0f;

        boneNames.add(boneName);
        parents.add(parent);
        transforms.add(trs);
        return boneNames.size() - 1;
    }

    /**
     * Freezes the skeleton and returns it. Idempotent; every call after the first hands back the same
     * table, so a clip built early and a clip built late address the same slots.
     */
    public NodeTable table() {
        if (table == null) {
            if (boneNames.isEmpty()) {
                throw new IllegalStateException(name + " has no bones");
            }

            String[] names = boneNames.toArray(new String[0]);
            int[] parentSlots = new int[parents.size()];
            float[] trs = new float[transforms.size() * NodeTable.TRS_STRIDE];
            for (int slot = 0; slot < parentSlots.length; slot++) {
                parentSlots[slot] = parents.get(slot);
                System.arraycopy(transforms.get(slot), 0, trs, slot * NodeTable.TRS_STRIDE,
                        NodeTable.TRS_STRIDE);
            }

            table = NodeTable.ofNodes(names, parentSlots, trs);
        }
        return table;
    }

    public int boneCount() {
        return boneNames.size();
    }

    /**
     * Hangs a mesh on a bone. The same geometry may be attached to several bones; six legs usually are.
     */
    public RigBuilder attach(int slot, RigGeometry geometry) {
        return attach(slot, geometry, null);
    }

    /**
     * As above, with a material of its own rather than the one {@link #build} is given.
     *
     * <p>Meshes are grouped by material and each group becomes one draw, so a rig with two textures on it
     * costs two draws however many parts wear each -- a propeller skinned separately from its airframe,
     * a glass canopy over an opaque hull. Materials are grouped by equality, so reuse the object.
     */
    public RigBuilder attach(int slot, RigGeometry geometry, @Nullable Material material) {
        if (slot < 0 || slot >= boneNames.size()) {
            throw new IllegalArgumentException(name + ": no bone in slot " + slot);
        }
        attachments.add(new Attachment(slot, geometry, material));
        return this;
    }

    /**
     * As above, naming a group of a file read by {@link WavefrontObj}.
     *
     * @throws IllegalArgumentException if the file has no such group, naming the ones it does have --
     *                                  a mistyped group is otherwise a part that silently never draws
     */
    public RigBuilder attach(int slot, Map<String, RigGeometry> groups, String group) {
        return attach(slot, groups, group, null);
    }

    public RigBuilder attach(int slot, Map<String, RigGeometry> groups, String group,
                             @Nullable Material material) {
        RigGeometry geometry = groups.get(group);
        if (geometry == null) {
            throw new IllegalArgumentException(name + ": no mesh named '" + group + "'; the file has "
                    + groups.keySet());
        }
        return attach(slot, geometry, material);
    }

    /**
     * Every group of a file, all on one bone: the shape a part exported on its own comes in.
     */
    public RigBuilder attachAll(int slot, Map<String, RigGeometry> groups, @Nullable Material material) {
        for (RigGeometry geometry : groups.values()) {
            attach(slot, geometry, material);
        }
        return this;
    }

    /**
     * Declares the skins this rig can be worn in: one mesh, one draw, a texture each.
     *
     * <p>{@link com.wf.gemrender.asset.GemRenderModels#variants} for a rig assembled in code. The
     * skins are stitched into one sheet at build time and the mesh is given the coordinates of the
     * first one's tile, so what picks a skin at draw time is {@code instance.variant(model.variant(i))}
     * and nothing else -- no second model, no second instancer, no second draw, and every copy in one
     * pose-cache bucket whatever it is wearing.
     *
     * <p>The material {@link #build} is given keeps everything about it except its texture, which
     * becomes the sheet. Skin 0 is the base; all of them have to be the same size, and the sheet has
     * the 4096-pixel ceiling any other has. Fewer than two skins is not an error and not a sheet --
     * the rig is built on the one texture, wearing the single variant every model has.
     *
     * <p>{@code atlasId} names the sheet, so two skin sets of one rig need two ids.
     *
     * @throws IllegalStateException if any mesh was attached with a material of its own, whose texture
     *                               is not in the sheet and whose coordinates therefore cannot be
     *                               remapped onto it
     */
    public RigBuilder skins(ResourceLocation atlasId, List<ResourceLocation> skins) {
        this.atlasId = atlasId;
        this.skins = List.copyOf(skins);
        return this;
    }

    /**
     * Builds with a {@link GltfMaterial}, which is the importers' way of saying it: alpha mode and
     * two-sidedness rather than Flywheel's shaders, and a texture GemRender resolves and owns (a
     * {@code .ktx2} is decoded here and released on reload).
     *
     * @param clips the asset's animations by name, which may be empty for a rig the game poses through
     *              layers it builds itself
     */
    public GemRenderGltfModel build(GltfMaterial material, Map<String, GltfAnimation> clips) {
        List<ResourceLocation> ownedTextures = new ArrayList<>();
        GltfMaterial resolved = material.texture() == null ? material
                : material.onTexture(ModelTextures.materialTexture(material.texture(), ownedTextures), true);

        return build(resolved.toFlywheel(), clips, ownedTextures, resolved.describe());
    }

    /**
     * Builds with a Flywheel {@link Material} the caller made itself.
     *
     * <p>The overload for a model whose material is already decided -- a decal at depth-equal, an
     * emissive overlay, anything {@link GltfMaterial} has no vocabulary for. The texture is the caller's
     * to keep alive; nothing here is released on reload.
     */
    public GemRenderGltfModel build(Material material, Map<String, GltfAnimation> clips) {
        // Mutable, and not List.of(): this list is where a stitched sheet records itself so the reload
        // can free it, and a rig declaring skins reaches that line through here.
        return build(material, clips, new ArrayList<>(), String.valueOf(material.texture()));
    }

    private GemRenderGltfModel build(Material colour, Map<String, GltfAnimation> clips,
                                     List<ResourceLocation> ownedTextures, String describe) {
        NodeTable skeleton = table();
        GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(skeleton);

        ModelAtlas sheet = stitchSkins();
        SpriteUv uv = SpriteUv.IDENTITY;
        if (sheet != null) {
            ownedTextures.add(sheet.texture());
            uv = sheet.uv(MaterialMaps.plain(skins.get(0)));
            colour = SimpleMaterial.builderOf(colour)
                    .texture(sheet.texture())
                    .build();
            describe = sheet.variants()
                    .size() + " skins on " + sheet.texture();
        }

        Matrix4f[] rest = restPose(skeleton);

        SkinnedBounds.Builder bounds = new SkinnedBounds.Builder();
        Map<Material, List<MeshGeometry>> byMaterial = new LinkedHashMap<>();
        int vertices = 0;
        for (Attachment attachment : attachments) {
            VertexSkinning skinning =
                    VertexSkinning.rigid(attachment.geometry()
                            .vertexCount(), attachment.slot());
            MeshGeometry piece = bake(attachment.geometry(), rest[attachment.slot()], skinning, uv);
            bounds.add(piece.positions(), piece.vertexCount(), skinning);
            byMaterial.computeIfAbsent(attachment.material() == null ? colour : attachment.material(),
                            unused -> new ArrayList<>())
                    .add(piece);
            vertices += piece.vertexCount();
        }

        if (byMaterial.isEmpty()) {
            throw new IllegalStateException(name + " has bones but no geometry, so nothing would ever draw");
        }

        List<Model.ConfiguredMesh> configured = new ArrayList<>();
        for (Map.Entry<Material, List<MeshGeometry>> group : byMaterial.entrySet()) {
            GltfMesh mesh = new GltfMesh(MeshGeometry.concat(group.getValue()));
            configured.add(new Model.ConfiguredMesh(group.getKey(), mesh));
            Material depthOnly = GltfMaterial.depthPassFor(group.getKey());
            if (depthOnly != null) {
                configured.add(new Model.ConfiguredMesh(depthOnly, mesh));
            }
        }

        GemRender.LOGGER.info("Built rig {}: {} bones, {} mesh(es) -> {} vertices, {} material(s) "
                        + "(default {}), {} draw{} per batch, {} animations {}", name, skeleton.nodeCount(),
                attachments.size(), vertices, byMaterial.size(), describe, configured.size(),
                configured.size() == 1 ? "" : "s", clips.size(), clips.keySet());

        return new GemRenderGltfModel(new SimpleModel(configured), layout, bounds.build(),
                GltfMorphLayout.NONE, Map.copyOf(clips), sheet == null ? null : sheet.texture(),
                ownedTextures, sheet == null ? List.of(VariantUv.NONE) : sheet.variants());
    }

    /**
     * Stitches the declared skins into one sheet, or {@code null} for a rig that wears one texture.
     *
     * <p>A sheet that will not pack -- too many skins, or skins too large -- is a {@code null} from
     * {@link ModelAtlas}, and the rig falls back to skin 0 on its own texture. That is a model wearing
     * the wrong colour rather than a crash, and the atlas has already said why in the log.
     */
    @Nullable
    private ModelAtlas stitchSkins() {
        if (skins.size() < 2 || atlasId == null) {
            return null;
        }

        for (Attachment attachment : attachments) {
            if (attachment.material() != null) {
                throw new IllegalStateException(name + " declares " + skins.size() + " skins and also "
                        + "attaches a mesh with a material of its own. That mesh's texture is not in the "
                        + "sheet, so its coordinates cannot be remapped onto it; build the two as "
                        + "separate models.");
            }
        }

        ResourceLocation base = skins.get(0);
        List<Map<ResourceLocation, ResourceLocation>> swaps = new ArrayList<>(skins.size());
        for (ResourceLocation skin : skins) {
            swaps.add(skin.equals(base) ? Map.of() : Map.of(base, skin));
        }
        return ModelAtlas.stitch(atlasId, List.of(MaterialMaps.plain(base)), swaps);
    }

    private Matrix4f[] restPose(NodeTable skeleton) {
        float[] state = skeleton.newScratch();
        Matrix4f[] world = new Matrix4f[skeleton.nodeCount()];
        Matrix4f local = new Matrix4f();

        int[] parentSlots = skeleton.parentSlots();
        for (int slot : skeleton.evaluationOrder()) {
            skeleton.localTransform(state, slot, local);
            int parent = parentSlots[slot];
            world[slot] = parent < 0 ? new Matrix4f(local) : new Matrix4f(world[parent]).mul(local);
        }
        return world;
    }

    private record Attachment(int slot, RigGeometry geometry, @Nullable Material material) {
    }
}
