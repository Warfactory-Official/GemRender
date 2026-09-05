package com.wf.gemrender.volume;

public record VolumeStyle(float density, float red, float green, float blue, float detail, float edge,
		float rise, float phase, float ambient, float blockLight, float skyLight, int steps, int sunSteps,
		float sunDensity, float sunStrength) {
	public static final int FLOATS = 20;

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private float density = 1.2f;

		private float red = 1.0f;

		private float green = 1.0f;

		private float blue = 1.0f;

		private float detail = 0.6f;

		private float edge = 0.35f;

		private float rise = 0.35f;

		private float phase = 0.3f;

		private float ambient = 0.35f;

		private float blockLight = 1.0f;

		private float skyLight = 1.0f;

		private int steps = VolumeQuality.DEFAULT.steps();

		private int sunSteps = VolumeQuality.DEFAULT.sunSteps();

		private float sunDensity = 1.0f;

		private float sunStrength = 0.3f;

		private Builder() {
		}

		public Builder density(float value) {
			density = value;
			return this;
		}

		public Builder tint(int rgb) {
			red = ((rgb >> 16) & 0xFF) / 255.0f;
			green = ((rgb >> 8) & 0xFF) / 255.0f;
			blue = (rgb & 0xFF) / 255.0f;
			return this;
		}

		public Builder tint(float r, float g, float b) {
			red = r;
			green = g;
			blue = b;
			return this;
		}

		public Builder detail(float value) {
			detail = value;
			return this;
		}

		public Builder edge(float value) {
			edge = value;
			return this;
		}

		public Builder rise(float value) {
			rise = value;
			return this;
		}

		public Builder phase(float value) {
			phase = value;
			return this;
		}

		public Builder ambient(float value) {
			ambient = value;
			return this;
		}

		public Builder light(float block, float sky) {
			blockLight = block;
			skyLight = sky;
			return this;
		}

		public Builder quality(VolumeQuality quality) {
			steps = quality.steps();
			sunSteps = quality.sunSteps();
			return this;
		}

		public Builder steps(int primary, int sun) {
			steps = primary;
			sunSteps = sun;
			return this;
		}

		public Builder sunDensity(float value) {
			sunDensity = value;
			return this;
		}

		public Builder sunStrength(float value) {
			sunStrength = value;
			return this;
		}

		public VolumeStyle build() {
			return new VolumeStyle(density, red, green, blue, detail, edge, rise, phase, ambient,
					blockLight, skyLight, steps, sunSteps, sunDensity, sunStrength);
		}
	}
}
