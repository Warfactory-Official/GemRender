package com.wf.gemrender.bedrock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import com.wf.gemrender.GemRender;

public record BedrockGeometry(@Nullable String identifier, int textureWidth, int textureHeight,
		List<Bone> bones, @Nullable String texture, String alpha, boolean cull,
		List<String> gameplayBones) {
	public enum Face {
		DOWN, UP, NORTH, SOUTH, WEST, EAST;

		public Face opposite() {
			return switch (this) {
				case DOWN -> UP;
				case UP -> DOWN;
				case NORTH -> SOUTH;
				case SOUTH -> NORTH;
				case WEST -> EAST;
				case EAST -> WEST;
			};
		}
	}

	public record Bone(String name, @Nullable String parent, float[] pivot, @Nullable float[] rotation,
			boolean mirror, @Nullable Float inflate, List<Cube> cubes) {
	}

	public record Cube(float[] origin, float[] size, @Nullable float[] pivot, @Nullable float[] rotation,
			@Nullable Float inflate, @Nullable Boolean mirror, @Nullable float[] boxUv,
			@Nullable Map<Face, FaceUv> faceUv) {
	}

	public record FaceUv(float[] uv, float[] uvSize, int rotation) {
	}

	private static final int DEFAULT_TEXTURE_SIZE = 16;

	public static BedrockGeometry parse(JsonObject root, String source) {
		String version = root.has("format_version") ? root.get("format_version")
				.getAsString() : null;

		JsonObject geometry = current(root, source);
		boolean legacy = geometry == null;
		if (legacy) {
			geometry = legacy(root, source);
		}
		if (geometry == null) {
			throw new JsonParseException(source + " has neither a 'minecraft:geometry' array nor a "
					+ "'geometry.<name>' object, so it is not a Bedrock geometry file"
					+ (version == null ? "" : " (format_version " + version + ")"));
		}

		JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description")
				: geometry;

		int width = textureSize(description, "texture_width", "texturewidth", source);
		int height = textureSize(description, "texture_height", "textureheight", source);

		List<Bone> bones = new ArrayList<>();
		for (JsonElement element : array(geometry, "bones")) {
			bones.add(bone(element.getAsJsonObject()));
		}

		return new BedrockGeometry(string(description, "identifier"), width, height, List.copyOf(bones),
				string(description, "gemrender:texture"),
				description.has("gemrender:alpha") ? description.get("gemrender:alpha")
						.getAsString()
						.toLowerCase(java.util.Locale.ROOT) : "cutout",
				description.has("gemrender:cull") && description.get("gemrender:cull")
						.getAsBoolean(),
				names(description, "gemrender:gameplay_bones"));
	}

	private static List<String> names(JsonObject description, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement element : array(description, key)) {
			out.add(element.getAsString());
		}
		return List.copyOf(out);
	}

	@Nullable
	private static JsonObject current(JsonObject root, String source) {
		if (!root.has("minecraft:geometry")) {
			return null;
		}

		JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
		if (geometries.isEmpty()) {
			return null;
		}
		if (geometries.size() > 1) {
			GemRender.LOGGER.warn("{} declares {} geometries; GemRender loads the first and ignores the rest",
					source, geometries.size());
		}
		return geometries.get(0)
				.getAsJsonObject();
	}

	@Nullable
	private static JsonObject legacy(JsonObject root, String source) {
		JsonObject found = null;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!entry.getKey()
					.startsWith("geometry.")
					|| !entry.getValue()
							.isJsonObject()) {
				continue;
			}
			if (found == null) {
				found = entry.getValue()
						.getAsJsonObject();
			} else {
				GemRender.LOGGER.warn("{} declares several 'geometry.<name>' objects; GemRender loads the "
						+ "first and ignores the rest", source);
				break;
			}
		}
		return found;
	}

	private static int textureSize(JsonObject description, String key, String legacyKey, String source) {
		JsonElement value = description.has(key) ? description.get(key) : description.get(legacyKey);
		if (value == null || value.getAsInt() <= 0) {
			GemRender.LOGGER.warn("{} does not declare {}; assuming {}. Its UVs will be wrong unless the "
					+ "texture really is that wide.", source, key, DEFAULT_TEXTURE_SIZE);
			return DEFAULT_TEXTURE_SIZE;
		}
		return value.getAsInt();
	}

	private static Bone bone(JsonObject json) {
		List<Cube> cubes = new ArrayList<>();
		for (JsonElement element : array(json, "cubes")) {
			cubes.add(cube(element.getAsJsonObject()));
		}

		unsupported(json, "poly_mesh", "texture_meshes");

		return new Bone(string(json, "name"), string(json, "parent"), vector(json, "pivot", 0.0f),
				optionalVector(json, "rotation"),
				json.has("mirror") && json.get("mirror")
						.getAsBoolean(),
				optionalFloat(json, "inflate"), List.copyOf(cubes));
	}

	private static Cube cube(JsonObject json) {
		JsonElement uv = json.get("uv");
		float[] boxUv = null;
		Map<Face, FaceUv> faceUv = null;

		if (uv == null) {
			boxUv = new float[] { 0.0f, 0.0f };
		} else if (uv.isJsonArray()) {
			boxUv = floats(uv.getAsJsonArray(), 2, 0.0f);
		} else {
			faceUv = faces(uv.getAsJsonObject());
		}

		return new Cube(vector(json, "origin", 0.0f), vector(json, "size", 0.0f),
				optionalVector(json, "pivot"), optionalVector(json, "rotation"),
				optionalFloat(json, "inflate"),
				json.has("mirror") ? json.get("mirror")
						.getAsBoolean() : null,
				boxUv, faceUv);
	}

	private static Map<Face, FaceUv> faces(JsonObject json) {
		Map<Face, FaceUv> faces = new EnumMap<>(Face.class);
		for (Face face : Face.values()) {
			JsonElement element = json.get(face.name()
					.toLowerCase(java.util.Locale.ROOT));
			if (element == null || !element.isJsonObject()) {
				continue;
			}

			JsonObject entry = element.getAsJsonObject();
			faces.put(face, new FaceUv(vector2(entry, "uv"), vector2(entry, "uv_size"),
					entry.has("uv_rotation") ? entry.get("uv_rotation")
							.getAsInt() : 0));
		}
		return faces.isEmpty() ? Map.of() : new LinkedHashMap<>(faces);
	}

	private static void unsupported(JsonObject json, String... keys) {
		for (String key : keys) {
			if (json.has(key)) {
				GemRender.LOGGER.warn("Bedrock bone '{}' has a '{}', which GemRender does not read; that "
						+ "geometry will be missing.", string(json, "name"), key);
			}
		}
	}

	private static JsonArray array(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || !value.isJsonArray() ? new JsonArray() : value.getAsJsonArray();
	}

	@Nullable
	private static String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	@Nullable
	private static Float optionalFloat(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsFloat();
	}

	private static float[] vector(JsonObject json, String key, float fill) {
		float[] value = optionalVector(json, key);
		if (value != null) {
			return value;
		}
		return new float[] { fill, fill, fill };
	}

	private static float[] vector2(JsonObject json, String key) {
		JsonElement value = json.get(key);
		if (value == null || !value.isJsonArray()) {
			return new float[] { 0.0f, 0.0f };
		}
		return floats(value.getAsJsonArray(), 2, 0.0f);
	}

	@Nullable
	private static float[] optionalVector(JsonObject json, String key) {
		JsonElement value = json.get(key);
		if (value == null || !value.isJsonArray()) {
			return null;
		}
		return floats(value.getAsJsonArray(), 3, 0.0f);
	}

	private static float[] floats(JsonArray array, int count, float fill) {
		float[] out = new float[count];
		for (int i = 0; i < count; i++) {
			out[i] = i < array.size() ? array.get(i)
					.getAsFloat() : fill;
		}
		return out;
	}
}
