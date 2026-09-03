package com.wf.gemrender.texture;

public record SpriteUv(float uOffset, float vOffset, float uScale, float vScale) {
	public static final SpriteUv IDENTITY = new SpriteUv(0.0f, 0.0f, 1.0f, 1.0f);

	public float u(float u) {
		return uOffset + u * uScale;
	}

	public float v(float v) {
		return vOffset + v * vScale;
	}

	public boolean isIdentity() {
		return equals(IDENTITY);
	}
}
