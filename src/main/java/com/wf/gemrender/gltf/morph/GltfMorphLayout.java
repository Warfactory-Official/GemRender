package com.wf.gemrender.gltf.morph;

import java.util.ArrayList;
import java.util.List;

import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.vendor.jgltf.model.MeshModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;

public final class GltfMorphLayout {
	public static final int HEADER_FLOATS = 4;

	public record MorphSet(int id, NodeModel node, MeshModel mesh, MorphTargets targets, int dataBase,
			int weightBase) {
		public void weights(float[] poseState, float[] out, int offset) {
			int count = targets.targetCount();
			if (weightBase < 0) {
				java.util.Arrays.fill(out, offset, offset + count, 0.0f);
				return;
			}
			System.arraycopy(poseState, weightBase, out, offset, count);
		}
	}

	private final List<MorphSet> sets;
	private final int blockFloats;
	private final int maxTargets;

	private GltfMorphLayout(List<MorphSet> sets, int blockFloats, int maxTargets) {
		this.sets = sets;
		this.blockFloats = blockFloats;
		this.maxTargets = maxTargets;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final GltfMorphLayout NONE = new GltfMorphLayout(List.of(), 0, 0);

	public List<MorphSet> sets() {
		return sets;
	}

	public boolean isEmpty() {
		return sets.isEmpty();
	}

	public int blockFloats() {
		return blockFloats;
	}

	public int maxTargets() {
		return maxTargets;
	}

	public void writeBlock(float[] out, float[] poseState) {
		int weightCursor = sets.size() * HEADER_FLOATS;

		for (int s = 0; s < sets.size(); s++) {
			MorphSet set = sets.get(s);
			int header = s * HEADER_FLOATS;
			int count = set.targets()
					.targetCount();

			out[header] = set.dataBase();
			out[header + 1] = count;
			out[header + 2] = weightCursor;
			out[header + 3] = set.targets()
					.floatsPerDelta();

			set.weights(poseState, out, weightCursor);
			weightCursor += count;
		}
	}

	public static final class Builder {
		private final List<MorphSet> sets = new ArrayList<>();
		private int maxTargets;

		public int add(NodeTable table, NodeModel node, MeshModel mesh, MorphTargets targets, int dataBase) {
			int slot = table.slotOf(node);
			int weightBase = slot < 0 ? -1 : table.weightBase(slot);

			if (weightBase >= 0 && table.weightCount(slot) < targets.targetCount()) {
				throw new IllegalStateException("node '" + node.getName() + "' has room for "
						+ table.weightCount(slot) + " morph weights but a primitive declares "
						+ targets.targetCount() + " targets");
			}

			int id = sets.size() + 1;
			sets.add(new MorphSet(id, node, mesh, targets, dataBase, weightBase));
			maxTargets = Math.max(maxTargets, targets.targetCount());
			return id;
		}

		public GltfMorphLayout build() {
			if (sets.isEmpty()) {
				return NONE;
			}

			int blockFloats = sets.size() * HEADER_FLOATS;
			for (MorphSet set : sets) {
				blockFloats += set.targets()
						.targetCount();
			}
			return new GltfMorphLayout(List.copyOf(sets), blockFloats, maxTargets);
		}
	}
}
