package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LabPbrTest {
	private static final int FLAT = 127;

	private static final int METAL = 230;

	private static final int NO_EMISSION = 255;

	@Test
	@DisplayName("the two normal channels pass through, because both formats store only XY")
	void normalKeepsItsTangentXy() {
		int encoded = LabPbr.normal(surface(40, 200, 255, 0));

		assertThat(red(encoded)).isEqualTo(40);
		assertThat(green(encoded)).isEqualTo(200);
	}

	@Test
	@DisplayName("a surface with no detail encodes to LabPBR's own flat normal")
	void flatSurfaceIsAFlatNormal() {
		assertThat(LabPbr.normal(surface(FLAT, FLAT, 255, 0))).isEqualTo(LabPbr.FLAT_NORMAL);
	}

	@Test
	@DisplayName("roughness and smoothness are complements, so the round trip is exact")
	void roughnessInvertsToSmoothness() {
		for (int roughness = 0; roughness <= 255; roughness++) {
			int encoded = LabPbr.specular(surface(FLAT, FLAT, roughness, 0), 0);

			assertThat(255 - red(encoded))
					.as("roughness %d", roughness)
					.isEqualTo(roughness);
		}
	}

	@Test
	@DisplayName("a metal reads as a metal, and takes its reflectance from the albedo")
	void metalReachesTheMetalRange() {
		int encoded = LabPbr.specular(surface(FLAT, FLAT, 64, 255), 0);

		assertThat(green(encoded)).isGreaterThanOrEqualTo(METAL);
	}

	@Test
	@DisplayName("nothing short of a metal lands in the metal range, and nothing is unlit")
	void dielectricsStayDielectric() {
		for (int metallic = 0; metallic < 128; metallic++) {
			int encoded = LabPbr.specular(surface(FLAT, FLAT, 255, metallic), 0);

			assertThat(green(encoded))
					.as("metallic %d", metallic)
					.isLessThan(METAL)
					.isGreaterThan(0);
		}
	}

	@Test
	@DisplayName("emission takes the brightest channel, and never claims not to emit")
	void emissionNeverCollidesWithTheSentinel() {
		assertThat(alpha(LabPbr.specular(surface(FLAT, FLAT, 255, 0), 0))).isZero();

		for (int level = 0; level <= 255; level++) {
			int encoded = LabPbr.specular(surface(FLAT, FLAT, 255, 0), surface(0, level, 0, 255));

			assertThat(alpha(encoded))
					.as("emissive green %d", level)
					.isLessThan(NO_EMISSION);
		}

		assertThat(alpha(LabPbr.specular(surface(FLAT, FLAT, 255, 0), surface(255, 0, 0, 255))))
				.isEqualTo(NO_EMISSION - 1);
	}

	@Test
	@DisplayName("porosity is left alone, because glTF has nothing to put there")
	void porosityIsZero() {
		assertThat(blue(LabPbr.specular(surface(FLAT, FLAT, 128, 128), surface(9, 9, 9, 255))))
				.isZero();
	}

	private static int surface(int r, int g, int b, int a) {
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int red(int pixel) {
		return pixel & 0xFF;
	}

	private static int green(int pixel) {
		return (pixel >> 8) & 0xFF;
	}

	private static int blue(int pixel) {
		return (pixel >> 16) & 0xFF;
	}

	private static int alpha(int pixel) {
		return (pixel >>> 24) & 0xFF;
	}
}
