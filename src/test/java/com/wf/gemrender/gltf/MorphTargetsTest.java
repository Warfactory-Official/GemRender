package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.morph.MorphTargets;

class MorphTargetsTest {

	@Test
	@DisplayName("both sets parse, with their own target counts and strides")
	void theTwoSetsDifferInEveryWayThatMatters() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		MorphTargets piston = MorphFixture.targets(MorphFixture.NODE_PISTON);

		assertThat(bellows.targetCount()).isEqualTo(MorphFixture.BELLOWS_TARGETS);
		assertThat(bellows.vertexCount()).isEqualTo(MorphFixture.BELLOWS_VERTICES);
		assertThat(bellows.hasNormals()).isTrue();
		assertThat(bellows.floatsPerDelta()).isEqualTo(MorphTargets.FLOATS_WITH_NORMALS);

		assertThat(piston.targetCount()).isEqualTo(MorphFixture.PISTON_TARGETS);
		assertThat(piston.vertexCount()).isEqualTo(MorphFixture.PISTON_VERTICES);
		assertThat(piston.hasNormals())
				.as("the piston's target has no NORMAL, which is the case a uniform stride would break")
				.isFalse();
		assertThat(piston.floatsPerDelta()).isEqualTo(MorphTargets.FLOATS_POSITION_ONLY);

		assertThat(bellows.floatCount())
				.isEqualTo(bellows.vertexCount() * bellows.targetCount() * bellows.floatsPerDelta());
		assertThat(piston.floatCount())
				.isEqualTo(piston.vertexCount() * piston.targetCount() * piston.floatsPerDelta());
	}

	@Test
	@DisplayName("a primitive with no targets yields no morph set at all")
	void aPrimitiveWithoutTargetsIsNull() {
		assertThat(MorphTargets.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
				RigFixture.vertexCount(RigFixture.NODE_ARMATURE))).isNull();
	}

	@Test
	@DisplayName("the deltas are the ones the generator wrote")
	void deltasMatchTheGenerator() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		for (int v = 0; v < bellows.vertexCount(); v++) {
			float y = base[v * 3 + 1];

			assertThat(bellows.delta(v, 0, 0)).isEqualTo(0.0f);
			assertThat(bellows.delta(v, 0, 1)).isCloseTo(-MorphFixture.SQUASH * y, within(1e-5f));
			assertThat(bellows.delta(v, 0, 2)).isEqualTo(0.0f);

			float expected = MorphFixture.BULGE
					* (float) Math.sin(y / MorphFixture.BELLOWS_HEIGHT * Math.PI);
			float actual = (float) Math.hypot(bellows.delta(v, 1, 0), bellows.delta(v, 1, 2));
			assertThat(actual).isCloseTo(expected, within(1e-5f));
			assertThat(bellows.delta(v, 1, 1)).isEqualTo(0.0f);
		}
	}

	@Test
	@DisplayName("a zero normal delta is stored, not skipped")
	void zeroNormalDeltasStillOccupyTheirSlot() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);

		for (int v = 0; v < bellows.vertexCount(); v++) {
			for (int c = 3; c < 6; c++) {
				assertThat(bellows.delta(v, 0, c)).isEqualTo(0.0f);
			}
		}

		int v = MorphFixture.BELLOWS_VERTICES / 2;
		assertThat(bellows.deltas()[(v * bellows.targetCount() + 1) * bellows.floatsPerDelta()])
				.isEqualTo(bellows.delta(v, 1, 0));
	}

	@Test
	@DisplayName("full weight on squash halves the bellows")
	void squashAtFullWeight() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		float tallest = 0.0f;
		for (int v = 0; v < bellows.vertexCount(); v++) {
			float[] out = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
			bellows.applyPosition(new float[] { 1.0f, 0.0f }, v, out);

			assertThat(out[1]).isCloseTo(base[v * 3 + 1] * (1.0f - MorphFixture.SQUASH), within(1e-5f));
			assertThat(out[0]).isCloseTo(base[v * 3], within(1e-5f));
			tallest = Math.max(tallest, out[1]);
		}

		assertThat(tallest)
				.as("the squashed tube's height")
				.isCloseTo(MorphFixture.BELLOWS_HEIGHT * (1.0f - MorphFixture.SQUASH), within(1e-5f));
	}

	@Test
	@DisplayName("two weights blend, they do not pick one")
	void targetsAccumulate() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		int v = middleRingVertex(bellows, base);
		float[] weights = MorphFixture.BELLOWS_WEIGHTS_AT_MID;

		float[] both = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
		bellows.applyPosition(weights, v, both);

		float[] first = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
		bellows.applyPosition(new float[] { weights[0], 0.0f }, v, first);

		float[] second = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
		bellows.applyPosition(new float[] { 0.0f, weights[1] }, v, second);

		assertThat(both[1]).isNotEqualTo(base[v * 3 + 1]);
		assertThat(Math.hypot(both[0], both[2])).isNotEqualTo(Math.hypot(base[v * 3], base[v * 3 + 2]));
		for (int c = 0; c < 3; c++) {
			assertThat(both[c])
					.isCloseTo(base[v * 3 + c] + (first[c] - base[v * 3 + c]) + (second[c] - base[v * 3 + c]),
							within(1e-5f));
		}
	}

	@Test
	@DisplayName("a weight of zero displaces nothing")
	void zeroWeightsAreTheBaseMesh() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		for (int v = 0; v < bellows.vertexCount(); v++) {
			float[] out = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
			bellows.applyPosition(new float[] { 0.0f, 0.0f }, v, out);
			for (int c = 0; c < 3; c++) {
				assertThat(out[c]).isEqualTo(base[v * 3 + c]);
			}
		}
	}

	@Test
	@DisplayName("the clip drives both sets' weights, each with its own count")
	void samplingDrivesTheWeights() {
		GltfMorphLayout layout = MorphFixture.morphLayout();
		GltfAnimation clip = MorphFixture.animation();

		float[] block = new float[layout.blockFloats()];
		Matrix4f[] palette = new Matrix4f[MorphFixture.layout()
				.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}

		GltfPose.evaluate(MorphFixture.layout(), clip, MorphFixture.MID_CLIP, palette, layout, block);

		float[] bellows = weightsOf(layout, block, 0);
		assertThat(bellows[0]).isCloseTo(MorphFixture.BELLOWS_WEIGHTS_AT_MID[0], within(1e-5f));
		assertThat(bellows[1]).isCloseTo(MorphFixture.BELLOWS_WEIGHTS_AT_MID[1], within(1e-5f));

		float[] piston = weightsOf(layout, block, 1);
		assertThat(piston[0]).isCloseTo(MorphFixture.PISTON_WEIGHTS_AT_MID[0], within(1e-5f));
	}

	private static float[] weightsOf(GltfMorphLayout layout, float[] block, int set) {
		int header = set * GltfMorphLayout.HEADER_FLOATS;
		int count = (int) block[header + 1];
		int offset = (int) block[header + 2];
		return java.util.Arrays.copyOfRange(block, offset, offset + count);
	}

	@Test
	@DisplayName("a rest evaluation after a posed one gives the declared weights, not the posed ones")
	void restEvaluationIsNotContaminatedByAPreviousOne() {
		GltfMorphLayout layout = MorphFixture.morphLayout();
		Matrix4f[] palette = new Matrix4f[MorphFixture.layout()
				.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}

		GltfPose.evaluate(MorphFixture.layout(), MorphFixture.animation(), MorphFixture.MID_CLIP,
				palette, layout, new float[layout.blockFloats()]);

		float[] block = new float[layout.blockFloats()];
		GltfPose.evaluate(MorphFixture.layout(), null, 0.0f, palette, layout, block);

		float[] weights = weightsOf(layout, block, 0);
		assertThat(weights[0])
				.as("the bellows' first weight in a rest evaluation that followed a posed one")
				.isEqualTo(0.0f);
		assertThat(weights[1]).isEqualTo(0.0f);
	}

	@Test
	@DisplayName("the worst-case displacement covers every reachable weight combination")
	void maxDisplacementIsConservative() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		float[][] corners = { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { 0.5f, 0.5f } };

		for (int v = 0; v < bellows.vertexCount(); v++) {
			float bound = bellows.maxDisplacement(v);
			for (float[] weights : corners) {
				float[] out = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
				bellows.applyPosition(weights, v, out);

				double moved = Math.sqrt(Math.pow(out[0] - base[v * 3], 2)
						+ Math.pow(out[1] - base[v * 3 + 1], 2)
						+ Math.pow(out[2] - base[v * 3 + 2], 2));
				assertThat(moved)
						.as("vertex %d at weights %s", v, java.util.Arrays.toString(weights))
						.isLessThanOrEqualTo(bound + 1e-4);
			}
		}
	}

	@Test
	@DisplayName("an unmorphed vertex has a displacement bound of zero, so it costs nothing")
	void unmorphedVerticesDoNotInflateTheBound() {
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		float[] base = MorphFixture.positions(MorphFixture.NODE_PUMP);

		for (int v = 0; v < 9; v++) {
			assertThat(base[v * 3 + 1]).isEqualTo(0.0f);
			assertThat(bellows.maxDisplacement(v))
					.as("vertex %d, on the fixed bottom ring", v)
					.isCloseTo(0.0f, within(1e-6f));
		}
	}

	private static int middleRingVertex(MorphTargets targets, float[] base) {
		int best = 0;
		for (int v = 1; v < targets.vertexCount(); v++) {
			if (targets.maxDisplacement(v) > targets.maxDisplacement(best)) {
				best = v;
			}
		}
		return best;
	}
}
