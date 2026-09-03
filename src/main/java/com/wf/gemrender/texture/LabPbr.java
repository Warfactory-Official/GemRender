package com.wf.gemrender.texture;

public final class LabPbr {
	public static final int FLAT_NORMAL = pack(127, 127, 255, 255);

	public static final int INERT_SPECULAR = pack(0, 0, 0, 0);

	private static final int ALBEDO_F0 = 255;

	private static final int MAX_DIELECTRIC_F0 = 229;

	private static final int DIELECTRIC_F0 = 10;

	private static final int METALLIC_THRESHOLD = 128;

	private static final int MAX_EMISSION = 254;

	private LabPbr() {
	}

	public static int normal(int surface) {
		return pack(red(surface), green(surface), 255, 255);
	}

	public static int specular(int surface, int emissive) {
		int smoothness = 255 - blue(surface);

		int metallic = alpha(surface);
		int f0 = metallic >= METALLIC_THRESHOLD ? ALBEDO_F0
				: Math.max(DIELECTRIC_F0, metallic * MAX_DIELECTRIC_F0 / 255);

		int emission = Math.max(red(emissive), Math.max(green(emissive), blue(emissive)))
				* MAX_EMISSION / 255;

		return pack(smoothness, f0, 0, emission);
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

	private static int pack(int r, int g, int b, int a) {
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
