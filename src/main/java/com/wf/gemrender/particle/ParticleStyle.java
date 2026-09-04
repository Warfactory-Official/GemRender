package com.wf.gemrender.particle;

public final class ParticleStyle {
	public static final int FLOATS = 16;

	public static final float FULL_BRIGHT = 240.0f / 256.0f;

	public final float drag;
	public final float gravity;
	public final float sizeAtBirth;
	public final float sizeGrowth;
	public final float tintRed;
	public final float tintGreen;
	public final float tintBlue;
	public final float alphaScale;
	public final float alphaFalloff;
	public final float coolFloor;
	public final float coolSpan;
	public final float spinRate;
	public final float lightBlock;
	public final float lightSky;

	private ParticleStyle(Builder builder) {
		drag = builder.drag;
		gravity = builder.gravity;
		sizeAtBirth = builder.sizeAtBirth;
		sizeGrowth = builder.sizeGrowth;
		tintRed = builder.tintRed;
		tintGreen = builder.tintGreen;
		tintBlue = builder.tintBlue;
		alphaScale = builder.alphaScale;
		alphaFalloff = builder.alphaFalloff;
		coolFloor = builder.coolFloor;
		coolSpan = builder.coolSpan;
		spinRate = builder.spinRate;
		lightBlock = builder.lightBlock;
		lightSky = builder.lightSky;
	}

	public static Builder builder() {
		return new Builder();
	}

	public void write(float[] target, int offset) {
		target[offset] = drag;
		target[offset + 1] = gravity;
		target[offset + 2] = sizeAtBirth;
		target[offset + 3] = sizeGrowth;
		target[offset + 4] = tintRed;
		target[offset + 5] = tintGreen;
		target[offset + 6] = tintBlue;
		target[offset + 7] = alphaScale;
		target[offset + 8] = alphaFalloff;
		target[offset + 9] = coolFloor;
		target[offset + 10] = coolSpan;
		target[offset + 11] = spinRate;
		target[offset + 12] = lightBlock;
		target[offset + 13] = lightSky;
		target[offset + 14] = 0.0f;
		target[offset + 15] = 0.0f;
	}

	public static float dragFromPerTickFactor(float factor) {
		if (factor <= 0.0f || factor >= 1.0f) {
			return 0.0f;
		}
		return (float) (-20.0 * Math.log(factor));
	}

	public static float gravityFromPerTickDelta(float deltaPerTick) {
		return -deltaPerTick * 400.0f;
	}

	public static final class Builder {
		private float drag = 0.0f;
		private float gravity = 0.0f;
		private float sizeAtBirth = 1.0f;
		private float sizeGrowth = 0.0f;
		private float tintRed = 1.0f;
		private float tintGreen = 1.0f;
		private float tintBlue = 1.0f;
		private float alphaScale = 1.0f;
		private float alphaFalloff = 1.0f;
		private float coolFloor = 1.0f;
		private float coolSpan = 1.0f;
		private float spinRate = 0.0f;
		private float lightBlock = FULL_BRIGHT;
		private float lightSky = FULL_BRIGHT;

		private Builder() {
		}

		public Builder drag(float perSecond) {
			drag = perSecond;
			return this;
		}

		public Builder gravity(float blocksPerSecondSquared) {
			gravity = blocksPerSecondSquared;
			return this;
		}

		public Builder size(float atBirth, float growth) {
			sizeAtBirth = atBirth;
			sizeGrowth = growth;
			return this;
		}

		public Builder tint(float red, float green, float blue) {
			tintRed = red;
			tintGreen = green;
			tintBlue = blue;
			return this;
		}

		public Builder tint(int rgb) {
			return tint(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
		}

		public Builder alpha(float scale, float falloff) {
			alphaScale = scale;
			alphaFalloff = falloff;
			return this;
		}

		public Builder cool(float floor, float span) {
			coolFloor = floor;
			coolSpan = span;
			return this;
		}

		public Builder spin(float radiansPerSecond) {
			spinRate = radiansPerSecond;
			return this;
		}

		public Builder light(float block, float sky) {
			lightBlock = block;
			lightSky = sky;
			return this;
		}

		public ParticleStyle build() {
			return new ParticleStyle(this);
		}
	}
}
