package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.bedrock.BedrockParts.RigidPart;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.NodeTable;

class BedrockPartsTest {

	@Test
	void nothingMovingCollapsesTheWholeSkeletonToOnePart() {
		BedrockSkeleton skeleton = BedrockFixture.skeleton(BedrockFixture.CHAIN);
		List<RigidPart> parts = BedrockParts.partition(BedrockFixture.geometry(BedrockFixture.CHAIN),
				skeleton, Set.of());

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0)
				.slots()).hasSize(skeleton.slotCount());
		assertThat(parts.get(0)
				.parent()).isEqualTo(-1);
	}

	@Test
	void cuttingAboveAMovingBoneSplitsTheTree() {
		BedrockSkeleton skeleton = BedrockFixture.skeleton(BedrockFixture.CHAIN);
		List<RigidPart> parts = BedrockParts.partition(BedrockFixture.geometry(BedrockFixture.CHAIN),
				skeleton, Set.of("hand"));

		assertThat(parts).hasSize(2);
		assertThat(parts).extracting(RigidPart::name)
				.containsExactlyInAnyOrder("root", "hand");

		RigidPart hand = parts.stream()
				.filter(part -> part.name()
						.equals("hand"))
				.findFirst()
				.orElseThrow();
		assertThat(hand.parent()).isZero();
		assertThat(hand.slots()).hasSize(1);
	}

	@Test
	void partsReproduceTheMergedRestPoseExactly() {
		for (Set<String> moving : List.of(Set.<String>of(), Set.of("hand"), Set.of("arm"),
				Set.of("arm", "hand"))) {
			assertRestPoseMatches(BedrockFixture.CHAIN, moving);
		}
	}

	private static void assertRestPoseMatches(String source, Set<String> moving) {
		BedrockGeometry geometry = BedrockFixture.geometry(source);
		BedrockSkeleton skeleton = BedrockFixture.skeleton(source);
		NodeTable table = skeleton.table();

		List<Vector3f> merged = sorted(mergedRestVertices(geometry, skeleton, table));
		List<Vector3f> split = sorted(partRestVertices(geometry, skeleton, moving));

		assertThat(split).as("vertex count for moving=%s", moving)
				.hasSameSizeAs(merged);

		for (int i = 0; i < merged.size(); i++) {
			assertThat(split.get(i)
					.distance(merged.get(i))).as("vertex %s for moving=%s", i, moving)
					.isLessThan(1.0e-4f);
		}
	}

	private static List<Vector3f> sorted(List<Vector3f> points) {
		return points.stream()
				.sorted((a, b) -> {
					int x = Float.compare(round(a.x), round(b.x));
					if (x != 0) {
						return x;
					}
					int y = Float.compare(round(a.y), round(b.y));
					return y != 0 ? y : Float.compare(round(a.z), round(b.z));
				})
				.toList();
	}

	private static float round(float value) {
		return Math.round(value * 8192.0f) / 8192.0f;
	}

	private static List<Vector3f> mergedRestVertices(BedrockGeometry geometry,
			BedrockSkeleton skeleton, NodeTable table) {
		Matrix4f[] world = worldRest(table);
		List<Vector3f> out = new java.util.ArrayList<>();

		for (Map.Entry<Integer, List<BedrockSkeleton.Part>> group : bySlot(skeleton).entrySet()) {
			BedrockCubes cubes = new BedrockCubes();
			for (BedrockSkeleton.Part part : group.getValue()) {
				cubes.add(part.cube(), part.pivot(), part.mirror(), part.inflate(),
						geometry.textureWidth(), geometry.textureHeight(), part.placement());
			}
			float[] positions = cubes.positions();
			for (int v = 0; v < cubes.vertexCount(); v++) {
				Vector3f point = new Vector3f(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
				world[group.getKey()].transformPosition(point);
				out.add(point);
			}
		}
		return out;
	}

	private static List<Vector3f> partRestVertices(BedrockGeometry geometry,
			BedrockSkeleton skeleton, Set<String> moving) {
		List<RigidPart> parts = BedrockParts.partition(geometry, skeleton, moving);

		Matrix4f[] partWorld = new Matrix4f[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			RigidPart part = parts.get(i);
			partWorld[i] = part.parent() < 0 ? new Matrix4f(part.restLocal())
					: new Matrix4f(partWorld[part.parent()]).mul(part.restLocal());
		}

		List<Vector3f> out = new java.util.ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			MeshGeometry mesh = parts.get(i)
					.geometry();
			if (mesh == null) {
				continue;
			}
			for (int v = 0; v < mesh.vertexCount(); v++) {
				Vector3f point = new Vector3f(mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
				partWorld[i].transformPosition(point);
				out.add(point);
			}
		}
		return out;
	}

	private static Matrix4f[] worldRest(NodeTable table) {
		Matrix4f[] world = new Matrix4f[table.nodeCount()];
		float[] rest = table.newScratch();
		table.resetToRest(rest);
		int[] parents = table.parentSlots();

		for (int slot : table.evaluationOrder()) {
			Matrix4f local = new Matrix4f();
			table.localTransform(rest, slot, local);
			world[slot] = parents[slot] < 0 ? local : new Matrix4f(world[parents[slot]]).mul(local);
		}
		return world;
	}

	private static Map<Integer, List<BedrockSkeleton.Part>> bySlot(BedrockSkeleton skeleton) {
		Map<Integer, List<BedrockSkeleton.Part>> grouped = new java.util.TreeMap<>();
		for (BedrockSkeleton.Part part : skeleton.parts()) {
			grouped.computeIfAbsent(part.slot(), key -> new java.util.ArrayList<>())
					.add(part);
		}
		return grouped;
	}
}
