package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.vendor.jgltf.model.NodeModel;

class GltfPaletteLayoutOrderTest {
	private final GltfPaletteLayout layout = RigFixture.layout();

	@Test
	@DisplayName("every slot appears in the evaluation order exactly once")
	void orderIsAPermutationOfTheNodes() {
		int[] order = layout.evaluationOrder();

		assertThat(order).hasSize(layout.nodes()
				.size());

		int[] sorted = order.clone();
		Arrays.sort(sorted);
		for (int i = 0; i < sorted.length; i++) {
			assertThat(sorted[i]).as("slot %d present", i)
					.isEqualTo(i);
		}
	}

	@Test
	@DisplayName("every parent is evaluated before its children")
	void parentsComeFirst() {
		int[] order = layout.evaluationOrder();
		int[] parents = layout.parentSlots();

		int[] position = new int[order.length];
		for (int k = 0; k < order.length; k++) {
			position[order[k]] = k;
		}

		for (int slot = 0; slot < parents.length; slot++) {
			int parent = parents[slot];
			if (parent >= 0) {
				assertThat(position[parent]).as("parent %d of slot %d is evaluated first", parent, slot)
						.isLessThan(position[slot]);
			}
		}
	}

	@Test
	@DisplayName("parent slots agree with the node graph")
	void parentSlotsMatchTheGraph() {
		int[] parents = layout.parentSlots();

		for (int slot = 0; slot < parents.length; slot++) {
			NodeModel parent = layout.nodes()
					.get(slot)
					.getParent();
			if (parent == null) {
				assertThat(parents[slot]).as("slot %d is a root", slot)
						.isEqualTo(-1);
			} else {
				assertThat(parents[slot]).as("slot %d's parent", slot)
						.isEqualTo(layout.nodeSlot(parent));
			}
		}
	}

	@Test
	@DisplayName("the rig actually has a hierarchy to get wrong")
	void theFixtureIsNotFlat() {

		int[] parents = layout.parentSlots();

		int deepest = 0;
		for (int slot = 0; slot < parents.length; slot++) {
			int depth = 0;
			for (int node = parents[slot]; node >= 0; node = parents[node]) {
				depth++;
			}
			deepest = Math.max(deepest, depth);
		}

		assertThat(deepest).as("depth of the rig's node graph")
				.isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("a skin's joint slots point at the nodes the skin names")
	void jointSlotsResolveToTheSkinsJoints() {
		GltfPaletteLayout.SkinBlock block = layout.skins()
				.get(0);

		assertThat(block.jointSlots()).hasSize(block.jointCount());

		for (int j = 0; j < block.jointCount(); j++) {
			NodeModel joint = block.skin()
					.getJoints()
					.get(j);
			assertThat(block.jointSlots()[j]).as("joint %d", j)
					.isEqualTo(layout.nodeSlot(joint));
		}
	}
}
