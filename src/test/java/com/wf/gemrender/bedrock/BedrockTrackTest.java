package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BedrockTrackTest {

	private static List<Molang> triple(double x, double y, double z) {
		return List.of(new Molang.Const(x), new Molang.Const(y), new Molang.Const(z));
	}

	private static BedrockTrack.Key key(float time, double value) {
		return new BedrockTrack.Key(time, triple(value, value * 2, value * 3),
				triple(value, value * 2, value * 3), Easing.LINEAR, Float.NaN);
	}

	private static float[] sample(BedrockTrack track, float time) {
		float[] out = new float[3];
		track.sample(time, out, 0);
		return out;
	}

	@Test
	@DisplayName("a value between two keys is the straight lerp of them")
	void linearBetweenKeys() {
		BedrockTrack track = BedrockTrack.of(List.of(key(0.0f, 0.0), key(2.0f, 10.0)));

		assertThat(sample(track, 0.0f)[0]).isCloseTo(0.0f, within(1e-6f));
		assertThat(sample(track, 0.5f)[0]).isCloseTo(2.5f, within(1e-6f));
		assertThat(sample(track, 1.0f)[0]).isCloseTo(5.0f, within(1e-6f));
		assertThat(sample(track, 2.0f)[0]).isCloseTo(10.0f, within(1e-6f));

		assertThat(sample(track, 1.0f)[1]).isCloseTo(10.0f, within(1e-6f));
		assertThat(sample(track, 1.0f)[2]).isCloseTo(15.0f, within(1e-6f));
	}

	@Test
	@DisplayName("outside the keyed range the value clamps to the nearest key's post")
	void clampsOutsideTheRange() {
		BedrockTrack track = BedrockTrack.of(List.of(key(1.0f, 4.0), key(2.0f, 8.0)));

		assertThat(sample(track, 0.0f)[0]).isCloseTo(4.0f, within(1e-6f));
		assertThat(sample(track, 9.0f)[0]).isCloseTo(8.0f, within(1e-6f));
	}

	@Test
	@DisplayName("keys are sorted, because a JSON object's iteration order is not the author's")
	void keysAreSorted() {
		BedrockTrack track = BedrockTrack.of(List.of(key(2.0f, 10.0), key(0.0f, 0.0), key(1.0f, 4.0)));

		assertThat(track.lastKeyTime()).isEqualTo(2.0f);
		assertThat(sample(track, 0.5f)[0]).isCloseTo(2.0f, within(1e-6f));
		assertThat(sample(track, 1.5f)[0]).isCloseTo(7.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a key whose two sides differ is a step, not a ramp")
	void preAndPostAreADiscontinuity() {
		BedrockTrack track = BedrockTrack.of(List.of(
				key(0.0f, 0.0),
				new BedrockTrack.Key(1.0f, triple(0.0, 0.0, 0.0), triple(100.0, 100.0, 100.0),
						Easing.LINEAR, Float.NaN),
				new BedrockTrack.Key(2.0f, triple(200.0, 200.0, 200.0), triple(200.0, 200.0, 200.0),
						Easing.LINEAR, Float.NaN)));

		assertThat(sample(track, 0.999f)[0]).as("approaching the key, the pre value")
				.isCloseTo(0.0f, within(0.01f));
		assertThat(sample(track, 1.0f)[0]).as("at the key, the post value: the cut")
				.isCloseTo(100.0f, within(1e-6f));
		assertThat(sample(track, 1.5f)[0]).as("halfway to the next key's pre")
				.isCloseTo(150.0f, within(1e-6f));
	}

	@Test
	@DisplayName("catmullrom on either end of a segment makes that segment a spline")
	void catmullRomSmoothsFromEitherEnd() {
		BedrockTrack straight = BedrockTrack.of(
				List.of(constant(0.0f, 0.0), constant(1.0f, 0.0), constant(2.0f, 10.0),
						constant(3.0f, 30.0)));
		BedrockTrack later = BedrockTrack.of(
				List.of(constant(0.0f, 0.0), constant(1.0f, 0.0), smooth(2.0f, 10.0),
						constant(3.0f, 30.0)));
		BedrockTrack earlier = BedrockTrack.of(
				List.of(constant(0.0f, 0.0), smooth(1.0f, 0.0), constant(2.0f, 10.0),
						constant(3.0f, 30.0)));

		assertThat(sample(straight, 1.5f)[0]).isCloseTo(5.0f, within(1e-6f));
		assertThat(sample(later, 1.5f)[0]).as("the key a segment arrives at curves it")
				.isCloseTo(3.75f, within(1e-5f));
		assertThat(sample(earlier, 1.5f)[0]).as("and so does the key it leaves, which GeckoLib misses")
				.isCloseTo(3.75f, within(1e-5f));
	}

	private static BedrockTrack.Key constant(float time, double value) {
		return new BedrockTrack.Key(time, triple(value, value, value), triple(value, value, value),
				Easing.LINEAR, Float.NaN);
	}

	private static BedrockTrack.Key smooth(float time, double value) {
		return new BedrockTrack.Key(time, triple(value, value, value), triple(value, value, value),
				Easing.CATMULLROM, Float.NaN);
	}

	@Test
	@DisplayName("the spline is uniform Catmull-Rom, and reduces to the value at the endpoints")
	void splineMatchesTheStandardPolynomial() {
		assertThat(BedrockTrack.spline(0.0f, 0.0f, 1.0f, 2.0f, 3.0f)).isCloseTo(1.0f, within(1e-6f));
		assertThat(BedrockTrack.spline(1.0f, 0.0f, 1.0f, 2.0f, 3.0f)).isCloseTo(2.0f, within(1e-6f));
		assertThat(BedrockTrack.spline(0.5f, 0.0f, 1.0f, 2.0f, 3.0f)).isCloseTo(1.5f, within(1e-6f));
		assertThat(BedrockTrack.spline(0.5f, 0.0f, 0.0f, 10.0f, 30.0f)).isCloseTo(3.75f, within(1e-6f));
	}

	@Test
	@DisplayName("a smoothed clip's first and last segments clamp their outer control points")
	void splineEndpointsClamp() {
		BedrockTrack track = BedrockTrack.of(List.of(
				new BedrockTrack.Key(0.0f, triple(0.0, 0.0, 0.0), triple(0.0, 0.0, 0.0),
						Easing.CATMULLROM, Float.NaN),
				new BedrockTrack.Key(1.0f, triple(10.0, 0.0, 0.0), triple(10.0, 0.0, 0.0),
						Easing.CATMULLROM, Float.NaN)));

		assertThat(sample(track, 0.0f)[0]).isCloseTo(0.0f, within(1e-5f));
		assertThat(sample(track, 1.0f)[0]).isCloseTo(10.0f, within(1e-5f));
		assertThat(sample(track, 0.5f)[0]).isBetween(0.0f, 10.0f);
	}

	@Test
	@DisplayName("the later key's easing shapes the interpolation factor")
	void easingReshapesTheFactor() {
		BedrockTrack linear = BedrockTrack.of(List.of(key(0.0f, 0.0), key(1.0f, 10.0)));
		BedrockTrack quad = BedrockTrack.of(List.of(key(0.0f, 0.0),
				new BedrockTrack.Key(1.0f, triple(10.0, 20.0, 30.0), triple(10.0, 20.0, 30.0),
						Easing.EASE_IN_QUAD, Float.NaN)));

		assertThat(sample(linear, 0.5f)[0]).isCloseTo(5.0f, within(1e-6f));
		assertThat(sample(quad, 0.5f)[0]).isCloseTo(2.5f, within(1e-6f));
		assertThat(sample(quad, 1.0f)[0]).isCloseTo(10.0f, within(1e-6f));
	}

	@Test
	@DisplayName("easingArgs feed the easings that take a parameter")
	void easingArgsReachTheCurve() {
		BedrockTrack stepped = BedrockTrack.of(List.of(key(0.0f, 0.0),
				new BedrockTrack.Key(1.0f, triple(10.0, 0.0, 0.0), triple(10.0, 0.0, 0.0), Easing.STEP,
						4.0f)));

		assertThat(sample(stepped, 0.3f)[0]).isCloseTo(2.5f, within(1e-5f));
		assertThat(sample(stepped, 0.6f)[0]).isCloseTo(5.0f, within(1e-5f));
	}

	@Test
	@DisplayName("a channel of literals never touches the expression evaluator")
	void constantChannelsAreFlattened() {
		assertThat(BedrockTrack.of(List.of(key(0.0f, 1.0)))
				.isDynamic()).isFalse();

		BedrockTrack dynamic = BedrockTrack.of(List.of(new BedrockTrack.Key(0.0f,
				List.of(Molang.parse("q.anim_time"), new Molang.Const(0), new Molang.Const(0)),
				List.of(Molang.parse("q.anim_time"), new Molang.Const(0), new Molang.Const(0)),
				Easing.LINEAR, Float.NaN)));
		assertThat(dynamic.isDynamic()).isTrue();

		assertThat(sample(dynamic, 2.5f)[0]).isCloseTo(2.5f, within(1e-6f));
	}

	@Test
	@DisplayName("two tracks with the same keyframes compare equal")
	void tracksHaveValueEquality() {
		BedrockTrack a = BedrockTrack.of(List.of(key(0.0f, 1.0), key(1.0f, 2.0)));
		BedrockTrack b = BedrockTrack.of(List.of(key(0.0f, 1.0), key(1.0f, 2.0)));
		BedrockTrack c = BedrockTrack.of(List.of(key(0.0f, 1.0), key(1.0f, 3.0)));

		assertThat(a).isEqualTo(b)
				.hasSameHashCodeAs(b)
				.isNotEqualTo(c);
	}

	@Test
	@DisplayName("a single keyframe holds its value for the whole clip")
	void oneKeyIsAConstantChannel() {
		BedrockTrack track = BedrockTrack.of(List.of(key(0.0f, 7.0)));

		assertThat(sample(track, 0.0f)[0]).isEqualTo(7.0f);
		assertThat(sample(track, 99.0f)[0]).isEqualTo(7.0f);
		assertThat(track.lastKeyTime()).isEqualTo(0.0f);
	}
}
