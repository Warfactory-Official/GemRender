package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnimationPhaseTest {
	private static final float DURATION = 2.0f;

	private static final GltfAnimation CLIP = new GltfAnimation("test", List.of(), DURATION);

	@Test
	@DisplayName("time wraps into the clip, forwards and backwards")
	void timeWraps() {
		AnimationPhase phase = AnimationPhase.of(CLIP);

		assertThat(phase.timeAt(0.0f)).isEqualTo(0.0f);
		assertThat(phase.timeAt(0.5f)).isEqualTo(0.5f);
		assertThat(phase.timeAt(DURATION)).isEqualTo(0.0f);
		assertThat(phase.timeAt(DURATION * 3 + 0.25f)).isCloseTo(0.25f, within(1e-5f));

		assertThat(phase.withOffset(-0.75f)
				.timeAt(0.0f))
				.as("wrapping a negative time")
				.isCloseTo(DURATION - 0.75f, within(1e-5f));
	}

	@Test
	@DisplayName("two phases with different offsets are never at the same point in the clip")
	void offsetsDesynchronise() {
		AnimationPhase a = AnimationPhase.of(CLIP)
				.withOffset(0.0f);
		AnimationPhase b = AnimationPhase.of(CLIP)
				.withOffset(0.4f);

		for (float t = 0.0f; t < DURATION * 2; t += 0.05f) {
			assertThat(b.timeAt(t))
					.as("offset copies at world time %s", t)
					.isNotEqualTo(a.timeAt(t));
		}
	}

	@Test
	@DisplayName("speed scales the clock but not the offset")
	void speedDoesNotShuffleTheOffset() {
		float offset = 0.3f;
		assertThat(AnimationPhase.of(CLIP)
				.withOffset(offset)
				.withSpeed(2.0f)
				.timeAt(0.0f))
				.isCloseTo(offset, within(1e-6f));

		assertThat(AnimationPhase.of(CLIP)
				.withOffset(offset)
				.withSpeed(2.0f)
				.timeAt(0.5f))
				.isCloseTo(1.0f + offset, within(1e-5f));
	}

	@Test
	@DisplayName("a speed of zero holds a pose, and no clip is a static phase")
	void staticPhases() {
		assertThat(AnimationPhase.REST.isStatic()).isTrue();
		assertThat(AnimationPhase.of(CLIP)
				.withSpeed(0.0f)
				.isStatic()).isTrue();
		assertThat(AnimationPhase.of(CLIP)
				.isStatic()).isFalse();

		AnimationPhase frozen = AnimationPhase.of(CLIP)
				.withOffset(0.7f)
				.withSpeed(0.0f);
		assertThat(frozen.timeAt(0.0f)).isEqualTo(frozen.timeAt(1000.0f));
	}

	@Test
	@DisplayName("the same seed always gives the same phase")
	void scatteringIsDeterministic() {
		long seed = 0x1234_5678_9ABC_DEF0L;
		assertThat(AnimationPhase.scattered(CLIP, seed))
				.isEqualTo(AnimationPhase.scattered(CLIP, seed));
	}

	@Test
	@DisplayName("neighbouring block positions land on well-separated phases")
	void scatteringSpreadsAdjacentPositions() {
		int span = 64;
		float[] offsets = new float[span];
		for (int i = 0; i < span; i++) {
			long packed = blockPosAsLong(i, 64, 0);
			offsets[i] = AnimationPhase.scattered(CLIP, packed)
					.offsetSeconds();
			assertThat(offsets[i]).isBetween(0.0f, DURATION);
		}

		int[] histogram = new int[8];
		for (float offset : offsets) {
			histogram[Math.min(7, (int) (offset / DURATION * 8))]++;
		}
		for (int eighth = 0; eighth < histogram.length; eighth++) {
			assertThat(histogram[eighth])
					.as("machines in eighth %d of the clip, of 64 in a row: %s", eighth,
							java.util.Arrays.toString(histogram))
					.isGreaterThan(0);
		}
	}

	@Test
	@DisplayName("scattering a null or zero-length clip yields the rest phase")
	void scatteringDegradesToRest() {
		assertThat(AnimationPhase.scattered(null, 1L)).isEqualTo(AnimationPhase.REST);
		assertThat(AnimationPhase.scattered(new GltfAnimation("empty", List.of(), 0.0f), 1L))
				.isEqualTo(AnimationPhase.REST);
	}

	@Test
	@DisplayName("snapping caps the distinct phases at the step count, however large the crowd")
	void snappingCapsDistinctPhases() {
		for (int steps : new int[] { 4, 8, 16 }) {
			java.util.Set<Float> distinct = new java.util.HashSet<>();
			for (int i = 0; i < 4096; i++) {
				float snapped = AnimationPhase.snap(i / 4096.0f, steps);
				assertThat(snapped)
						.as("snapped phase stays in the clip")
						.isGreaterThanOrEqualTo(0.0f)
						.isLessThan(1.0f);
				distinct.add(snapped);
			}
			assertThat(distinct)
					.as("distinct phases at %d steps", steps)
					.hasSize(steps);
		}
	}

	@Test
	@DisplayName("snapping is off by default and never moves a phase by more than one step")
	void snappingIsBoundedAndOptional() {
		assertThat(AnimationPhase.snap(0.37f, 0)).isEqualTo(0.37f);
		assertThat(AnimationPhase.snap(0.37f, -1)).isEqualTo(0.37f);

		assertThat(AnimationPhase.snap(1.0f, 8)).isCloseTo(7.0f / 8.0f, within(1e-6f));

		for (int i = 0; i <= 1000; i++) {
			float unit = i / 1000.0f;
			assertThat(unit - AnimationPhase.snap(unit, 8))
					.as("drift at %s", unit)
					.isBetween(0.0f, 1.0f / 8.0f);
		}
	}

	private static long blockPosAsLong(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) | ((long) z & 0x3FFFFFF) << 12;
	}
}
