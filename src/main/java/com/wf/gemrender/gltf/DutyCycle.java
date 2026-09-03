package com.wf.gemrender.gltf;

/**
 * A driver that moves for part of its cycle and holds still for the rest, which is what most
 * gameplay-driven motion actually looks like: a turret traverses onto a target and then stays there,
 * a hatch opens once, a landing gear comes down and stops.
 *
 * <p>It exists because a layer that is holding is the only case where evaluating layers separately
 * beats evaluating them together. A layer that moves continuously crosses a new quantum every
 * quantum whatever its rate, so splitting it out saves nothing.
 */
public record DutyCycle(PoseDriver inner, float cycleSeconds, float duty) implements PoseDriver {
	public static PoseDriver of(PoseDriver inner, float duty) {
		if (duty >= 1.0f || inner.cycleSeconds() <= 0.0f) {
			return inner;
		}
		return new DutyCycle(inner, inner.cycleSeconds(), Math.max(0.0f, duty));
	}

	/**
	 * {@code timeSeconds} pinned to the end of the moving window once it is past it. Idempotent, so it
	 * is safe to apply both to a layer's clock and inside the driver the layer carries.
	 */
	public static float held(float timeSeconds, float cycleSeconds, float duty) {
		if (duty >= 1.0f || cycleSeconds <= 0.0f) {
			return timeSeconds;
		}

		float phase = timeSeconds % cycleSeconds;
		if (phase < 0.0f) {
			phase += cycleSeconds;
		}

		float window = cycleSeconds * duty;
		return phase <= window ? timeSeconds : timeSeconds - phase + window;
	}

	@Override
	public void apply(float timeSeconds, float[] scratch) {
		inner.apply(held(timeSeconds, cycleSeconds, duty), scratch);
	}

	@Override
	public int offset() {
		return inner.offset();
	}
}
