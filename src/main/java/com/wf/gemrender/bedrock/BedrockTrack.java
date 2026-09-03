package com.wf.gemrender.bedrock;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public final class BedrockTrack {
	public record Key(float timeSeconds, List<Molang> pre, List<Molang> post, Easing easing,
			float easingArg) {
	}

	private final float[] times;
	private final float[] pre;
	private final float[] post;

	@Nullable
	private final Molang[] preExpr;
	@Nullable
	private final Molang[] postExpr;

	private final Easing[] easing;
	private final float[] easingArg;

	private final int hash;

	private BedrockTrack(float[] times, float[] pre, float[] post, Molang[] preExpr, Molang[] postExpr,
			Easing[] easing, float[] easingArg) {
		this.times = times;
		this.pre = pre;
		this.post = post;
		this.preExpr = preExpr;
		this.postExpr = postExpr;
		this.easing = easing;
		this.easingArg = easingArg;
		this.hash = Objects.hash(Arrays.hashCode(times), Arrays.hashCode(pre), Arrays.hashCode(post),
				Arrays.hashCode(preExpr), Arrays.hashCode(postExpr), Arrays.hashCode(easing),
				Arrays.hashCode(easingArg));
	}

	public static BedrockTrack of(List<Key> keys) {
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("a channel with no keyframes drives nothing");
		}

		List<Key> sorted = keys.stream()
				.sorted((a, b) -> Float.compare(a.timeSeconds(), b.timeSeconds()))
				.toList();

		int count = sorted.size();
		float[] times = new float[count];
		float[] pre = new float[count * 3];
		float[] post = new float[count * 3];
		Molang[] preExpr = new Molang[count * 3];
		Molang[] postExpr = new Molang[count * 3];
		Easing[] easing = new Easing[count];
		float[] easingArg = new float[count];
		boolean dynamic = false;

		for (int k = 0; k < count; k++) {
			Key key = sorted.get(k);
			times[k] = key.timeSeconds();
			easing[k] = key.easing();
			easingArg[k] = key.easingArg();

			for (int axis = 0; axis < 3; axis++) {
				dynamic |= flatten(key.pre()
						.get(axis), pre, preExpr, k * 3 + axis);
				dynamic |= flatten(key.post()
						.get(axis), post, postExpr, k * 3 + axis);
			}
		}

		return new BedrockTrack(times, pre, post, dynamic ? preExpr : null, dynamic ? postExpr : null,
				easing, easingArg);
	}

	private static boolean flatten(Molang value, float[] constants, Molang[] expressions, int index) {
		if (value.isConstant()) {
			constants[index] = (float) value.evaluate(0.0f);
			return false;
		}
		expressions[index] = value;
		return true;
	}

	public void sample(float timeSeconds, float[] out, int offset) {
		int count = times.length;
		int index = indexAtOrBefore(timeSeconds);

		if (index < 0 || index == count - 1) {
			int clamped = Math.max(index, 0);
			for (int axis = 0; axis < 3; axis++) {
				out[offset + axis] = value(post, postExpr, clamped, axis, timeSeconds);
			}
			return;
		}

		float span = times[index + 1] - times[index];
		float alpha = span <= 0.0f ? 0.0f : (timeSeconds - times[index]) / span;
		alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);

		if (easing[index] == Easing.CATMULLROM || easing[index + 1] == Easing.CATMULLROM) {
			int previous = index > 0 ? index - 1 : index;
			int next = index + 2 < count ? index + 2 : index + 1;
			for (int axis = 0; axis < 3; axis++) {
				out[offset + axis] = spline(alpha,
						value(post, postExpr, previous, axis, timeSeconds),
						value(post, postExpr, index, axis, timeSeconds),
						value(pre, preExpr, index + 1, axis, timeSeconds),
						value(pre, preExpr, next, axis, timeSeconds));
			}
			return;
		}

		float shaped = easing[index + 1].apply(alpha, easingArg[index + 1]);
		for (int axis = 0; axis < 3; axis++) {
			float from = value(post, postExpr, index, axis, timeSeconds);
			float to = value(pre, preExpr, index + 1, axis, timeSeconds);
			out[offset + axis] = from + (to - from) * shaped;
		}
	}

	static float spline(float alpha, float p0, float p1, float p2, float p3) {
		float a2 = alpha * alpha;
		float a3 = a2 * alpha;
		return 0.5f * (2.0f * p1 + (p2 - p0) * alpha
				+ (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * a2
				+ (3.0f * p1 - p0 - 3.0f * p2 + p3) * a3);
	}

	private float value(float[] constants, Molang[] expressions, int key, int axis, float timeSeconds) {
		int index = key * 3 + axis;
		if (expressions != null && expressions[index] != null) {
			return (float) expressions[index].evaluate(timeSeconds);
		}
		return constants[index];
	}

	private int indexAtOrBefore(float timeSeconds) {
		int low = 0;
		int high = times.length - 1;
		int found = -1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (times[mid] <= timeSeconds) {
				found = mid;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return found;
	}

	public float lastKeyTime() {
		return times[times.length - 1];
	}

	public int keyCount() {
		return times.length;
	}

	public boolean isDynamic() {
		return preExpr != null;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BedrockTrack that && hash == that.hash
				&& Arrays.equals(times, that.times) && Arrays.equals(pre, that.pre)
				&& Arrays.equals(post, that.post) && Arrays.equals(preExpr, that.preExpr)
				&& Arrays.equals(postExpr, that.postExpr) && Arrays.equals(easing, that.easing)
				&& Arrays.equals(easingArg, that.easingArg);
	}

	@Override
	public int hashCode() {
		return hash;
	}
}
