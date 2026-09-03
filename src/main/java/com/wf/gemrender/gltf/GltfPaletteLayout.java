package com.wf.gemrender.gltf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.SkinModel;

public final class GltfPaletteLayout {
	public record SkinBlock(SkinModel skin, int base, Matrix4f[] inverseBind, int[] jointSlots) {
		public int jointCount() {
			return inverseBind.length;
		}
	}

	private final List<NodeModel> nodes;
	private final Map<NodeModel, Integer> nodeSlots;
	private final List<SkinBlock> skins;
	private final Map<SkinModel, SkinBlock> skinBlocks;
	private final int size;

	private final NodeTable nodeTable;

	private GltfPaletteLayout(List<NodeModel> nodes, Map<NodeModel, Integer> nodeSlots,
			List<SkinBlock> skins, Map<SkinModel, SkinBlock> skinBlocks, int size, NodeTable nodeTable) {
		this.nodes = nodes;
		this.nodeSlots = nodeSlots;
		this.skins = List.copyOf(skins);
		this.skinBlocks = skinBlocks;
		this.size = size;
		this.nodeTable = nodeTable;
	}

	public static GltfPaletteLayout of(GltfModel gltf) {
		List<NodeModel> nodes = List.copyOf(gltf.getNodeModels());

		Map<NodeModel, Integer> nodeSlots = new IdentityHashMap<>();
		for (int i = 0; i < nodes.size(); i++) {
			nodeSlots.put(nodes.get(i), i);
		}

		List<SkinBlock> skins = new ArrayList<>();
		Map<SkinModel, SkinBlock> skinBlocks = new IdentityHashMap<>();
		int next = nodes.size();

		for (NodeModel node : nodes) {
			SkinModel skin = node.getSkinModel();
			if (skin == null || skinBlocks.containsKey(skin)) {
				continue;
			}

			List<NodeModel> joints = skin.getJoints();
			Matrix4f[] inverseBind = new Matrix4f[joints.size()];
			int[] jointSlots = new int[joints.size()];
			float[] scratch = new float[16];
			for (int j = 0; j < joints.size(); j++) {
				skin.getInverseBindMatrix(j, scratch);
				inverseBind[j] = new Matrix4f().set(scratch);

				Integer jointSlot = nodeSlots.get(joints.get(j));
				if (jointSlot == null) {
					throw new IllegalArgumentException("skin " + skin.getName() + " joint " + j
							+ " is not one of the model's nodes");
				}
				jointSlots[j] = jointSlot;
			}

			SkinBlock block = new SkinBlock(skin, next, inverseBind, jointSlots);
			skins.add(block);
			skinBlocks.put(skin, block);
			next += joints.size();
		}

		if (next > BoneAttributeCodec.MAX_JOINTS) {
			throw new IllegalArgumentException("glTF needs " + next + " palette slots ("
					+ nodes.size() + " nodes + " + (next - nodes.size()) + " skin joints) but the vertex "
					+ "encoding holds at most " + BoneAttributeCodec.MAX_JOINTS
					+ ". Split the model, or bake unanimated nodes into their parents.");
		}

		return new GltfPaletteLayout(nodes, nodeSlots, skins, Collections.unmodifiableMap(skinBlocks), next,
				NodeTable.of(nodes));
	}

	public static GltfPaletteLayout ofNodes(NodeTable table) {
		if (table.nodeCount() > BoneAttributeCodec.MAX_JOINTS) {
			throw new IllegalArgumentException("model needs " + table.nodeCount() + " palette slots but the "
					+ "vertex encoding holds at most " + BoneAttributeCodec.MAX_JOINTS
					+ ". Split the model, or merge bones that never move apart.");
		}
		return new GltfPaletteLayout(List.of(), Map.of(), List.of(), Map.of(), table.nodeCount(), table);
	}

	public int[] evaluationOrder() {
		return nodeTable.evaluationOrder();
	}

	public int[] parentSlots() {
		return nodeTable.parentSlots();
	}

	public NodeTable nodeTable() {
		return nodeTable;
	}

	public int size() {
		return size;
	}

	public List<NodeModel> nodes() {
		return nodes;
	}

	public List<SkinBlock> skins() {
		return skins;
	}

	public int nodeSlot(NodeModel node) {
		Integer slot = nodeSlots.get(node);
		if (slot == null) {
			throw new IllegalArgumentException("node is not part of this model: " + node.getName());
		}
		return slot;
	}

	public int[] jointSlots(SkinModel skin) {
		SkinBlock block = skinBlocks.get(skin);
		if (block == null) {
			throw new IllegalArgumentException("skin is not part of this model: " + skin.getName());
		}

		int[] slots = new int[block.jointCount()];
		for (int j = 0; j < slots.length; j++) {
			slots[j] = block.base() + j;
		}
		return slots;
	}
}
