package com.wf.gemrender.bedrock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.wf.gemrender.bedrock.BedrockSkeleton.Part;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.SpriteUv;

public final class BedrockParts {
	public record RigidPart(String name, int rootSlot, int parent, Matrix4f toParent, Matrix4f restLocal,
			MeshGeometry geometry, int[] slots) {
	}

	private BedrockParts() {
	}

	public static List<RigidPart> partition(BedrockGeometry geometry, BedrockSkeleton skeleton,
			Set<String> moving) {
		NodeTable table = skeleton.table();
		int count = table.nodeCount();
		int[] parents = table.parentSlots();

		int[] componentRoot = componentRoots(table, parents, moving);
		Matrix4f[] toComponentRoot = restTransforms(table, parents, componentRoot);

		Map<Integer, List<Integer>> members = new LinkedHashMap<>();
		for (int slot : table.evaluationOrder()) {
			members.computeIfAbsent(componentRoot[slot], key -> new ArrayList<>())
					.add(slot);
		}

		Map<Integer, Integer> indexByRoot = new LinkedHashMap<>();
		for (int root : members.keySet()) {
			indexByRoot.put(root, indexByRoot.size());
		}

		Map<Integer, List<Part>> bySlot = new LinkedHashMap<>();
		for (Part part : skeleton.parts()) {
			bySlot.computeIfAbsent(part.slot(), key -> new ArrayList<>())
					.add(part);
		}

		List<RigidPart> parts = new ArrayList<>(members.size());
		Matrix4f scratch = new Matrix4f();

		for (Map.Entry<Integer, List<Integer>> entry : members.entrySet()) {
			int root = entry.getKey();
			List<Integer> slots = entry.getValue();

			BedrockCubes cubes = new BedrockCubes();
			for (int slot : slots) {
				for (Part part : bySlot.getOrDefault(slot, List.of())) {
					Matrix4f placement = scratch.set(toComponentRoot[slot]);
					if (part.placement() != null) {
						placement.mul(part.placement());
					}
					cubes.add(part.cube(), part.pivot(), part.mirror(), part.inflate(),
							geometry.textureWidth(), geometry.textureHeight(),
							placement.equals(IDENTITY) ? null : placement);
				}
			}

			int parentSlot = parents[root];
			int parentIndex = parentSlot < 0 ? -1 : indexByRoot.get(componentRoot[parentSlot]);

			Matrix4f toParent = parentSlot < 0 ? new Matrix4f()
					: new Matrix4f(toComponentRoot[parentSlot]);
			Matrix4f restLocal = new Matrix4f(toParent).mul(localAtRest(table, root, new Matrix4f()));

			MeshGeometry mesh = cubes.vertexCount() == 0 ? null
					: MeshGeometry.of(cubes.positions(), cubes.normals(), cubes.texCoords(),
							cubes.indices(), VertexSkinning.rigid(cubes.vertexCount(), 0),
							SpriteUv.IDENTITY, 0);

			parts.add(new RigidPart(table.nodeName(root), root, parentIndex, toParent, restLocal, mesh,
					slots.stream()
							.mapToInt(Integer::intValue)
							.toArray()));
		}

		return parts;
	}

	private static final Matrix4f IDENTITY = new Matrix4f();

	private static int[] componentRoots(NodeTable table, int[] parents, Set<String> moving) {
		int[] root = new int[table.nodeCount()];
		Arrays.fill(root, -1);

		for (int slot : table.evaluationOrder()) {
			int parent = parents[slot];
			boolean cut = parent < 0 || moving.contains(table.nodeName(slot));
			root[slot] = cut ? slot : root[parent];
		}
		return root;
	}

	private static Matrix4f[] restTransforms(NodeTable table, int[] parents, int[] componentRoot) {
		Matrix4f[] out = new Matrix4f[table.nodeCount()];

		for (int slot : table.evaluationOrder()) {
			Matrix4f local = localAtRest(table, slot, new Matrix4f());
			int parent = parents[slot];
			if (componentRoot[slot] == slot) {
				out[slot] = new Matrix4f();
			} else {
				out[slot] = new Matrix4f(out[parent]).mul(local);
			}
		}
		return out;
	}

	private static Matrix4f localAtRest(NodeTable table, int slot, Matrix4f out) {
		float[] rest = table.newScratch();
		table.resetToRest(rest);
		table.localTransform(rest, slot, out);
		return out;
	}
}
