package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoseLodTest {
	private static final float NEAR = 32.0f;

	private static PoseLod lod() {
		return new PoseLod(NEAR, 0.0f);
	}

	private static double sq(double blocks) {
		return blocks * blocks;
	}

	@Test
	@DisplayName("inside the near distance nothing is levelled at all")
	void theNearFieldIsUntouched() {
		PoseLod lod = lod();

		assertThat(lod.levelAt(0.0)).isZero();
		assertThat(lod.levelAt(sq(1.0))).isZero();
		assertThat(lod.levelAt(sq(NEAR - 0.001))).isZero();
		assertThat(lod.levelAt(sq(NEAR))).isZero();
	}

	@Test
	@DisplayName("the level goes up by one every time the distance doubles")
	void levelsAreOctaves() {
		PoseLod lod = lod();

		assertThat(lod.levelAt(sq(NEAR * 1.5))).isEqualTo(0);
		assertThat(lod.levelAt(sq(NEAR * 2))).isEqualTo(1);
		assertThat(lod.levelAt(sq(NEAR * 3))).isEqualTo(1);
		assertThat(lod.levelAt(sq(NEAR * 4))).isEqualTo(2);
		assertThat(lod.levelAt(sq(NEAR * 8))).isEqualTo(3);
		assertThat(lod.levelAt(sq(NEAR * 16))).isEqualTo(4);
	}

	@Test
	@DisplayName("the quantum stays proportional to the distance, which is the whole point")
	void theScreenSpaceErrorIsConstant() {
		PoseLod lod = lod();

		double worst = 0.0;
		for (int octave = 0; octave < PoseLod.MAX_LEVEL; octave++) {
			double distance = NEAR * Math.pow(2, octave + 1) - 0.001;
			int level = lod.levelAt(sq(distance));
			double ratio = PoseLod.quantumScale(level) / distance;
			worst = Math.max(worst, ratio);
		}

		assertThat(worst).isLessThanOrEqualTo(PoseLod.quantumScale(0) / NEAR + 1e-9);
	}

	@Test
	@DisplayName("levels stop rather than growing without bound")
	void theLevelIsCapped() {
		PoseLod lod = lod();

		assertThat(lod.levelAt(sq(NEAR * (1 << PoseLod.MAX_LEVEL)))).isEqualTo(PoseLod.MAX_LEVEL);
		assertThat(lod.levelAt(sq(NEAR * 100000))).isEqualTo(PoseLod.MAX_LEVEL);
		assertThat(lod.levelAt(Double.MAX_VALUE)).isEqualTo(PoseLod.MAX_LEVEL);
	}

	@Test
	@DisplayName("levelling off means level zero everywhere")
	void offIsOff() {
		assertThat(PoseLod.OFF.enabled()).isFalse();
		assertThat(PoseLod.OFF.levelAt(sq(100000))).isZero();
		assertThat(PoseLod.OFF.frozenAt(sq(100000))).isFalse();
	}

	@Test
	@DisplayName("freezing is off unless a distance is given")
	void freezingIsOptional() {
		assertThat(lod().frozenAt(sq(100000))).isFalse();

		PoseLod freezing = new PoseLod(NEAR, 256.0f);
		assertThat(freezing.frozenAt(sq(255))).isFalse();
		assertThat(freezing.frozenAt(sq(256))).isFalse();
		assertThat(freezing.frozenAt(sq(257))).isTrue();
	}

	@Test
	@DisplayName("the scale is a power of two, and clamps at both ends")
	void quantumScaleIsAPowerOfTwo() {
		assertThat(PoseLod.quantumScale(0)).isEqualTo(1);
		assertThat(PoseLod.quantumScale(1)).isEqualTo(2);
		assertThat(PoseLod.quantumScale(4)).isEqualTo(16);
		assertThat(PoseLod.quantumScale(PoseLod.MAX_LEVEL)).isEqualTo(1 << PoseLod.MAX_LEVEL);

		assertThat(PoseLod.quantumScale(-3)).isEqualTo(1);
		assertThat(PoseLod.quantumScale(64)).isEqualTo(1 << PoseLod.MAX_LEVEL);
	}
}
