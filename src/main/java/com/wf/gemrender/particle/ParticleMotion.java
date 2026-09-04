package com.wf.gemrender.particle;

import org.joml.Vector3f;

public final class ParticleMotion {
	private static final float DRAG_EPSILON = 1e-4f;

	private ParticleMotion() {
	}

	public static float unitAge(float age, float life) {
		if (life <= 0.0f) {
			return 0.0f;
		}
		return Math.clamp(age / life, 0.0f, 1.0f);
	}

	public static boolean alive(float age, float life) {
		return life > 0.0f && age >= 0.0f && age < life;
	}

	public static Vector3f velocity(ParticleStyle style, Vector3f spawnVelocity, float age, Vector3f target) {
		float gravity = -style.gravity;

		if (style.drag > DRAG_EPSILON) {
			float decay = (float) Math.exp(-style.drag * age);
			float terminal = gravity / style.drag;
			return target.set(spawnVelocity.x * decay,
					(spawnVelocity.y - terminal) * decay + terminal,
					spawnVelocity.z * decay);
		}

		return target.set(spawnVelocity.x, spawnVelocity.y + gravity * age, spawnVelocity.z);
	}

	public static Vector3f position(ParticleStyle style, Vector3f spawnPosition, Vector3f spawnVelocity, float age,
			Vector3f target) {
		float gravity = -style.gravity;

		if (style.drag > DRAG_EPSILON) {
			float travel = (1.0f - (float) Math.exp(-style.drag * age)) / style.drag;
			float terminal = gravity / style.drag;
			return target.set(spawnPosition.x + spawnVelocity.x * travel,
					spawnPosition.y + (spawnVelocity.y - terminal) * travel + terminal * age,
					spawnPosition.z + spawnVelocity.z * travel);
		}

		return target.set(spawnPosition.x + spawnVelocity.x * age,
				spawnPosition.y + spawnVelocity.y * age + 0.5f * gravity * age * age,
				spawnPosition.z + spawnVelocity.z * age);
	}

	public static float size(ParticleStyle style, float sizeScale, float unitAge) {
		return sizeScale * (style.sizeAtBirth + style.sizeGrowth * unitAge);
	}

	public static float alpha(ParticleStyle style, float unitAge) {
		return Math.clamp(style.alphaScale * (float) Math.pow(1.0f - unitAge, style.alphaFalloff), 0.0f, 1.0f);
	}

	public static float cool(ParticleStyle style, float unitAge) {
		float span = Math.max(style.coolSpan, 1e-6f);
		return style.coolFloor + (1.0f - style.coolFloor) * (1.0f - Math.min(unitAge / span, 1.0f));
	}
}
