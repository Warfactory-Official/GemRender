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

	private static float axisVelocity(float drag, float spawnVelocity, float acceleration, float age) {
		if (drag > DRAG_EPSILON) {
			float terminal = acceleration / drag;
			return (spawnVelocity - terminal) * (float) Math.exp(-drag * age) + terminal;
		}

		return spawnVelocity + acceleration * age;
	}

	private static float axisPosition(float drag, float spawnPosition, float spawnVelocity, float acceleration,
			float age) {
		if (drag > DRAG_EPSILON) {
			float terminal = acceleration / drag;
			float travel = (1.0f - (float) Math.exp(-drag * age)) / drag;
			return spawnPosition + (spawnVelocity - terminal) * travel + terminal * age;
		}

		return spawnPosition + spawnVelocity * age + 0.5f * acceleration * age * age;
	}

	public static Vector3f velocity(ParticleStyle style, Vector3f spawnVelocity, float age, Vector3f target) {
		return target.set(axisVelocity(style.drag, spawnVelocity.x, 0.0f, age),
				axisVelocity(style.dragY, spawnVelocity.y, -style.gravity, age),
				axisVelocity(style.drag, spawnVelocity.z, 0.0f, age));
	}

	public static Vector3f position(ParticleStyle style, Vector3f spawnPosition, Vector3f spawnVelocity, float age,
			Vector3f target) {
		return target.set(
				axisPosition(style.drag, spawnPosition.x, spawnVelocity.x, 0.0f, age),
				axisPosition(style.dragY, spawnPosition.y, spawnVelocity.y, -style.gravity, age),
				axisPosition(style.drag, spawnPosition.z, spawnVelocity.z, 0.0f, age));
	}

	public static float size(ParticleStyle style, float sizeScale, float unitAge) {
		return sizeScale * (style.sizeAtBirth + style.sizeGrowth * unitAge);
	}

	public static float alpha(ParticleStyle style, float unitAge) {
		float alpha = style.alphaScale * (float) Math.pow(1.0f - unitAge, style.alphaFalloff);
		float ramp = style.fadeIn > DRAG_EPSILON ? Math.min(unitAge / style.fadeIn, 1.0f) : 1.0f;
		return Math.clamp(alpha * ramp, 0.0f, 1.0f);
	}

	public static float cool(ParticleStyle style, float unitAge) {
		float span = Math.max(style.coolSpan, 1e-6f);
		return style.coolFloor + (1.0f - style.coolFloor) * (1.0f - Math.min(unitAge / span, 1.0f));
	}
}
