package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnimationDriveTest {
	private static final float DURATION = 2.0f;

	private static final GltfAnimation CLIP = new GltfAnimation("test", List.of(), DURATION);

	private static final float CIRCUMFERENCE = (float) (2.0 * Math.PI * 0.2);

	@Test
	@DisplayName("a cyclic drive runs through the clip once per cycle and keeps going")
	void cyclicRepeats() {
		AnimationDrive wheel = AnimationDrive.cyclic(CLIP, CIRCUMFERENCE);

		assertThat(wheel.timeAt(0.0f)).isEqualTo(0.0f);
		assertThat(wheel.timeAt(CIRCUMFERENCE / 4.0f)).isCloseTo(DURATION / 4.0f, within(1e-5f));
		assertThat(wheel.timeAt(CIRCUMFERENCE)).isCloseTo(0.0f, within(1e-5f));
		assertThat(wheel.timeAt(CIRCUMFERENCE * 3.25f)).isCloseTo(DURATION / 4.0f, within(1e-4f));
	}

	@Test
	@DisplayName("reversing runs the wheel backwards rather than off the front of the clip")
	void cyclicHandlesReverse() {
		AnimationDrive wheel = AnimationDrive.cyclic(CLIP, CIRCUMFERENCE);

		assertThat(wheel.timeAt(-CIRCUMFERENCE / 4.0f))
				.isCloseTo(DURATION * 0.75f, within(1e-4f));

		for (float metres = -50.0f; metres < 50.0f; metres += 0.37f) {
			assertThat(wheel.timeAt(metres))
					.as("odometer at %s m", metres)
					.isGreaterThanOrEqualTo(0.0f)
					.isLessThan(DURATION);
		}
	}

	@Test
	@DisplayName("a ranged drive scrubs linearly between its stops")
	void rangedScrubs() {
		AnimationDrive steering = AnimationDrive.ranged(CLIP, -30.0f, 30.0f);

		assertThat(steering.timeAt(-30.0f)).isEqualTo(0.0f);
		assertThat(steering.timeAt(0.0f)).isCloseTo(DURATION / 2.0f, within(1e-5f));
		assertThat(steering.timeAt(15.0f)).isCloseTo(DURATION * 0.75f, within(1e-5f));
		assertThat(steering.timeAt(30.0f)).isCloseTo(DURATION, within(1e-5f));
	}

	@Test
	@DisplayName("a ranged drive holds at its stops instead of wrapping past them")
	void rangedClampsRatherThanWrapping() {
		AnimationDrive steering = AnimationDrive.ranged(CLIP, -30.0f, 30.0f);

		assertThat(steering.timeAt(-500.0f)).isEqualTo(0.0f);
		assertThat(steering.timeAt(500.0f)).isCloseTo(DURATION, within(1e-5f));

		assertThat(steering.isAtEnd(29.0f)).isFalse();
		assertThat(steering.isAtEnd(30.0f)).isTrue();
		assertThat(steering.isAtEnd(500.0f)).isTrue();
	}

	@Test
	@DisplayName("stops given the other way round drive the clip in reverse, which is an inverted binding")
	void rangedInverts() {
		AnimationDrive normal = AnimationDrive.ranged(CLIP, 0.0f, 90.0f);
		AnimationDrive inverted = AnimationDrive.ranged(CLIP, 90.0f, 0.0f);

		for (float angle = 0.0f; angle <= 90.0f; angle += 5.0f) {
			assertThat(inverted.timeAt(angle))
					.as("mirrored at %s degrees", angle)
					.isCloseTo(DURATION - normal.timeAt(angle), within(1e-5f));
		}

		assertThat(inverted.isAtEnd(-1.0f)).isTrue();
	}

	@Test
	@DisplayName("a drive answers in the same currency a phase does, so the two are interchangeable")
	void agreesWithAPhaseOnTheSameFraction() {
		AnimationDrive drive = AnimationDrive.cyclic(CLIP, 1.0f);
		AnimationPhase phase = AnimationPhase.of(CLIP);

		for (float unit = 0.0f; unit < 3.0f; unit += 0.05f) {
			assertThat(drive.timeAt(unit))
					.as("drive against phase at %s cycles", unit)
					.isCloseTo(phase.timeAt(unit * DURATION), within(1e-4f));
		}
	}

	@Test
	@DisplayName("equal drives are equal values, which is what lets two identical vehicles share a pose")
	void driveIsAValue() {
		assertThat(AnimationDrive.cyclic(CLIP, CIRCUMFERENCE))
				.isEqualTo(AnimationDrive.cyclic(CLIP, CIRCUMFERENCE));

		assertThat(AnimationDrive.cyclic(CLIP, CIRCUMFERENCE))
				.isNotEqualTo(AnimationDrive.ranged(CLIP, 0.0f, CIRCUMFERENCE));
	}

	@Test
	@DisplayName("nothing to drive, or nowhere to drive it, holds the rest pose rather than dividing by zero")
	void degeneratesToRest() {
		assertThat(AnimationDrive.cyclic(null, 1.0f)
				.timeAt(5.0f)).isEqualTo(0.0f);
		assertThat(AnimationDrive.cyclic(CLIP, 0.0f)
				.timeAt(5.0f)).isEqualTo(0.0f);
		assertThat(AnimationDrive.ranged(CLIP, 10.0f, 10.0f)
				.timeAt(50.0f)).isEqualTo(0.0f);
		assertThat(AnimationDrive.ranged(CLIP, 10.0f, 10.0f)
				.isAtEnd(50.0f)).isFalse();

		assertThat(AnimationDrive.cyclic(new GltfAnimation("empty", List.of(), 0.0f), 1.0f)
				.timeAt(5.0f)).isEqualTo(0.0f);
	}
}
