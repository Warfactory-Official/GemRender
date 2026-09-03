package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator;

class NodeTableTest {
	private final GltfPaletteLayout layout = RigFixture.layout();
	private final NodeTable table = RigFixture.layout()
			.nodeTable();

	@Test
	@DisplayName("a rest local transform matches the one jgltf computes for the same node")
	void restLocalTransformsMatchJgltf() {
		float[] state = table.newScratch();
		Matrix4f actual = new Matrix4f();
		float[] jgltf = new float[16];

		for (int slot = 0; slot < table.nodeCount(); slot++) {
			NodeModel node = layout.nodes()
					.get(slot);

			node.computeLocalTransform(jgltf);
			table.localTransform(state, slot, actual);

			Matrix4f expected = new Matrix4f().set(jgltf);
			assertThat(MatrixScalar.maxDifference(expected, actual))
					.as("slot %d ('%s') local transform", slot, node.getName())
					.isLessThan(1e-6f);
		}
	}

	@Test
	@DisplayName("the fixture has nodes with real transforms, not a table of identities")
	void theFixtureIsNotAllIdentity() {
		float[] state = table.newScratch();
		Matrix4f local = new Matrix4f();
		Matrix4f identity = new Matrix4f();

		int nonIdentity = 0;
		for (int slot = 0; slot < table.nodeCount(); slot++) {
			table.localTransform(state, slot, local);
			if (MatrixScalar.maxDifference(identity, local) > 1e-6f) {
				nonIdentity++;
			}
		}

		assertThat(nonIdentity).as("nodes whose rest transform is not the identity")
				.isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("an absent component reads as the glTF default, not as zero")
	void absentComponentsDefaultCorrectly() {
		float[] state = table.newScratch();

		for (int slot = 0; slot < table.nodeCount(); slot++) {
			NodeModel node = layout.nodes()
					.get(slot);
			int base = slot * NodeTable.TRS_STRIDE;

			if (node.getRotation() == null) {
				assertThat(state[base + NodeTable.ROTATION + 3]).as("slot %d default rotation w", slot)
						.isEqualTo(1.0f);
			}
			if (node.getScale() == null) {
				assertThat(state[base + NodeTable.SCALE]).as("slot %d default scale x", slot)
						.isEqualTo(1.0f);
			}
		}
	}

	@Test
	@DisplayName("evaluating a clip does not write into the glTF node graph")
	void evaluationLeavesTheNodeGraphAlone() {
		List<float[]> before = snapshotNodes();

		GltfPose.evaluate(layout, RigFixture.animation(), RigFixture.MID_CLIP, RigFixture.newPalette());

		List<float[]> after = snapshotNodes();
		for (int i = 0; i < before.size(); i++) {
			assertThat(after.get(i)).as("node array %d after evaluating a clip", i)
					.isEqualTo(before.get(i));
		}
	}

	@Test
	@DisplayName("the clip really does move the rig, so the graph could have been written")
	void theClipActuallyPoses() {
		Matrix4f[] rest = RigFixture.pose(null, 0.0f);
		Matrix4f[] posed = RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP);

		float moved = 0.0f;
		for (int slot = 0; slot < rest.length; slot++) {
			moved = Math.max(moved, MatrixScalar.maxDifference(rest[slot], posed[slot]));
		}

		assertThat(moved).as("largest matrix change between rest and mid-clip")
				.isGreaterThan(0.1f);
	}

	@Test
	@DisplayName("concurrent evaluations of one model agree with serial ones")
	void concurrentEvaluationMatchesSerial() throws Exception {
		GltfAnimation clip = RigFixture.animation();
		float[] times = new float[16];
		for (int i = 0; i < times.length; i++) {
			times[i] = i * (RigFixture.CLIP_SECONDS / times.length);
		}

		Matrix4f[][] serial = new Matrix4f[times.length][];
		for (int i = 0; i < times.length; i++) {
			serial[i] = RigFixture.pose(clip, times[i]);
		}

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 32; round++) {
				List<Callable<Matrix4f[]>> work = new ArrayList<>();
				for (float time : times) {
					work.add(() -> RigFixture.pose(clip, time));
				}

				List<Future<Matrix4f[]>> results = pool.invokeAll(work);
				for (int i = 0; i < times.length; i++) {
					Matrix4f[] concurrent = results.get(i)
							.get();
					for (int slot = 0; slot < concurrent.length; slot++) {
						assertThat(MatrixScalar.maxDifference(serial[i][slot], concurrent[slot]))
								.as("round %d, time %.3f, slot %d", round, times[i], slot)
								.isLessThan(1e-6f);
					}
				}
			}
		} finally {
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	@DisplayName("a morphed node gets a weight row sized by its targets")
	void morphedNodesGetWeightRows() {
		NodeTable morph = MorphFixture.layout()
				.nodeTable();

		int pump = morph.slotOf(MorphFixture.node(MorphFixture.NODE_PUMP));
		int piston = morph.slotOf(MorphFixture.node(MorphFixture.NODE_PISTON));

		assertThat(morph.weightCount(pump)).as("the bellows' weight row")
				.isEqualTo(MorphFixture.BELLOWS_TARGETS);
		assertThat(morph.weightCount(piston)).as("the piston's weight row")
				.isEqualTo(MorphFixture.PISTON_TARGETS);

		assertThat(morph.weightBase(pump)).isNotEqualTo(morph.weightBase(piston));
		assertThat(morph.weightBase(piston))
				.isGreaterThanOrEqualTo(morph.weightBase(pump) + MorphFixture.BELLOWS_TARGETS);
	}

	@Test
	@DisplayName("a node with no morph targets has no weight row")
	void unmorphedNodesHaveNoWeightRow() {
		for (int slot = 0; slot < table.nodeCount(); slot++) {
			assertThat(table.weightBase(slot)).as("rig slot %d has no morph weights", slot)
					.isEqualTo(-1);
		}
		assertThat(table.scratchFloats()).as("the rig's scratch is exactly its nodes")
				.isEqualTo(RigFixture.NODE_COUNT * NodeTable.TRS_STRIDE);
	}

	@Test
	@DisplayName("the first root is found, and is not assumed to be slot 0")
	void firstRootIsFound() {
		int root = table.firstRootSlot();

		assertThat(root).as("a root exists")
				.isNotNegative();
		assertThat(layout.parentSlots()[root]).as("slot %d has no parent", root)
				.isEqualTo(-1);

		for (int slot = 0; slot < root; slot++) {
			assertThat(layout.parentSlots()[slot]).as("slot %d before the first root", slot)
					.isNotEqualTo(-1);
		}
	}

	@Test
	@DisplayName("a driver's offset addresses the property it names")
	void offsetsAddressTheRightProperty() {
		int slot = RigFixture.NODE_BONE0;
		int base = slot * NodeTable.TRS_STRIDE;

		assertThat(table.offsetFor(slot, GltfAnimationCreator.TRANSLATION_PATH))
				.isEqualTo(base + NodeTable.TRANSLATION);
		assertThat(table.offsetFor(slot, GltfAnimationCreator.ROTATION_PATH))
				.isEqualTo(base + NodeTable.ROTATION);
		assertThat(table.offsetFor(slot, GltfAnimationCreator.SCALE_PATH))
				.isEqualTo(base + NodeTable.SCALE);

		assertThat(table.componentsFor(slot, GltfAnimationCreator.TRANSLATION_PATH)).isEqualTo(3);
		assertThat(table.componentsFor(slot, GltfAnimationCreator.ROTATION_PATH)).isEqualTo(4);
		assertThat(table.componentsFor(slot, GltfAnimationCreator.SCALE_PATH)).isEqualTo(3);

		assertThat(table.offsetFor(slot, GltfAnimationCreator.WEIGHTS_PATH)).isEqualTo(-1);
	}

	@Test
	@DisplayName("resetting a dirtied scratch restores the rest state exactly")
	void resetRestoresRest() {
		float[] state = table.newScratch();
		float[] rest = state.clone();

		java.util.Arrays.fill(state, 99.0f);
		table.resetToRest(state);

		for (int i = 0; i < rest.length; i++) {
			assertThat(state[i]).as("scratch float %d", i)
					.isCloseTo(rest[i], within(0.0f));
		}
	}

	private List<float[]> snapshotNodes() {
		List<float[]> out = new ArrayList<>();
		for (NodeModel node : layout.nodes()) {
			out.add(orMarker(node.getMatrix()));
			out.add(orMarker(node.getTranslation()));
			out.add(orMarker(node.getRotation()));
			out.add(orMarker(node.getScale()));
			out.add(orMarker(node.getWeights()));
		}
		return out;
	}

	private static float[] orMarker(float[] values) {
		return values == null ? new float[] { Float.NEGATIVE_INFINITY } : values.clone();
	}
}
