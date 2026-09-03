package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.wf.gemrender.bedrock.BedrockParts.RigidPart;
import com.wf.gemrender.gltf.MeshGeometry;

class BedrockFleetAnalysisTest {
	private static final int BONE_MATRIX_BYTES = 64;

	private static final int INSTANCE_BYTES = 96;

	@Test
	void reportPartitionAcrossAFleet() throws IOException {
		String root = System.getProperty("gemrender.analysis.fleet");
		assumeTrue(root != null && Files.isDirectory(Path.of(root)),
				"set -PanalysisFleet to a directory of Bedrock vehicles");

		List<Vehicle> fleet = new ArrayList<>();
		try (var entries = Files.list(Path.of(root))) {
			for (Path dir : entries.filter(Files::isDirectory)
					.sorted()
					.toList()) {
				Vehicle vehicle = read(dir);
				if (vehicle != null) {
					fleet.add(vehicle);
				}
			}
		}

		assumeTrue(!fleet.isEmpty(), "no <name>/<name>.geo.json under " + root);

		printPartition(fleet);
		printMeshSharing(fleet);
		printPerClipMotion(fleet);
		printBudget(fleet);

		assertThat(fleet).allSatisfy(vehicle -> {
			assertThat(vehicle.parts).isNotEmpty();
			assertThat(vehicle.parts.size()).isLessThanOrEqualTo(vehicle.bones);
		});
	}

	private static void printPartition(List<Vehicle> fleet) {
		System.out.printf("%n=== partition: what the tree splits into ===%n");
		System.out.printf("%-12s %6s %6s %8s %7s %7s %7s %7s  %s%n",
				"vehicle", "bones", "cubes", "verts", "clipBn", "gameBn", "parts", "bone/prt", "largest part");

		for (Vehicle v : fleet) {
			RigidPart largest = v.parts.stream()
					.filter(part -> part.geometry() != null)
					.max((a, b) -> Integer.compare(a.geometry()
							.vertexCount(),
							b.geometry()
									.vertexCount()))
					.orElse(null);

			System.out.printf("%-12s %6d %6d %8d %7d %7d %7d %7.1f  %s%n",
					v.name, v.bones, v.cubes, v.vertices, v.clipDriven.size(), v.gameplay.size(),
					v.parts.size(), v.bones / (double) v.parts.size(),
					largest == null ? "-"
							: String.format("%s (%d bones, %d verts, %d%% of the model)", largest.name(),
									largest.slots().length, largest.geometry()
											.vertexCount(),
									Math.round(100.0 * largest.geometry()
											.vertexCount() / v.vertices)));
		}
	}

	private static void printMeshSharing(List<Vehicle> fleet) {
		System.out.printf("%n=== mesh sharing: how many instancers the level actually needs ===%n");
		System.out.printf("%-12s %7s %8s %8s %9s  %s%n",
				"vehicle", "parts", "byBits", "byPixel", "saved", "note");

		for (Vehicle v : fleet) {
			List<MeshGeometry> meshes = v.parts.stream()
					.map(RigidPart::geometry)
					.toList();

			int withGeometry = (int) meshes.stream()
					.filter(java.util.Objects::nonNull)
					.count();
			int byBits = distinct(PartMerge.canonical(meshes, null), meshes);
			int byPixel = v.pixels == null ? byBits : distinct(PartMerge.canonical(meshes, v.pixels), meshes);

			System.out.printf("%-12s %7d %8d %8d %8d%%  %s%n",
					v.name, withGeometry, byBits, byPixel,
					withGeometry == 0 ? 0 : Math.round(100.0 * (withGeometry - byPixel) / withGeometry),
					v.pixels == null ? "no texture, so UVs are compared bit-for-bit"
							: byPixel == byBits
									? (byBits == withGeometry ? "no two parts are the same shape"
											: "every match was already a bit-for-bit one")
									: (byBits - byPixel) + " parts merged only because their pixels match");
		}
	}

	private static void printPerClipMotion(List<Vehicle> fleet) {
		System.out.printf("%n=== per clip: how much of the vehicle a given animation actually moves ===%n");
		System.out.printf("%-12s %-22s %7s %8s %8s%n", "vehicle", "clip", "parts", "moving", "share");

		for (Vehicle v : fleet) {
			if (v.clips.isEmpty()) {
				System.out.printf("%-12s %-22s %7d %8d %7d%%%n", v.name, "<none>", v.parts.size(), 0, 0);
				continue;
			}
			for (Map.Entry<String, Set<String>> clip : v.clips.entrySet()) {
				print(v, clip.getKey(), clip.getValue());
			}
			if (!v.gameplay.isEmpty()) {
				print(v, "<gameplay, no clip>", v.gameplay);
			}
		}
	}

	private static void print(Vehicle vehicle, String label, Set<String> bones) {
		int moving = movingParts(vehicle, bones);
		System.out.printf("%-12s %-22s %7d %8d %7d%%%n", vehicle.name, label, vehicle.parts.size(), moving,
				Math.round(100.0 * moving / vehicle.parts.size()));
	}

	private static void printBudget(List<Vehicle> fleet) {
		System.out.printf("%n=== per copy per frame, on the heaviest clip ===%n");
		System.out.printf("%-12s %10s %12s %10s %12s %9s%n",
				"vehicle", "palette", "paletteB", "moving", "instanceB", "ratio");

		for (Vehicle v : fleet) {
			int moving = java.util.stream.Stream.concat(v.clips.values()
					.stream(), java.util.stream.Stream.of(v.gameplay))
					.mapToInt(bones -> movingParts(v, bones))
					.max()
					.orElse(0);

			int paletteBytes = v.bones * BONE_MATRIX_BYTES;
			int instanceBytes = moving * INSTANCE_BYTES;

			System.out.printf("%-12s %10d %11dB %10d %11dB %8.1fx%n",
					v.name, v.bones, paletteBytes, moving, instanceBytes,
					instanceBytes == 0 ? Double.POSITIVE_INFINITY : paletteBytes / (double) instanceBytes);
		}

		System.out.printf("%nThe palette column is unconditional: the pose cache clears every frame, so a "
				+ "parked vehicle costs the same as a moving one.%nThe instance column is only paid on the "
				+ "frames a part's transform actually changes.%n");
	}

	private static int movingParts(Vehicle vehicle, Set<String> clipBones) {
		boolean[] moving = new boolean[vehicle.parts.size()];
		int count = 0;

		for (int i = 0; i < vehicle.parts.size(); i++) {
			RigidPart part = vehicle.parts.get(i);
			int parent = part.parent();
			moving[i] = clipBones.contains(part.name()) || (parent >= 0 && moving[parent]);
			if (moving[i]) {
				count++;
			}
		}
		return count;
	}

	private static int distinct(int[] canonical, List<MeshGeometry> meshes) {
		int distinct = 0;
		for (int i = 0; i < canonical.length; i++) {
			if (meshes.get(i) != null && canonical[i] == i) {
				distinct++;
			}
		}
		return distinct;
	}

	private record Vehicle(String name, int bones, int cubes, int vertices, Set<String> clipDriven,
			Set<String> gameplay, Map<String, Set<String>> clips, List<RigidPart> parts,
			@Nullable PartMerge.Pixels pixels) {
	}

	@Nullable
	private static Vehicle read(Path dir) throws IOException {
		String name = dir.getFileName()
				.toString();
		Path model = dir.resolve(name + ".geo.json");
		if (!Files.exists(model)) {
			return null;
		}

		BedrockGeometry geometry = BedrockGeometry.parse(json(model), model.toString());
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);

		Map<String, Set<String>> clips = clips(dir.resolve(name + ".animation.json"));
		Set<String> driven = new LinkedHashSet<>();
		clips.values()
				.forEach(driven::addAll);

		Set<String> gameplay = new LinkedHashSet<>();
		for (String bone : System.getProperty("gemrender.analysis.gameplayBones", "")
				.split(",")) {
			if (!bone.isBlank() && skeleton.table()
					.slotOfName(bone.trim()) >= 0) {
				gameplay.add(bone.trim());
			}
		}

		Set<String> moving = new LinkedHashSet<>(driven);
		moving.addAll(gameplay);

		List<RigidPart> parts = BedrockParts.partition(geometry, skeleton, moving);
		int vertices = parts.stream()
				.mapToInt(part -> part.geometry() == null ? 0 : part.geometry()
						.vertexCount())
				.sum();

		return new Vehicle(name, skeleton.slotCount(), skeleton.parts()
				.size(), vertices, driven, gameplay, clips, parts, pixels(dir.resolve(name + ".png")));
	}

	private static Map<String, Set<String>> clips(Path path) throws IOException {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		if (!Files.exists(path)) {
			return out;
		}

		JsonObject animations = json(path).getAsJsonObject("animations");
		if (animations == null) {
			return out;
		}

		for (String clip : animations.keySet()) {
			JsonObject bones = animations.getAsJsonObject(clip)
					.getAsJsonObject("bones");
			out.put(shortName(clip), bones == null ? Set.of() : new TreeSet<>(bones.keySet()));
		}
		return out;
	}

	private static String shortName(String clip) {
		int dot = clip.lastIndexOf('.');
		return dot < 0 ? clip : clip.substring(dot + 1);
	}

	@Nullable
	private static PartMerge.Pixels pixels(Path path) throws IOException {
		if (!Files.exists(path)) {
			return null;
		}

		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null) {
			return null;
		}

		int width = image.getWidth();
		int height = image.getHeight();
		int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

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

	private static JsonObject json(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
				.getAsJsonObject();
	}
}
