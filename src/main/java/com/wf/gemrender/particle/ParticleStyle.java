package com.wf.gemrender.particle;

public final class ParticleStyle {
	public static final int FLOATS = 16;

	public static final float FULL_BRIGHT = 240.0f / 256.0f;

	public final float drag;
	public final float dragY;
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
	public final float fadeIn;

	private final float[] data;

	private ParticleStyle(float[] data) {
		this.data = data;
		drag = data[0];
		gravity = data[1];
		sizeAtBirth = data[2];
		sizeGrowth = data[3];
		tintRed = data[4];
		tintGreen = data[5];
		tintBlue = data[6];
		alphaScale = data[7];
		alphaFalloff = data[8];
		coolFloor = data[9];
		coolSpan = data[10];
		spinRate = data[11];
		lightBlock = data[12];
		lightSky = data[13];
		dragY = data[14];
		fadeIn = data[15];
	}

	public static ParticleStyle of(float... sixteen) {
		if (sixteen.length != FLOATS) {
			throw new IllegalArgumentException("A style is " + FLOATS + " floats, got " + sixteen.length);
		}
		return new ParticleStyle(sixteen.clone());
	}

	public static Builder builder() {
		return new Builder();
	}

	public void write(float[] target, int offset) {
		System.arraycopy(data, 0, target, offset, FLOATS);
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
		private float dragY = 0.0f;
		private boolean dragYSet;
		private float gravity = 0.0f;
		private float fadeIn = 0.0f;
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

		public Builder drag(float horizontalPerSecond, float verticalPerSecond) {
			drag = horizontalPerSecond;
			dragY = verticalPerSecond;
			dragYSet = true;
			return this;
		}

		public Builder fadeIn(float unitAge) {
			fadeIn = unitAge;
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
			return ParticleStyle.of(drag, gravity, sizeAtBirth, sizeGrowth,
					tintRed, tintGreen, tintBlue, alphaScale,
					alphaFalloff, coolFloor, coolSpan, spinRate,
					lightBlock, lightSky, dragYSet ? dragY : drag, fadeIn);
		}
	}
}
