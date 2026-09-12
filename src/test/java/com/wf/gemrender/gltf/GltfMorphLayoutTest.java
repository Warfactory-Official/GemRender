package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.morph.MorphTargets;

class GltfMorphLayoutTest {

	@Test
	@DisplayName("each set's header holds its own base, count, weight offset and stride")
	void headersDescribeEachSetSeparately() {
		GltfMorphLayout layout = MorphFixture.morphLayout();
		float[] block = block(layout, MorphFixture.MID_CLIP);

		int stride = GltfMorphLayout.HEADER_FLOATS;
		int bellowsFloats = MorphFixture.targets(MorphFixture.NODE_PUMP)
				.floatCount();

		assertThat(block[0]).as("bellows dataBase").isEqualTo(0.0f);
		assertThat(block[1]).as("bellows targetCount").isEqualTo(2.0f);
		assertThat(block[3]).as("bellows floatsPerDelta").isEqualTo(6.0f);

		assertThat(block[stride]).as("piston dataBase").isEqualTo((float) bellowsFloats);
		assertThat(block[stride + 1]).as("piston targetCount").isEqualTo(1.0f);
		assertThat(block[stride + 3])
				.as("piston floatsPerDelta, the value a uniform stride would get wrong")
				.isEqualTo(3.0f);
	}

	@Test
	@DisplayName("weight offsets are relative to the block, and point at the right weights")
	void weightOffsetsAreRelativeAndCorrect() {
		GltfMorphLayout layout = MorphFixture.morphLayout();
		float[] block = block(layout, MorphFixture.MID_CLIP);

		int stride = GltfMorphLayout.HEADER_FLOATS;
		int bellowsWeights = (int) block[2];
		int pistonWeights = (int) block[stride + 2];

		assertThat(bellowsWeights).isEqualTo(2 * stride);
		assertThat(pistonWeights).isEqualTo(2 * stride + MorphFixture.BELLOWS_TARGETS);

		assertThat(block[bellowsWeights])
				.isCloseTo(MorphFixture.BELLOWS_WEIGHTS_AT_MID[0], within(1e-5f));
		assertThat(block[bellowsWeights + 1])
				.isCloseTo(MorphFixture.BELLOWS_WEIGHTS_AT_MID[1], within(1e-5f));
		assertThat(block[pistonWeights])
				.isCloseTo(MorphFixture.PISTON_WEIGHTS_AT_MID[0], within(1e-5f));
	}

	@Test
	@DisplayName("the block is exactly as long as it says it is")
	void blockFloatsCoversHeadersAndWeights() {
		GltfMorphLayout layout = MorphFixture.morphLayout();

		assertThat(layout.blockFloats())
				.isEqualTo(2 * GltfMorphLayout.HEADER_FLOATS
						+ MorphFixture.BELLOWS_TARGETS + MorphFixture.PISTON_TARGETS);
		assertThat(layout.maxTargets()).isEqualTo(MorphFixture.BELLOWS_TARGETS);

		float[] block = block(layout, MorphFixture.MID_CLIP);
		assertThat(block[block.length - 1])
				.isCloseTo(MorphFixture.PISTON_WEIGHTS_AT_MID[0], within(1e-5f));
	}

	@Test
	@DisplayName("set ids are 1-based, because zero means a vertex does not morph")
	void setIdsAreOneBased() {
		GltfMorphLayout layout = MorphFixture.morphLayout();

		assertThat(layout.sets()
				.get(0)
				.id()).isEqualTo(1);
		assertThat(layout.sets()
				.get(1)
				.id()).isEqualTo(2);
		assertThat(GltfMorphLayout.NONE.isEmpty()).isTrue();
		assertThat(GltfMorphLayout.NONE.blockFloats()).isZero();
	}

	@Test
	@DisplayName("a set whose weights the clip does not fill still gets a full row")
	void missingWeightsAreZeroedRatherThanLeftOver() {
		GltfMorphLayout layout = MorphFixture.morphLayout();

		float[] dirty = new float[layout.blockFloats()];
		java.util.Arrays.fill(dirty, 99.0f);

		Matrix4f[] palette = palette();
		GltfPose.evaluate(MorphFixture.layout(), null, 0.0f, palette, layout, dirty);

		for (int i = 2 * GltfMorphLayout.HEADER_FLOATS; i < dirty.length; i++) {
			assertThat(dirty[i])
					.as("weight float %d after a rest-pose evaluation", i)
					.isEqualTo(0.0f);
		}
	}

	@Test
	@DisplayName("a merged part's header points where the MERGED vertex ids will look")
	void rebaseCancelsTheMergedVertexBase() {
		MorphTargets piston = MorphFixture.targets(MorphFixture.NODE_PISTON);
		int bellowsFloats = MorphFixture.targets(MorphFixture.NODE_PUMP)
				.floatCount();
		int vertexBase = MorphFixture.vertexCount(MorphFixture.NODE_PUMP);

		float[] deltas = new float[bellowsFloats + piston.floatCount()];
		System.arraycopy(MorphFixture.targets(MorphFixture.NODE_PUMP)
				.deltas(), 0, deltas, 0, bellowsFloats);
		System.arraycopy(piston.deltas(), 0, deltas, bellowsFloats, piston.floatCount());

		float[] block = block(MorphFixture.mergedMorphLayout(), MorphFixture.MID_CLIP);
		int header = GltfMorphLayout.HEADER_FLOATS;

		int dataBase = (int) block[header];
		int targetCount = (int) block[header + 1];
		int floatsPerDelta = (int) block[header + 3];

		for (int v = 0; v < piston.vertexCount(); v++) {

			int offset = dataBase + (vertexBase + v) * targetCount * floatsPerDelta;

			for (int t = 0; t < targetCount; t++) {
				for (int c = 0; c < 3; c++) {
					assertThat(deltas[offset + t * floatsPerDelta + c])
							.as("piston vertex %d, target %d, component %d", v, t, c)
							.isEqualTo(piston.delta(v, t, c));
				}
			}
		}
	}

	@Test
	@DisplayName("without the rebase the same arithmetic reads off the end of the buffer")
	void theUnrebasedAddressIsOutOfRange() {
		MorphTargets piston = MorphFixture.targets(MorphFixture.NODE_PISTON);
		int bellowsFloats = MorphFixture.targets(MorphFixture.NODE_PUMP)
				.floatCount();
		int vertexBase = MorphFixture.vertexCount(MorphFixture.NODE_PUMP);
		int stride = piston.targetCount() * piston.floatsPerDelta();

		float[] block = block(MorphFixture.morphLayout(), MorphFixture.MID_CLIP);
		int dataBase = (int) block[GltfMorphLayout.HEADER_FLOATS];

		assertThat(dataBase + vertexBase * stride)
				.as("the first merged piston vertex, addressed without a rebase")
				.isGreaterThan(bellowsFloats + piston.floatCount());
	}

	private static float[] block(GltfMorphLayout layout, float time) {
		float[] block = new float[layout.blockFloats()];
		GltfPose.evaluate(MorphFixture.layout(), MorphFixture.animation(), time, palette(), layout, block);
		return block;
	}

	private static Matrix4f[] palette() {
		Matrix4f[] palette = new Matrix4f[MorphFixture.layout()
				.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		return palette;
	}
}
