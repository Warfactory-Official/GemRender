package com.wf.gemrender.bedrock;

import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.ROTATION_PATH;
import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.SCALE_PATH;
import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.TRANSLATION_PATH;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;

public final class BedrockAnimations {
	private BedrockAnimations() {
	}

	public static java.util.Set<String> drivenBones(JsonObject root) {
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		if (!root.has("animations")) {
			return out;
		}

		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations")
				.entrySet()) {
			if (!entry.getValue()
					.isJsonObject()) {
				continue;
			}
			JsonElement bones = entry.getValue()
					.getAsJsonObject()
					.get("bones");
			if (bones != null && bones.isJsonObject()) {
				out.addAll(bones.getAsJsonObject()
						.keySet());
			}
		}
		return out;
	}

	public static Map<String, GltfAnimation> parse(JsonObject root, BedrockSkeleton skeleton, String source) {
		Map<String, GltfAnimation> animations = new LinkedHashMap<>();
		if (!root.has("animations")) {
			GemRender.LOGGER.warn("{} has no 'animations' object, so it defines no clips", source);
			return animations;
		}

		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations")
				.entrySet()) {
			try {
				GltfAnimation animation = animation(entry.getKey(), entry.getValue()
						.getAsJsonObject(), skeleton, source);
				if (animation != null) {
					animations.put(entry.getKey(), animation);
				}
			} catch (RuntimeException e) {
				GemRender.LOGGER.error("Could not read clip '{}' in {}; the model's other clips are "
						+ "unaffected", entry.getKey(), source, e);
			}
		}
		return animations;
	}

	private static GltfAnimation animation(String name, JsonObject clip, BedrockSkeleton skeleton,
			String source) {
		NodeTable table = skeleton.table();
		List<PoseDriver> drivers = new ArrayList<>();
		float longestChannel = 0.0f;

		if (clip.has("bones")) {
			for (Map.Entry<String, JsonElement> entry : clip.getAsJsonObject("bones")
					.entrySet()) {
				int slot = table.slotOfName(entry.getKey());
				if (slot < 0) {
					GemRender.LOGGER.warn("Clip '{}' in {} animates bone '{}', which the model does not "
							+ "have; skipping it.", name, source, entry.getKey());
					continue;
				}

				JsonObject bone = entry.getValue()
						.getAsJsonObject();
				for (String property : new String[] { "position", "rotation", "scale" }) {
					BedrockTrack track = track(bone.get(property), name, entry.getKey(), property, source);
					if (track == null) {
						continue;
					}

					drivers.add(driver(property, track, table, slot, skeleton));
					longestChannel = Math.max(longestChannel, track.lastKeyTime());
				}
			}
		}

		if (drivers.isEmpty()) {
			GemRender.LOGGER.warn("Clip '{}' in {} drives nothing on this model; skipping it.", name, source);
			return null;
		}

		float length = clip.has("animation_length") ? clip.get("animation_length")
				.getAsFloat() : 0.0f;
		if (length <= 0.0f) {
			length = longestChannel;
		}

		JsonElement loop = clip.get("loop");
		if (loop != null && !(loop.isJsonPrimitive() && loop.getAsJsonPrimitive()
				.isBoolean() && loop.getAsBoolean())) {
			GemRender.LOGGER.info("Clip '{}' in {} declares loop={}; GemRender plays every clip on a loop, "
					+ "because a phase is a position in a cycle rather than a playhead.", name, source, loop);
		}

		for (String unsupported : new String[] { "sound_effects", "particle_effects", "timeline" }) {
			if (clip.has(unsupported)) {
				GemRender.LOGGER.info("Clip '{}' in {} has '{}', which GemRender has nowhere to deliver.",
						name, source, unsupported);
			}
		}

		return new GltfAnimation(name, drivers, length);
	}

	private static PoseDriver driver(String property, BedrockTrack track, NodeTable table, int slot,
			BedrockSkeleton skeleton) {
		return switch (property) {
			case "position" -> new BedrockChannel.Position(track, table.offsetFor(slot, TRANSLATION_PATH));
			case "scale" -> new BedrockChannel.Scale(track, table.offsetFor(slot, SCALE_PATH));
			default -> new BedrockChannel.Rotation(track, table.offsetFor(slot, ROTATION_PATH),
					skeleton.restEuler(slot, 0), skeleton.restEuler(slot, 1), skeleton.restEuler(slot, 2));
		};
	}

	private static BedrockTrack track(JsonElement channel, String clip, String bone, String property,
			String source) {
		if (channel == null || channel.isJsonNull()) {
			return null;
		}

		try {
			List<BedrockTrack.Key> keys = keys(channel);
			return keys.isEmpty() ? null : BedrockTrack.of(keys);
		} catch (Molang.MolangException | IllegalArgumentException e) {
			GemRender.LOGGER.warn("Clip '{}' in {} cannot drive {} on bone '{}', so that channel is "
					+ "dropped and the bone keeps its rest {}: {}",
					clip, source, property, bone, property, e.getMessage());
			return null;
		}
	}

	private static List<BedrockTrack.Key> keys(JsonElement channel) {
		List<BedrockTrack.Key> keys = new ArrayList<>();

		if (channel.isJsonPrimitive() || channel.isJsonArray()
				|| channel.getAsJsonObject()
						.has("vector")) {
			keys.add(key(0.0f, channel));
			return keys;
		}

		for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject()
				.entrySet()) {
			keys.add(key(time(entry.getKey()), entry.getValue()));
		}
		return keys;
	}

	private static float time(String key) {
		try {
			return Float.parseFloat(key);
		} catch (NumberFormatException e) {
			GemRender.LOGGER.warn("Keyframe time '{}' is not a number; treating it as 0", key);
			return 0.0f;
		}
	}

	private static BedrockTrack.Key key(float timeSeconds, JsonElement value) {
		if (value.isJsonPrimitive() || value.isJsonArray()) {
			List<Molang> triple = triple(value);
			return new BedrockTrack.Key(timeSeconds, triple, triple, Easing.LINEAR, Float.NaN);
		}

		JsonObject object = value.getAsJsonObject();

		Easing easing = Easing.of(string(object, object.has("easing") ? "easing" : "lerp_mode"));
		float easingArg = Float.NaN;
		if (object.has("easingArgs") && !object.getAsJsonArray("easingArgs").isEmpty()) {
			easingArg = object.getAsJsonArray("easingArgs")
					.get(0)
					.getAsFloat();
		}

		if (object.has("vector")) {
			List<Molang> triple = triple(object.get("vector"));
			return new BedrockTrack.Key(timeSeconds, triple, triple, easing, easingArg);
		}

		JsonElement pre = object.get("pre");
		JsonElement post = object.get("post");
		if (pre == null && post == null) {
			throw new IllegalArgumentException("keyframe at " + timeSeconds
					+ " has neither a value, a 'vector', nor a 'pre'/'post'");
		}

		List<Molang> preTriple = triple(unwrap(pre == null ? post : pre));
		List<Molang> postTriple = triple(unwrap(post == null ? pre : post));
		return new BedrockTrack.Key(timeSeconds, preTriple, postTriple, easing, easingArg);
	}

	private static JsonElement unwrap(JsonElement side) {
		if (side.isJsonObject() && side.getAsJsonObject()
				.has("vector")) {
			return side.getAsJsonObject()
					.get("vector");
		}
		return side;
	}

	private static List<Molang> triple(JsonElement value) {
		if (!value.isJsonArray()) {
			Molang single = component(value);
			return List.of(single, single, single);
		}

		List<Molang> components = new ArrayList<>(3);
		for (int axis = 0; axis < 3; axis++) {
			components.add(axis < value.getAsJsonArray()
					.size() ? component(value.getAsJsonArray()
							.get(axis)) : new Molang.Const(0.0));
		}
		return List.copyOf(components);
	}

	private static Molang component(JsonElement value) {
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isNumber()) {
			return new Molang.Const(primitive.getAsDouble());
		}
		return Molang.parse(primitive.getAsString());
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
	}
}
