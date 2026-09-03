package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import com.wf.gemrender.bedrock.BedrockParts.RigidPart;

class BedrockPartsAnalysisTest {
	private static final int BONE_MATRIX_BYTES = 64;
	private static final int INSTANCE_BYTES = 96;

	@Test
	void reportPartitionForARealVehicle() throws IOException {
		String model = System.getProperty("gemrender.analysis.model");
		assumeTrue(model != null && Files.exists(Path.of(model)),
				"set -Dgemrender.analysis.model to a Bedrock geometry file");

		BedrockGeometry geometry = BedrockGeometry.parse(read(Path.of(model)), model);
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);

		Set<String> moving = new LinkedHashSet<>(clipDrivenBones());
		moving.addAll(gameplayBones());

		List<RigidPart> parts = BedrockParts.partition(geometry, skeleton, moving);

		int cubes = skeleton.parts()
				.size();
		int bones = skeleton.slotCount();
		int vertices = parts.stream()
				.mapToInt(part -> part.geometry() == null ? 0 : part.geometry()
						.vertexCount())
				.sum();

		List<RigidPart> withGeometry = parts.stream()
				.filter(part -> part.geometry() != null)
				.toList();

		System.out.printf("%n=== %s ===%n", Path.of(model).getFileName());
		System.out.printf("bones %d, cubes %d, vertices %d%n", bones, cubes, vertices);
		System.out.printf("moving bones declared: %d (%d clip-driven, %d gameplay)%n",
				moving.size(), clipDrivenBones().size(), gameplayBones().size());
		System.out.printf("rigid parts: %d (%d carry geometry)  ->  %.2fx fewer transforms than bones%n",
				parts.size(), withGeometry.size(), bones / (double) parts.size());

		System.out.println("largest parts:");
		parts.stream()
				.filter(part -> part.geometry() != null)
				.sorted((a, b) -> Integer.compare(b.geometry()
						.vertexCount(),
						a.geometry()
								.vertexCount()))
				.limit(6)
				.forEach(part -> System.out.printf("   %-24s %3d bones %6d verts%n", part.name(),
						part.slots().length, part.geometry()
								.vertexCount()));

		System.out.printf("%nper copy, per frame:%n");
		System.out.printf("   skinned palette (today) : %d matrices, %d B, rebuilt from zero every frame%n",
				bones, bones * BONE_MATRIX_BYTES);
		System.out.printf("   rigid parts, all moving : %d instances, %d B%n",
				parts.size(), parts.size() * INSTANCE_BYTES);
		int turretOnly = (int) parts.stream()
				.filter(part -> gameplayBones().contains(part.name()))
				.count();
		System.out.printf("   rigid parts, turret only: %d instances, %d B (the rest upload nothing)%n",
				turretOnly, turretOnly * INSTANCE_BYTES);

		assertThat(parts).isNotEmpty();
		assertThat(parts.size()).isLessThanOrEqualTo(bones);
	}

	private static Set<String> gameplayBones() {
		String raw = System.getProperty("gemrender.analysis.gameplayBones", "");
		Set<String> out = new LinkedHashSet<>();
		for (String name : raw.split(",")) {
			if (!name.isBlank()) {
				out.add(name.trim());
			}
		}
		return out;
	}

	private static Set<String> clipDrivenBones() {
		String path = System.getProperty("gemrender.analysis.animation");
		if (path == null || !Files.exists(Path.of(path))) {
			return Set.of();
		}

		Set<String> out = new LinkedHashSet<>();
		try {
			JsonObject root = read(Path.of(path));
			JsonObject animations = root.getAsJsonObject("animations");
			if (animations == null) {
				return out;
			}
			for (String clip : animations.keySet()) {
				JsonObject bones = animations.getAsJsonObject(clip)
						.getAsJsonObject("bones");
				if (bones != null) {
					out.addAll(new ArrayList<>(bones.keySet()));
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("could not read " + path, e);
		}
		return out;
	}

	private static JsonObject read(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
				.getAsJsonObject();
	}
}
