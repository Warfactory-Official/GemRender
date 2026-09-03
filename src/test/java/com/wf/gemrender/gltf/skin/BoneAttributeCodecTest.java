package com.wf.gemrender.gltf.skin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoneAttributeCodecTest {

	@Test
	@DisplayName("joint indices round-trip through the packed overlay integer")
	void jointsRoundTrip() {
		int packed = BoneAttributeCodec.packJoints(0, 1, 127, 255);

		assertThat(BoneAttributeCodec.unpackJoint(packed, 0)).isEqualTo(0);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 1)).isEqualTo(1);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 2)).isEqualTo(127);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 3)).isEqualTo(255);
	}

	@Test
	@DisplayName("every joint index in every influence slot round-trips")
	void jointsRoundTripExhaustively() {
		for (int joint = 0; joint < BoneAttributeCodec.MAX_JOINTS; joint++) {
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				int[] j = new int[BoneAttributeCodec.INFLUENCES];
				j[influence] = joint;
				int packed = BoneAttributeCodec.packJoints(j[0], j[1], j[2], j[3]);

				assertThat(BoneAttributeCodec.unpackJoint(packed, influence))
						.as("joint %d in influence %d", joint, influence)
						.isEqualTo(joint);
			}
		}
	}

	@Test
	@DisplayName("the high joint slot survives the packed value going negative")
	void highJointsSurviveSignedPacking() {
		int packed = BoneAttributeCodec.packJoints(0, 0, 0, 255);

		assertThat(packed).isNegative();
		assertThat(BoneAttributeCodec.unpackJoint(packed, 3)).isEqualTo(255);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 256, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE })
	@DisplayName("out-of-range joint indices are rejected rather than silently truncated")
	void rejectsOutOfRangeJoints(int joint) {
		assertThatThrownBy(() -> BoneAttributeCodec.packJoints(joint, 0, 0, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("out of range");
	}

	@Test
	@DisplayName("quantised weights sum to exactly 255")
	void weightsSumToExactly255() {
		float[][] cases = {
				{ 1.0f, 0.0f, 0.0f, 0.0f },
				{ 0.5f, 0.5f, 0.0f, 0.0f },
				{ 0.25f, 0.25f, 0.25f, 0.25f },
				{ 1 / 3f, 1 / 3f, 1 / 3f, 0.0f },
				{ 1 / 7f, 2 / 7f, 3 / 7f, 1 / 7f },
				{ 0.1f, 0.2f, 0.3f, 0.4f },
				{ 2.0f, 2.0f, 2.0f, 2.0f },
				{ 0.9f, 0.05f, 0.03f, 0.01f },
		};

		int[] out = new int[4];
		for (float[] weights : cases) {
			BoneAttributeCodec.quantizeWeights(weights, 0, out);
			assertThat(out[0] + out[1] + out[2] + out[3])
					.as("sum for %s", java.util.Arrays.toString(weights))
					.isEqualTo(BoneAttributeCodec.WEIGHT_SUM);
		}
	}

	@Test
	@DisplayName("quantised weights stay close to the input proportions")
	void weightsPreserveProportions() {
		int[] out = new int[4];
		BoneAttributeCodec.quantizeWeights(new float[] { 0.1f, 0.2f, 0.3f, 0.4f }, 0, out);

		assertThat(out[0]).isBetween(25, 26);
		assertThat(out[1]).isBetween(50, 51);
		assertThat(out[2]).isBetween(76, 77);
		assertThat(out[3]).isBetween(102, 103);
	}

	@Test
	@DisplayName("a vertex with no usable weights binds rigidly to its first joint")
	void degenerateWeightsBindToFirstJoint() {
		int[] out = new int[4];

		BoneAttributeCodec.quantizeWeights(new float[] { 0f, 0f, 0f, 0f }, 0, out);
		assertThat(out).containsExactly(255, 0, 0, 0);

		BoneAttributeCodec.quantizeWeights(new float[] { -1f, -2f, 0f, 0f }, 0, out);
		assertThat(out).containsExactly(255, 0, 0, 0);

		BoneAttributeCodec.quantizeWeights(new float[] { Float.NaN, Float.NaN, Float.NaN, Float.NaN }, 0, out);
		assertThat(out).containsExactly(255, 0, 0, 0);
	}

	@Test
	@DisplayName("negative and NaN influences are dropped, not propagated")
	void discardsBadInfluencesButKeepsGoodOnes() {
		int[] out = new int[4];
		BoneAttributeCodec.quantizeWeights(new float[] { 0.5f, Float.NaN, -0.25f, 0.5f }, 0, out);

		assertThat(out[1]).isZero();
		assertThat(out[2]).isZero();
		assertThat(out[0] + out[3]).isEqualTo(BoneAttributeCodec.WEIGHT_SUM);
	}

	@Test
	@DisplayName("weights read from an offset into a packed vertex array")
	void readsFromOffset() {
		float[] packed = { 9f, 9f, 9f, 9f, 0.5f, 0.5f, 0f, 0f };
		int[] out = new int[4];

		BoneAttributeCodec.quantizeWeights(packed, 4, out);

		assertThat(out[0] + out[1]).isEqualTo(BoneAttributeCodec.WEIGHT_SUM);
		assertThat(out[2]).isZero();
		assertThat(out[3]).isZero();
	}

	@Test
	@DisplayName("every quantised weight survives Flywheel's truncating byte packer")
	void weightChannelSurvivesTruncation() {
		for (int q = 0; q <= 255; q++) {
			float channel = BoneAttributeCodec.weightChannel(q);

			assertThat(BoneAttributeCodec.packNormU8(channel))
					.as("quantised weight %d round-tripping through packNormU8", q)
					.isEqualTo(q);
		}
	}

	@Test
	@DisplayName("the exact quotient q/255f also round-trips, so the bias is insurance not a bugfix")
	void unbiasedQuotientAlsoRoundTrips() {
		for (int q = 0; q <= 255; q++) {
			assertThat(BoneAttributeCodec.packNormU8(q / 255.0f))
					.as("unbiased q/255f for q=%d", q)
					.isEqualTo(q);
		}
	}

	@Test
	@DisplayName("truncation does bite raw weights, which is why quantisation comes first")
	void rawWeightsFedStraightThroughBreakTheSum() {
		float[] raw = { 0.5f, 0.5f, 0.0f, 0.0f };

		int naiveSum = 0;
		for (float w : raw) {
			naiveSum += BoneAttributeCodec.packNormU8(w);
		}
		assertThat(naiveSum)
				.as("raw weights encoded directly lose a step to truncation")
				.isEqualTo(254)
				.isNotEqualTo(BoneAttributeCodec.WEIGHT_SUM);

		int[] quantised = new int[4];
		BoneAttributeCodec.quantizeWeights(raw, 0, quantised);

		int codecSum = 0;
		for (int q : quantised) {
			codecSum += BoneAttributeCodec.packNormU8(BoneAttributeCodec.weightChannel(q));
		}
		assertThat(codecSum).isEqualTo(BoneAttributeCodec.WEIGHT_SUM);
	}

	@Test
	@DisplayName("weightChannel rejects values outside the quantised range")
	void weightChannelRejectsOutOfRange() {
		assertThatThrownBy(() -> BoneAttributeCodec.weightChannel(256))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BoneAttributeCodec.weightChannel(-1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("a full encode/decode round trip reconstructs weights summing to 1.0")
	void fullRoundTripSumsToOne() {
		int[] quantised = new int[4];
		BoneAttributeCodec.quantizeWeights(new float[] { 0.37f, 0.21f, 0.11f, 0.31f }, 0, quantised);

		float sum = 0f;
		for (int i = 0; i < 4; i++) {
			sum += BoneAttributeCodec.packNormU8(BoneAttributeCodec.weightChannel(quantised[i])) / 255.0f;
		}

		assertThat(sum).isEqualTo(1.0f);
	}

	@Test
	@DisplayName("weightChannel is what to write, decodeWeight is what the shader reads, and they differ")
	void theAuthoringValueIsNotTheWeight() {
		assertThat(BoneAttributeCodec.weightChannel(0)).isGreaterThan(0.0f);
		assertThat(BoneAttributeCodec.decodeWeight(0)).isEqualTo(0.0f);

		int[] quantised = new int[4];
		BoneAttributeCodec.quantizeWeights(new float[] { 0.4f, 0.3f, 0.2f, 0.1f }, 0, quantised);

		float authored = 0.0f;
		float decoded = 0.0f;
		for (int i = 0; i < 4; i++) {
			authored += BoneAttributeCodec.weightChannel(quantised[i]);
			decoded += BoneAttributeCodec.decodeWeight(quantised[i]);
		}
		assertThat(authored).isGreaterThan(1.0f);
		assertThat(decoded).isEqualTo(1.0f);

		for (int q = 0; q <= 255; q++) {
			assertThat(BoneAttributeCodec.decodeWeight(
					BoneAttributeCodec.packNormU8(BoneAttributeCodec.weightChannel(q))))
					.as("round trip for quantised weight %d", q)
					.isEqualTo(BoneAttributeCodec.decodeWeight(q));
		}
	}
}
