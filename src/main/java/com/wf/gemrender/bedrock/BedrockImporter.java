package com.wf.gemrender.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.bedrock.BedrockSkeleton.Part;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfMaterial;
import com.wf.gemrender.gltf.GltfMesh;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.RigidMesh;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.texture.SpriteUv;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class BedrockImporter {
	private static final String GEOMETRY_SUFFIX = ".geo.json";

	private static final Set<String> GAMEPLAY_BONES_OVERRIDE =
			names(System.getProperty("gemrender.gameplaybones", ""));

	private BedrockImporter() {
	}

	private static Set<String> names(String commaSeparated) {
		Set<String> out = new LinkedHashSet<>();
		for (String name : commaSeparated.split(",")) {
			if (!name.isBlank()) {
				out.add(name.trim());
			}
		}
		return out;
	}

	public static GemRenderGltfModel load(ResourceLocation location) throws IOException {
		JsonObject root = read(location);
		BedrockGeometry geometry = BedrockGeometry.parse(root, location.toString());
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);

		if (skeleton.slotCount() == 0) {
			throw new IllegalArgumentException("Bedrock geometry " + location + " has no bones");
		}

		GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(skeleton.table());
		List<ResourceLocation> ownedTextures = new ArrayList<>();

		GltfMaterial material = material(geometry, location, ownedTextures);

		SkinnedBounds.Builder bounds = new SkinnedBounds.Builder();
		List<MeshGeometry> pieces = new ArrayList<>();
		int cubes = 0;
		int dropped = 0;

		for (Map.Entry<Integer, List<Part>> group : bySlot(skeleton).entrySet()) {
			int slot = group.getKey();
			BedrockCubes builder = new BedrockCubes();

			for (Part part : group.getValue()) {
				builder.add(part.cube(), part.pivot(), part.mirror(), part.inflate(),
						geometry.textureWidth(), geometry.textureHeight(), part.placement());
				cubes++;
			}
			dropped += builder.droppedFaces();

			if (builder.vertexCount() == 0) {
				continue;
			}

			VertexSkinning skinning = VertexSkinning.rigid(builder.vertexCount(), slot);
			MeshGeometry piece = MeshGeometry.of(builder.positions(), builder.normals(),
					builder.texCoords(), builder.indices(), skinning, SpriteUv.IDENTITY, 0);
			bounds.add(piece.positions(), piece.vertexCount(), skinning);
			pieces.add(piece);
		}

		if (pieces.isEmpty()) {
			throw new IllegalArgumentException("Bedrock geometry " + location + " produced no faces: "
					+ (cubes == 0 ? "it has no cubes at all, so its geometry is in a poly_mesh or a "
							+ "texture_mesh, neither of which GemRender reads"
							: "its " + cubes + " cube(s) are all degenerate or name no texture rectangle"));
		}

		GltfMesh mesh = new GltfMesh(MeshGeometry.concat(pieces));
		Material colour = material.toFlywheel();
		List<Model.ConfiguredMesh> configured = new ArrayList<>();
		configured.add(new Model.ConfiguredMesh(colour, mesh));
		Material depthOnly = GltfMaterial.depthPassFor(colour);
		if (depthOnly != null) {
			configured.add(new Model.ConfiguredMesh(depthOnly, mesh));
		}

		Model model = new SimpleModel(configured);
		Map<String, GltfAnimation> animations = animations(location, skeleton);

		long rotated = skeleton.parts()
				.stream()
				.filter(part -> part.placement() != null)
				.count();
		GemRender.LOGGER.info("Loaded Bedrock {}: {} palette slots, {} cubes ({} with a baked rotation), "
				+ "{} vertices, 1 material, 1 mesh ({} draw{} per batch), {} animations {}",
				location, skeleton.slotCount(), cubes, rotated, pieces.stream()
						.mapToInt(MeshGeometry::vertexCount)
						.sum(),
				configured.size(), configured.size() == 1 ? "" : "s",
				animations.size(), animations.keySet());
		GemRender.LOGGER.info("  material {}: {}, sheet {}x{}, {} face(s) dropped for having no area or no "
				+ "texture rectangle",
				material.texture(), material.describe(), geometry.textureWidth(), geometry.textureHeight(),
				dropped);

		return new GemRenderGltfModel(model, layout, bounds.build(), GltfMorphLayout.NONE, animations, null,
				ownedTextures);
	}

	public static GemRenderPartsModel loadParts(ResourceLocation location) throws IOException {
		BedrockGeometry geometry = BedrockGeometry.parse(read(location), location.toString());
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);

		if (skeleton.slotCount() == 0) {
			throw new IllegalArgumentException("Bedrock geometry " + location + " has no bones");
		}

		JsonObject clipJson = animationJson(location);
		Map<String, GltfAnimation> animations = clipJson == null ? Map.of()
				: BedrockAnimations.parse(clipJson, skeleton, sibling(location, ".animation.json").toString());

		Set<String> moving = new LinkedHashSet<>();
		if (clipJson != null) {
			moving.addAll(BedrockAnimations.drivenBones(clipJson));
		}
		int clipDriven = moving.size();
		moving.addAll(geometry.gameplayBones());
		moving.addAll(GAMEPLAY_BONES_OVERRIDE);

		List<BedrockParts.RigidPart> rigid = BedrockParts.partition(geometry, skeleton, moving);

		List<ResourceLocation> ownedTextures = new ArrayList<>();
		GltfMaterial material = material(geometry, location, ownedTextures);
		Material colour = material.toFlywheel();
		Material depthOnly = GltfMaterial.depthPassFor(colour);

		List<MeshGeometry> geometries = rigid.stream()
				.map(BedrockParts.RigidPart::geometry)
				.toList();

		PartMerge.Pixels sheet = sheet(geometry, location);
		int[] canonical = PartMerge.canonical(geometries, sheet);
		int shared = 0;

		List<Model> models = new ArrayList<>(rigid.size());
		for (int i = 0; i < rigid.size(); i++) {
			MeshGeometry mesh = geometries.get(i);
			if (mesh == null) {
				models.add(null);
			} else if (canonical[i] != i) {
				models.add(models.get(canonical[i]));
				shared++;
			} else {
				models.add(partModel(mesh, colour, depthOnly));
			}
		}

		List<GemRenderPartsModel.Part> parts = new ArrayList<>(rigid.size());
		int[] slotToPart = new int[skeleton.slotCount()];
		for (int i = 0; i < rigid.size(); i++) {
			BedrockParts.RigidPart part = rigid.get(i);
			parts.add(new GemRenderPartsModel.Part(part.name(), part.rootSlot(), part.parent(),
					part.toParent(), models.get(i)));
			for (int slot : part.slots()) {
				slotToPart[slot] = i;
			}
		}

		int distinct = (int) models.stream()
				.filter(java.util.Objects::nonNull)
				.distinct()
				.count();

		GemRenderPartsModel out = new GemRenderPartsModel(parts,
				GltfPaletteLayout.ofNodes(skeleton.table()), slotToPart, animations,
				restBoundingSphere(rigid, models), distinct, ownedTextures);

		int sharedByUv = shared(PartMerge.canonical(geometries, null));
		GemRender.LOGGER.info("Loaded Bedrock {} as rigid parts: {} bones -> {} parts ({} distinct meshes, "
				+ "{} shared, of which {} only because their pixels match), {} vertices, {} moving bones "
				+ "({} clip-driven, {} gameplay), {} animations {}",
				location, skeleton.slotCount(), parts.size(), distinct, shared, shared - sharedByUv,
				rigid.stream()
						.mapToInt(part -> part.geometry() == null ? 0 : part.geometry()
								.vertexCount())
						.sum(),
				moving.size(), clipDriven, moving.size() - clipDriven, animations.size(),
				animations.keySet());

		return out;
	}

	private static int shared(int[] canonical) {
		int shared = 0;
		for (int i = 0; i < canonical.length; i++) {
			if (canonical[i] != i) {
				shared++;
			}
		}
		return shared;
	}

	private static Model partModel(MeshGeometry mesh, Material colour, @Nullable Material depthOnly) {
		RigidMesh rigidMesh = new RigidMesh(mesh);
		List<Model.ConfiguredMesh> configured = new ArrayList<>();
		configured.add(new Model.ConfiguredMesh(colour, rigidMesh));
		if (depthOnly != null) {
			configured.add(new Model.ConfiguredMesh(depthOnly, rigidMesh));
		}
		return new SimpleModel(configured);
	}

	private static Vector4fc restBoundingSphere(List<BedrockParts.RigidPart> rigid, List<Model> models) {
		Matrix4f[] world = new Matrix4f[rigid.size()];
		Vector3f centre = new Vector3f();
		float radius = 0.0f;

		for (int i = 0; i < rigid.size(); i++) {
			BedrockParts.RigidPart part = rigid.get(i);
			world[i] = part.parent() < 0 ? new Matrix4f(part.restLocal())
					: new Matrix4f(world[part.parent()]).mul(part.restLocal());

			Model model = models.get(i);
			if (model == null) {
				continue;
			}

			Vector4fc sphere = model.boundingSphere();
			world[i].transformPosition(centre.set(sphere.x(), sphere.y(), sphere.z()));
			radius = Math.max(radius, centre.length() + sphere.w());
		}

		return new Vector4f(0.0f, 0.0f, 0.0f, radius);
	}

	@Nullable
	private static PartMerge.Pixels sheet(BedrockGeometry geometry, ResourceLocation location) {
		ResourceLocation source = texture(geometry, location);
		if (source == null) {
			return null;
		}

		com.mojang.blaze3d.platform.NativeImage image = ModelTextures.read(source);
		if (image == null) {
			return null;
		}

		try (image) {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] argb = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					argb[y * width + x] = image.getPixelRGBA(x, y);
				}
			}

			return new PartMerge.Pixels() {
				@Override
				public int width() {
					return width;
				}

				@Override
				public int height() {
					return height;
				}

				@Override
				public int argb(int x, int y) {
					return argb[y * width + x];
				}
			};
		}
	}

	private static GltfMaterial material(BedrockGeometry geometry, ResourceLocation location,
			List<ResourceLocation> ownedTextures) {
		ResourceLocation texture = texture(geometry, location);
		return new GltfMaterial(
				texture == null ? GltfMaterial.UNTEXTURED
						: ModelTextures.materialTexture(texture, ownedTextures),
				alphaMode(geometry.alpha()), 0.1f, !geometry.cull());
	}

	private static Map<Integer, List<Part>> bySlot(BedrockSkeleton skeleton) {
		Map<Integer, List<Part>> grouped = new TreeMap<>();
		for (Part part : skeleton.parts()) {
			grouped.computeIfAbsent(part.slot(), key -> new ArrayList<>())
					.add(part);
		}
		return grouped;
	}

	@Nullable
	private static ResourceLocation texture(BedrockGeometry geometry, ResourceLocation location) {
		if (geometry.texture() != null) {
			return ResourceLocation.parse(geometry.texture());
		}

		ResourceLocation sibling = sibling(location, ".png");
		if (Minecraft.getInstance()
				.getResourceManager()
				.getResource(sibling)
				.isPresent()) {
			return sibling;
		}

		GemRender.LOGGER.warn("Bedrock geometry {} has no texture: there is no {} and its description names "
				+ "no 'gemrender:texture'. It will render untextured.", location, sibling);
		return null;
	}

	private static GltfMaterial.AlphaMode alphaMode(String alpha) {
		return switch (alpha) {
			case "opaque" -> GltfMaterial.AlphaMode.OPAQUE;
			case "blend" -> GltfMaterial.AlphaMode.BLEND;
			default -> GltfMaterial.AlphaMode.MASK;
		};
	}

	private static Map<String, GltfAnimation> animations(ResourceLocation location,
			BedrockSkeleton skeleton) {
		JsonObject clips = animationJson(location);
		if (clips == null) {
			return Map.of();
		}
		return BedrockAnimations.parse(clips, skeleton, sibling(location, ".animation.json").toString());
	}

	@Nullable
	private static JsonObject animationJson(ResourceLocation location) {
		ResourceLocation clips = sibling(location, ".animation.json");
		Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance()
				.getResourceManager()
				.getResource(clips);
		if (resource.isEmpty()) {
			return null;
		}

		try (InputStream in = resource.get()
				.open()) {
			return parse(in);
		} catch (IOException | RuntimeException e) {
			GemRender.LOGGER.error("Could not read Bedrock animations {}; the model will render at rest",
					clips, e);
			return null;
		}
	}

	static ResourceLocation sibling(ResourceLocation location, String suffix) {
		String path = location.getPath();
		if (path.endsWith(GEOMETRY_SUFFIX)) {
			path = path.substring(0, path.length() - GEOMETRY_SUFFIX.length());
		} else if (path.endsWith(".json")) {
			path = path.substring(0, path.length() - ".json".length());
		}
		return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path + suffix);
	}

	private static JsonObject read(ResourceLocation location) throws IOException {
		try (InputStream in = Minecraft.getInstance()
				.getResourceManager()
				.getResourceOrThrow(location)
				.open()) {
			return parse(in);
		}
	}

	private static JsonObject parse(InputStream in) {
		return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
				.getAsJsonObject();
	}
}
