package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleStyleTest {
	@Test
	@DisplayName("a style writes its sixteen floats in the order the shader reads them")
	void writesTheShaderLayout() {
		ParticleStyle style = ParticleStyle.builder()
				.drag(2.1f)
				.gravity(1.6f)
				.size(0.5f, 2.8f)
				.tint(0.25f, 0.5f, 0.75f)
				.alpha(0.75f, 0.4f)
				.cool(0.1f, 0.6f)
				.spin(3.0f)
				.light(0.9f, 0.8f)
				.build();

		float[] target = new float[ParticleStyle.FLOATS * 2];
		style.write(target, ParticleStyle.FLOATS);

		assertThat(target).startsWith(new float[ParticleStyle.FLOATS]);
		assertThat(java.util.Arrays.copyOfRange(target, ParticleStyle.FLOATS, target.length))
				.containsExactly(2.1f, 1.6f, 0.5f, 2.8f, 0.25f, 0.5f, 0.75f, 0.75f, 0.4f, 0.1f, 0.6f, 3.0f, 0.9f,
						0.8f, 2.1f, 0.0f);
	}

	@Test
	@DisplayName("a packed rgb tint unpacks to the three channels")
	void unpacksAPackedTint() {
		ParticleStyle style = ParticleStyle.builder()
				.tint(0xFF8000)
				.build();

		assertThat(style.tintRed).isCloseTo(1.0f, within(1e-6f));
		assertThat(style.tintGreen).isCloseTo(128.0f / 255.0f, within(1e-6f));
		assertThat(style.tintBlue).isZero();
	}

	@Test
	@DisplayName("a per-tick drag factor outside the open unit interval means no drag")
	void degenerateDragFactors() {
		assertThat(ParticleStyle.dragFromPerTickFactor(1.0f)).isZero();
		assertThat(ParticleStyle.dragFromPerTickFactor(0.0f)).isZero();
		assertThat(ParticleStyle.dragFromPerTickFactor(-0.5f)).isZero();
		assertThat(ParticleStyle.dragFromPerTickFactor(0.9f)).isCloseTo(2.10721f, within(1e-4f));
	}

	@Test
	@DisplayName("a downward per-tick delta converts to a positive downward gravity")
	void gravitySignConvention() {
		assertThat(ParticleStyle.gravityFromPerTickDelta(-0.04f)).isCloseTo(16.0f, within(1e-4f));
		assertThat(ParticleStyle.gravityFromPerTickDelta(0.004f)).isCloseTo(-1.6f, within(1e-4f));
	}

	@Test
	@DisplayName("a default style is inert, opaque and full bright")
	void defaults() {
		ParticleStyle style = ParticleStyle.builder()
				.build();

		assertThat(style.drag).isZero();
		assertThat(style.gravity).isZero();
		assertThat(style.sizeAtBirth).isEqualTo(1.0f);
		assertThat(style.sizeGrowth).isZero();
		assertThat(style.lightBlock).isEqualTo(ParticleStyle.FULL_BRIGHT);
		assertThat(style.lightSky).isEqualTo(ParticleStyle.FULL_BRIGHT);
	}
}
