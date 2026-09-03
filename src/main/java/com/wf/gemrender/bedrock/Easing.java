package com.wf.gemrender.bedrock;

import java.util.Locale;

public enum Easing {
	LINEAR("linear"),
	NONE("none"),
	STEP("step"),
	CATMULLROM("catmullrom"),

	EASE_IN_SINE("easeinsine"), EASE_OUT_SINE("easeoutsine"), EASE_IN_OUT_SINE("easeinoutsine"),
	EASE_IN_QUAD("easeinquad"), EASE_OUT_QUAD("easeoutquad"), EASE_IN_OUT_QUAD("easeinoutquad"),
	EASE_IN_CUBIC("easeincubic"), EASE_OUT_CUBIC("easeoutcubic"), EASE_IN_OUT_CUBIC("easeinoutcubic"),
	EASE_IN_QUART("easeinquart"), EASE_OUT_QUART("easeoutquart"), EASE_IN_OUT_QUART("easeinoutquart"),
	EASE_IN_QUINT("easeinquint"), EASE_OUT_QUINT("easeoutquint"), EASE_IN_OUT_QUINT("easeinoutquint"),
	EASE_IN_EXPO("easeinexpo"), EASE_OUT_EXPO("easeoutexpo"), EASE_IN_OUT_EXPO("easeinoutexpo"),
	EASE_IN_CIRC("easeincirc"), EASE_OUT_CIRC("easeoutcirc"), EASE_IN_OUT_CIRC("easeinoutcirc"),
	EASE_IN_BACK("easeinback"), EASE_OUT_BACK("easeoutback"), EASE_IN_OUT_BACK("easeinoutback"),
	EASE_IN_ELASTIC("easeinelastic"), EASE_OUT_ELASTIC("easeoutelastic"),
	EASE_IN_OUT_ELASTIC("easeinoutelastic"),
	EASE_IN_BOUNCE("easeinbounce"), EASE_OUT_BOUNCE("easeoutbounce"),
	EASE_IN_OUT_BOUNCE("easeinoutbounce");

	private final String key;

	Easing(String key) {
		this.key = key;
	}

	public static Easing of(String name) {
		if (name == null) {
			return LINEAR;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		for (Easing easing : values()) {
			if (easing.key.equals(lower)) {
				return easing;
			}
		}
		return LINEAR;
	}

	public float apply(float t, float arg) {
		return switch (this) {
			case LINEAR, NONE, CATMULLROM -> t;
			case STEP -> (float) step(t, arg);

			case EASE_IN_SINE -> (float) sine(t);
			case EASE_OUT_SINE -> (float) out(t, arg, (x, unused) -> sine(x));
			case EASE_IN_OUT_SINE -> (float) inOut(t, arg, (x, unused) -> sine(x));

			case EASE_IN_QUAD -> t * t;
			case EASE_OUT_QUAD -> (float) out(t, arg, (x, unused) -> x * x);
			case EASE_IN_OUT_QUAD -> (float) inOut(t, arg, (x, unused) -> x * x);

			case EASE_IN_CUBIC -> t * t * t;
			case EASE_OUT_CUBIC -> (float) out(t, arg, (x, unused) -> x * x * x);
			case EASE_IN_OUT_CUBIC -> (float) inOut(t, arg, (x, unused) -> x * x * x);

			case EASE_IN_QUART -> (float) Math.pow(t, 4);
			case EASE_OUT_QUART -> (float) out(t, arg, (x, unused) -> Math.pow(x, 4));
			case EASE_IN_OUT_QUART -> (float) inOut(t, arg, (x, unused) -> Math.pow(x, 4));

			case EASE_IN_QUINT -> (float) Math.pow(t, 5);
			case EASE_OUT_QUINT -> (float) out(t, arg, (x, unused) -> Math.pow(x, 5));
			case EASE_IN_OUT_QUINT -> (float) inOut(t, arg, (x, unused) -> Math.pow(x, 5));

			case EASE_IN_EXPO -> (float) exp(t);
			case EASE_OUT_EXPO -> (float) out(t, arg, (x, unused) -> exp(x));
			case EASE_IN_OUT_EXPO -> (float) inOut(t, arg, (x, unused) -> exp(x));

			case EASE_IN_CIRC -> (float) circle(t);
			case EASE_OUT_CIRC -> (float) out(t, arg, (x, unused) -> circle(x));
			case EASE_IN_OUT_CIRC -> (float) inOut(t, arg, (x, unused) -> circle(x));

			case EASE_IN_BACK -> (float) back(t, arg);
			case EASE_OUT_BACK -> (float) out(t, arg, Easing::back);
			case EASE_IN_OUT_BACK -> (float) inOut(t, arg, Easing::back);

			case EASE_IN_ELASTIC -> (float) elastic(t, arg);
			case EASE_OUT_ELASTIC -> (float) out(t, arg, Easing::elastic);
			case EASE_IN_OUT_ELASTIC -> (float) inOut(t, arg, Easing::elastic);

			case EASE_IN_BOUNCE -> (float) bounce(t, arg);
			case EASE_OUT_BOUNCE -> (float) out(t, arg, Easing::bounce);
			case EASE_IN_OUT_BOUNCE -> (float) inOut(t, arg, Easing::bounce);
		};
	}

	@FunctionalInterface
	private interface Curve {
		double apply(double t, float arg);
	}

	private static double out(double t, float arg, Curve curve) {
		return 1.0 - curve.apply(1.0 - t, arg);
	}

	private static double inOut(double t, float arg, Curve curve) {
		if (t < 0.5) {
			return curve.apply(t * 2.0, arg) / 2.0;
		}
		return 1.0 - curve.apply((1.0 - t) * 2.0, arg) / 2.0;
	}

	private static double sine(double t) {
		return 1.0 - Math.cos(t * Math.PI / 2.0);
	}

	private static double circle(double t) {
		return 1.0 - Math.sqrt(1.0 - t * t);
	}

	private static double exp(double t) {
		return Math.pow(2.0, 10.0 * (t - 1.0));
	}

	private static double back(double t, float arg) {
		double k = Float.isNaN(arg) ? 1.70158 : arg * 1.70158;
		return t * t * ((k + 1.0) * t - k);
	}

	private static double elastic(double t, float arg) {
		double k = Float.isNaN(arg) ? 1.0 : arg;
		return 1.0 - Math.pow(Math.cos(t * Math.PI / 2.0), 3) * Math.cos(t * k * Math.PI);
	}

	private static double bounce(double t, float arg) {
		double n = Float.isNaN(arg) ? 0.5 : arg;
		double one = 121.0 / 16.0 * t * t;
		double two = 121.0 / 4.0 * n * sq(t - 6.0 / 11.0) + 1 - n;
		double three = 121.0 * n * n * sq(t - 9.0 / 11.0) + 1 - n * n;
		double four = 484.0 * n * n * n * sq(t - 10.5 / 11.0) + 1 - n * n * n;
		return Math.min(Math.min(one, two), Math.min(three, four));
	}

	private static double step(double t, float arg) {
		int steps = Float.isNaN(arg) ? 2 : (int) arg;
		if (steps < 2) {
			return t;
		}
		if (t <= 0.0) {
			return 0.0;
		}
		double last = (steps - 1) / (double) steps;
		return Math.min(Math.floor(t * steps) / steps, last);
	}

	private static double sq(double x) {
		return x * x;
	}
}
