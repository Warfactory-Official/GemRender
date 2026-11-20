package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleStyleTest {
	@Test
	@DisplayName("a style writes its twenty-four floats in the order the shader reads them")
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
				.fadeOut(0.25f)
				.streak(0.04f)
				.build();

		float[] target = new float[ParticleStyle.FLOATS * 2];
		style.write(target, ParticleStyle.FLOATS);

		assertThat(target).startsWith(new float[ParticleStyle.FLOATS]);
		assertThat(Arrays.copyOfRange(target, ParticleStyle.FLOATS, target.length))
				.containsExactly(2.1f, 1.6f, 0.5f, 2.8f, 0.25f, 0.5f, 0.75f, 0.75f, 0.4f, 0.1f, 0.6f, 3.0f, 0.9f,
						0.8f, 2.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.04f, 0.0f, 0.0f, 0.0f);
	}

	@Test
	@DisplayName("fadeOut holds full alpha until its unit age, then fades to zero by the end of life")
	void fadeOutHoldsThenFades() {
		ParticleStyle style = ParticleStyle.builder()
				.alpha(0.8f, 1.0f)
				.fadeOut(0.6f)
				.build();

		assertThat(ParticleMotion.alpha(style, 0.0f)).isEqualTo(0.8f);
		assertThat(ParticleMotion.alpha(style, 0.59f)).isEqualTo(0.8f);
		assertThat(ParticleMotion.alpha(style, 0.8f)).isCloseTo(0.4f, within(1e-5f));
		assertThat(ParticleMotion.alpha(style, 1.0f)).isZero();
	}

	@Test
	@DisplayName("fadeOut 0 is the old whole-life fade, value for value")
	void fadeOutZeroIsUnchanged() {
		ParticleStyle style = ParticleStyle.builder()
				.alpha(0.7f, 0.5f)
				.build();
		for (float u = 0.0f; u <= 1.0f; u += 0.1f) {
			assertThat(ParticleMotion.alpha(style, u))
					.isEqualTo(Math.max(0.0f, Math.min(1.0f, 0.7f * (float) Math.pow(1.0f - u, 0.5f))));
		}
	}

	@Test
	@DisplayName("a contact response rides in the style's last texel, restitution first")
	void writesTheContactTexel() {
		ParticleStyle style = ParticleStyle.builder()
				.bouncesOnContact(0.4f, 0.7f)
				.build();

		float[] target = new float[ParticleStyle.FLOATS];
		style.write(target, 0);

		assertThat(style.response).isEqualTo(ContactResponse.BOUNCE);
		assertThat(style.collides()).isTrue();
		assertThat(Arrays.copyOfRange(target, 16, 20))
				.containsExactly(0.4f, 0.7f, ContactResponse.BOUNCE.ordinal(), 0.0f);
	}

	@Test
	@DisplayName("a style says so when it has no reason to be swept for contact")
	void inertStyleDoesNotCollide() {
		assertThat(ParticleStyle.builder()
				.build()
				.collides()).isFalse();
		assertThat(ParticleStyle.builder()
				.stopsOnContact()
				.build()
				.collides()).isTrue();
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
