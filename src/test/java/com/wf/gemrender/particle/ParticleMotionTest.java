package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleMotionTest {
	private static final float TICK = 1.0f / 20.0f;

	private final Vector3f scratch = new Vector3f();

	private static ParticleStyle style(float drag, float gravity) {
		return ParticleStyle.builder()
				.drag(drag)
				.gravity(gravity)
				.build();
	}

	@Test
	@DisplayName("with neither drag nor gravity a particle travels in a straight line")
	void inertialTravel() {
		ParticleStyle style = style(0.0f, 0.0f);
		Vector3f spawn = new Vector3f(1.0f, 2.0f, 3.0f);
		Vector3f velocity = new Vector3f(4.0f, -5.0f, 6.0f);

		Vector3f at = ParticleMotion.position(style, spawn, velocity, 2.0f, scratch);

		assertThat(at.x).isCloseTo(9.0f, within(1e-5f));
		assertThat(at.y).isCloseTo(-8.0f, within(1e-5f));
		assertThat(at.z).isCloseTo(15.0f, within(1e-5f));
	}

	@Test
	@DisplayName("with gravity and no drag the path is the textbook parabola")
	void ballisticTravel() {
		ParticleStyle style = style(0.0f, 16.0f);
		Vector3f spawn = new Vector3f(0.0f, 0.0f, 0.0f);
		Vector3f velocity = new Vector3f(0.0f, 8.0f, 0.0f);

		for (float t = 0.0f; t <= 2.0f; t += 0.1f) {
			Vector3f at = ParticleMotion.position(style, spawn, velocity, t, scratch);
			assertThat(at.y).as("height at t=%.2f", t)
					.isCloseTo(8.0f * t - 0.5f * 16.0f * t * t, within(1e-4f));
		}
	}

	@Test
	@DisplayName("a drag converted from a per-tick factor reproduces that factor tick by tick")
	void dragMatchesThePerTickLoop() {
		float factor = 0.9f;
		ParticleStyle style = style(ParticleStyle.dragFromPerTickFactor(factor), 0.0f);
		Vector3f velocity = new Vector3f(3.0f, 0.0f, -2.0f);

		float expectedX = velocity.x;
		float expectedZ = velocity.z;

		for (int tick = 1; tick <= 20; tick++) {
			expectedX *= factor;
			expectedZ *= factor;

			Vector3f at = ParticleMotion.velocity(style, velocity, tick * TICK, scratch);

			assertThat(at.x).as("velocity x after %d ticks", tick)
					.isCloseTo(expectedX, within(1e-4f));
			assertThat(at.z).as("velocity z after %d ticks", tick)
					.isCloseTo(expectedZ, within(1e-4f));
		}
	}

	@Test
	@DisplayName("a gravity converted from a per-tick delta adds that delta each tick")
	void gravityMatchesThePerTickLoop() {
		float delta = 0.004f;
		ParticleStyle style = style(0.0f, ParticleStyle.gravityFromPerTickDelta(delta));
		Vector3f velocity = new Vector3f(0.0f, 0.0f, 0.0f);

		for (int tick = 1; tick <= 20; tick++) {
			Vector3f at = ParticleMotion.velocity(style, velocity, tick * TICK, scratch);
			assertThat(at.y).as("velocity y after %d ticks, in blocks per second", tick)
					.isCloseTo(delta * tick * 20.0f, within(1e-5f));
		}
	}

	@Test
	@DisplayName("under drag and gravity the velocity settles at the terminal value")
	void reachesTerminalVelocity() {
		ParticleStyle style = style(2.0f, 16.0f);
		Vector3f velocity = new Vector3f(5.0f, 5.0f, 5.0f);

		Vector3f late = ParticleMotion.velocity(style, velocity, 30.0f, scratch);

		assertThat(late.x).isCloseTo(0.0f, within(1e-4f));
		assertThat(late.z).isCloseTo(0.0f, within(1e-4f));
		assertThat(late.y).isCloseTo(-16.0f / 2.0f, within(1e-3f));
	}

	@Test
	@DisplayName("the closed-form position is the integral of the closed-form velocity")
	void positionIntegratesVelocity() {
		ParticleStyle style = style(2.107f, 9.0f);
		Vector3f spawn = new Vector3f(0.0f, 0.0f, 0.0f);
		Vector3f velocity = new Vector3f(2.0f, 7.0f, -3.0f);

		int steps = 200000;
		float horizon = 3.0f;
		float step = horizon / steps;

		Vector3f integrated = new Vector3f();
		Vector3f sample = new Vector3f();
		for (int i = 0; i < steps; i++) {
			ParticleMotion.velocity(style, velocity, (i + 0.5f) * step, sample);
			integrated.add(sample.mul(step));
		}

		Vector3f closedForm = ParticleMotion.position(style, spawn, velocity, horizon, scratch);

		assertThat(closedForm.x).isCloseTo(integrated.x, within(1e-3f));
		assertThat(closedForm.y).isCloseTo(integrated.y, within(1e-3f));
		assertThat(closedForm.z).isCloseTo(integrated.z, within(1e-3f));
	}

	@Test
	@DisplayName("the drag branch agrees with the dragless branch as drag approaches zero")
	void dragBranchesAgreeAtTheThreshold() {
		Vector3f spawn = new Vector3f(1.0f, 1.0f, 1.0f);
		Vector3f velocity = new Vector3f(3.0f, 4.0f, 5.0f);

		Vector3f dragless = ParticleMotion.position(style(0.0f, 12.0f), spawn, velocity, 1.5f, new Vector3f());
		Vector3f barelyDragged = ParticleMotion.position(style(1e-4f, 12.0f), spawn, velocity, 1.5f, scratch);

		assertThat(barelyDragged.x).isCloseTo(dragless.x, within(1e-3f));
		assertThat(barelyDragged.y).isCloseTo(dragless.y, within(1e-3f));
		assertThat(barelyDragged.z).isCloseTo(dragless.z, within(1e-3f));
	}

	@Test
	@DisplayName("a particle is alive from spawn up to but not including its lifetime")
	void lifetimeBoundaries() {
		assertThat(ParticleMotion.alive(-0.01f, 2.0f)).isFalse();
		assertThat(ParticleMotion.alive(0.0f, 2.0f)).isTrue();
		assertThat(ParticleMotion.alive(1.999f, 2.0f)).isTrue();
		assertThat(ParticleMotion.alive(2.0f, 2.0f)).isFalse();
	}

	@Test
	@DisplayName("an unwritten slot reads as a dead particle")
	void emptySlotIsDead() {
		assertThat(ParticleMotion.alive(0.0f, 0.0f)).isFalse();
		assertThat(ParticleMotion.unitAge(0.0f, 0.0f)).isZero();
	}

	@Test
	@DisplayName("size grows from birth to the end of life and alpha fades to nothing")
	void sizeAndAlphaCurves() {
		ParticleStyle style = ParticleStyle.builder()
				.size(0.5f, 2.8f)
				.alpha(0.75f, 0.4f)
				.build();

		assertThat(ParticleMotion.size(style, 2.0f, 0.0f)).isCloseTo(1.0f, within(1e-6f));
		assertThat(ParticleMotion.size(style, 2.0f, 1.0f)).isCloseTo(6.6f, within(1e-5f));

		assertThat(ParticleMotion.alpha(style, 0.0f)).isCloseTo(0.75f, within(1e-6f));
		assertThat(ParticleMotion.alpha(style, 1.0f)).isCloseTo(0.0f, within(1e-6f));
	}

	@Test
	@DisplayName("the cooling curve falls to its floor once the span has elapsed and stays there")
	void coolingCurve() {
		ParticleStyle style = ParticleStyle.builder()
				.cool(0.1f, 0.6f)
				.build();

		assertThat(ParticleMotion.cool(style, 0.0f)).isCloseTo(1.0f, within(1e-6f));
		assertThat(ParticleMotion.cool(style, 0.3f)).isCloseTo(0.55f, within(1e-5f));
		assertThat(ParticleMotion.cool(style, 0.6f)).isCloseTo(0.1f, within(1e-6f));
		assertThat(ParticleMotion.cool(style, 1.0f)).isCloseTo(0.1f, within(1e-6f));
	}
}
