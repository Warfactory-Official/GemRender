package com.wf.gemrender.gltf;

import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.ROTATION_PATH;
import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.SCALE_PATH;
import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.TRANSLATION_PATH;
import static com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.WEIGHTS_PATH;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.wf.gemrender.vendor.jgltf.model.MeshModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;

/** Maps glTF nodes to palette slots and TRS offsets; {@code slotOf(node)} feeds the driver factories. */
public final class NodeTable {
	public static final int TRS_STRIDE = 10;

	public static final int TRANSLATION = 0;

	public static final int ROTATION = 3;

	public static final int SCALE = 7;

	private final int nodeCount;

	private final float[] restState;

	private final Matrix4f[] fixedLocal;

	private final int[] parents;

	private final int[] order;

	private final int[] weightBase;

	private final int[] weightCount;

	private final Map<NodeModel, Integer> slots;

	private final String[] names;

	private final Map<String, Integer> nameSlots;

	private NodeTable(int nodeCount, float[] restState, Matrix4f[] fixedLocal, int[] parents, int[] order,
			int[] weightBase, int[] weightCount, Map<NodeModel, Integer> slots, String[] names) {
		this.nodeCount = nodeCount;
		this.restState = restState;
		this.fixedLocal = fixedLocal;
		this.parents = parents;
		this.order = order;
		this.weightBase = weightBase;
		this.weightCount = weightCount;
		this.slots = slots;
		this.names = names;

		Map<String, Integer> byName = new java.util.HashMap<>();
		for (int i = 0; i < nodeCount; i++) {
			if (names[i] != null) {
				byName.putIfAbsent(names[i], i);
			}
		}
		this.nameSlots = Map.copyOf(byName);
	}

	public static NodeTable of(List<NodeModel> nodes) {
		int nodeCount = nodes.size();

		Map<NodeModel, Integer> slots = new IdentityHashMap<>();
		for (int i = 0; i < nodeCount; i++) {
			slots.put(nodes.get(i), i);
		}

		int[] weightCount = new int[nodeCount];
		int[] weightBase = new int[nodeCount];
		int weightCursor = nodeCount * TRS_STRIDE;

		for (int i = 0; i < nodeCount; i++) {
			int count = weightCount(nodes.get(i));
			weightCount[i] = count;
			weightBase[i] = count == 0 ? -1 : weightCursor;
			weightCursor += count;
		}

		float[] restState = new float[weightCursor];
		Matrix4f[] fixedLocal = new Matrix4f[nodeCount];
		int[] parents = new int[nodeCount];
		String[] names = new String[nodeCount];

		for (int i = 0; i < nodeCount; i++) {
			NodeModel node = nodes.get(i);
			names[i] = node.getName();

			NodeModel parent = node.getParent();
			Integer parentSlot = parent == null ? null : slots.get(parent);
			parents[i] = parentSlot == null ? -1 : parentSlot;

			float[] matrix = node.getMatrix();
			if (matrix != null) {
				fixedLocal[i] = new Matrix4f().set(matrix);
			}

			int base = i * TRS_STRIDE;
			copyOrDefault(node.getTranslation(), restState, base + TRANSLATION, 3, 0.0f, -1);
			copyOrDefault(node.getRotation(), restState, base + ROTATION, 4, 0.0f, 3);
			copyOrDefault(node.getScale(), restState, base + SCALE, 3, 1.0f, -1);

			if (weightCount[i] > 0) {
				copyWeights(node, restState, weightBase[i], weightCount[i]);
			}
		}

		return new NodeTable(nodeCount, restState, fixedLocal, parents, evaluationOrder(parents),
				weightBase, weightCount, slots, names);
	}

	public static NodeTable ofNodes(String[] names, int[] parents, float[] trs) {
		int nodeCount = names.length;
		if (parents.length != nodeCount || trs.length != nodeCount * TRS_STRIDE) {
			throw new IllegalArgumentException("node table arrays disagree: " + nodeCount + " names, "
					+ parents.length + " parents, " + trs.length + " floats");
		}

		for (int i = 0; i < nodeCount; i++) {
			if (parents[i] >= nodeCount) {
				throw new IllegalArgumentException("node " + i + " names parent " + parents[i]
						+ ", which is not one of the " + nodeCount + " nodes");
			}
		}

		int[] weightBase = new int[nodeCount];
		java.util.Arrays.fill(weightBase, -1);

		return new NodeTable(nodeCount, trs.clone(), new Matrix4f[nodeCount], parents.clone(),
				evaluationOrder(parents), weightBase, new int[nodeCount], Map.of(), names.clone());
	}

	private static int weightCount(NodeModel node) {
		int count = node.getWeights() == null ? 0 : node.getWeights().length;

		for (MeshModel mesh : node.getMeshModels()) {
			if (mesh.getWeights() != null) {
				count = Math.max(count, mesh.getWeights().length);
			}
			for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
				if (primitive.getTargets() != null) {
					count = Math.max(count, primitive.getTargets()
							.size());
				}
			}
		}
		return count;
	}

	private static void copyWeights(NodeModel node, float[] out, int offset, int count) {
		float[] source = node.getWeights();
		if (source == null) {
			for (MeshModel mesh : node.getMeshModels()) {
				if (mesh.getWeights() != null) {
					source = mesh.getWeights();
					break;
				}
			}
		}
		if (source == null) {
			return;
		}
		System.arraycopy(source, 0, out, offset, Math.min(count, source.length));
	}

	private static void copyOrDefault(float[] source, float[] out, int offset, int count, float fill,
			int unitComponent) {
		if (source != null) {
			System.arraycopy(source, 0, out, offset, Math.min(count, source.length));
			if (source.length >= count) {
				return;
			}
		}
		for (int i = source == null ? 0 : source.length; i < count; i++) {
			out[offset + i] = i == unitComponent ? 1.0f : fill;
		}
	}

	private static int[] evaluationOrder(int[] parents) {
		int count = parents.length;
		int[] depth = new int[count];

		for (int i = 0; i < count; i++) {
			int d = 0;
			for (int node = parents[i]; node >= 0; node = parents[node]) {
				if (++d > count) {
					throw new IllegalArgumentException(
							"node graph has a cycle in the parent chain of node " + i);
				}
			}
			depth[i] = d;
		}

		int maxDepth = 0;
		for (int d : depth) {
			maxDepth = Math.max(maxDepth, d);
		}

		int[] countPerDepth = new int[maxDepth + 2];
		for (int d : depth) {
			countPerDepth[d + 1]++;
		}
		for (int d = 1; d < countPerDepth.length; d++) {
			countPerDepth[d] += countPerDepth[d - 1];
		}

		int[] order = new int[count];
		for (int i = 0; i < count; i++) {
			order[countPerDepth[depth[i]]++] = i;
		}
		return order;
	}

	public int scratchFloats() {
		return restState.length;
	}

	public float[] newScratch() {
		return restState.clone();
	}

	public void resetToRest(float[] scratch) {
		System.arraycopy(restState, 0, scratch, 0, restState.length);
	}

	public void localTransform(float[] scratch, int slot, Matrix4f out) {
		Matrix4f fixed = fixedLocal[slot];
		if (fixed != null) {
			out.set(fixed);
			return;
		}

		int base = slot * TRS_STRIDE;
		out.translationRotateScale(
				scratch[base + TRANSLATION], scratch[base + TRANSLATION + 1], scratch[base + TRANSLATION + 2],
				scratch[base + ROTATION], scratch[base + ROTATION + 1], scratch[base + ROTATION + 2],
				scratch[base + ROTATION + 3],
				scratch[base + SCALE], scratch[base + SCALE + 1], scratch[base + SCALE + 2]);
	}

	public int nodeCount() {
		return nodeCount;
	}

	public int[] evaluationOrder() {
		return order;
	}

	public int[] parentSlots() {
		return parents;
	}

	public int firstRootSlot() {
		for (int slot = 0; slot < nodeCount; slot++) {
			if (parents[slot] < 0) {
				return slot;
			}
		}
		return -1;
	}

	public String nodeName(int slot) {
		if (slot < 0 || slot >= nodeCount) {
			return "<none>";
		}
		String name = names[slot];
		return name == null ? "<unnamed>" : name;
	}

	public int slotOf(NodeModel node) {
		Integer slot = slots.get(node);
		return slot == null ? -1 : slot;
	}

	public int slotOfName(String name) {
		Integer slot = name == null ? null : nameSlots.get(name);
		return slot == null ? -1 : slot;
	}

	public int offsetFor(int slot, String path) {
		if (slot < 0 || slot >= nodeCount) {
			return -1;
		}
		return switch (path) {
			case TRANSLATION_PATH -> slot * TRS_STRIDE + TRANSLATION;
			case ROTATION_PATH -> slot * TRS_STRIDE + ROTATION;
			case SCALE_PATH -> slot * TRS_STRIDE + SCALE;
			case WEIGHTS_PATH -> weightBase[slot];
			default -> -1;
		};
	}

	public int componentsFor(int slot, String path) {
		if (slot < 0 || slot >= nodeCount) {
			return 0;
		}
		return switch (path) {
			case TRANSLATION_PATH, SCALE_PATH -> 3;
			case ROTATION_PATH -> 4;
			case WEIGHTS_PATH -> weightCount[slot];
			default -> 0;
		};
	}

	/** The node whose block of the scratch array contains {@code offset}, or -1 if none does. */
	public int slotOfOffset(int offset) {
		if (offset < 0) {
			return -1;
		}
		if (offset < nodeCount * TRS_STRIDE) {
			return offset / TRS_STRIDE;
		}
		for (int slot = 0; slot < nodeCount; slot++) {
			if (weightCount[slot] > 0 && offset >= weightBase[slot]
					&& offset < weightBase[slot] + weightCount[slot]) {
				return slot;
			}
		}
		return -1;
	}

	public int weightBase(int slot) {
		return slot < 0 || slot >= nodeCount ? -1 : weightBase[slot];
	}

	public int weightCount(int slot) {
		return slot < 0 || slot >= nodeCount ? 0 : weightCount[slot];
	}
}
