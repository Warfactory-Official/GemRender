package com.wf.gemrender.gltf.blend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseChannel;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.vendor.mcgltf.animation.LinearInterpolatedChannel;
import com.wf.gemrender.vendor.mcgltf.animation.SphericalLinearInterpolatedChannel;
import java.util.ArrayList;
import java.util.List;

class CrossfadeTest {
	private final NodeTable table = BlendFixture.table(15, 9L);
	private final GltfAnimation idle = BlendFixture.clip(table, "idle", 2.0f, 5, 0.6f, 31L);
	private final GltfAnimation reload = BlendFixture.clip(table, "reload", 1.5f, 6, 1.5f, 32L);
	private final GltfAnimation fire = BlendFixture.clip(table, "fire", 0.3f, 3, 0.9f, 33L);
	private final BlendEvaluator.Scratch scratch = new BlendEvaluator.Scratch();

	private float[] at(Crossfade fade, float now) {
		AnimationBlend blend = new AnimationBlend();
		fade.write(now, blend);
		float[] out = table.newScratch();
		BlendEvaluator.evaluate(table, blend, out, scratch);
		return out;
	}

	private float[] clip(GltfAnimation clip, float time) {
		float[] out = table.newScratch();
		clip.apply(clip.loop(time), out);
		return out;
	}

	/**
	 * Largest per-float change between consecutive samples over {@code [from, to)}, step {@code dt}.
	 */
	private float maxStep(Crossfade fade, float from, float to, float dt) {
		float worst = 0.0f;
		float[] previous = at(fade, from);
		for (float t = from + dt; t < to; t += dt) {
			float[] now = at(fade, t);
			worst = Math.max(worst, poseDelta(table, previous, now));
			previous = now;
		}
		return worst;
	}

	/**
	 * Max over nodes of translation/scale difference and {@code 2|q_a - (+-q_b)|} (~ angle); blind to {@code q == -q}.
	 */
	static float poseDelta(NodeTable table, float[] a, float[] b) {
		float worst = 0.0f;
		for (int n = 0; n < table.nodeCount(); n++) {
			int s = n * NodeTable.TRS_STRIDE;
			for (int k : new int[] { 0, 1, 2, 7, 8, 9 }) {
				worst = Math.max(worst, Math.abs(a[s + k] - b[s + k]));
			}
			float minus = 0.0f;
			float plus = 0.0f;
			for (int k = 3; k < 7; k++) {
				minus += (a[s + k] - b[s + k]) * (a[s + k] - b[s + k]);
				plus += (a[s + k] + b[s + k]) * (a[s + k] + b[s + k]);
			}
			worst = Math.max(worst, 2.0f * (float) Math.sqrt(Math.min(minus, plus)));
		}
		for (int i = table.nodeCount() * NodeTable.TRS_STRIDE; i < a.length; i++) {
			worst = Math.max(worst, Math.abs(a[i] - b[i]));
		}
		return worst;
	}

	@Test
	@DisplayName("a fade starts on the outgoing pose, ends on the incoming one, and never pops")
	void continuity() {
		for (FadeCurve curve : new FadeCurve[] { FadeCurve.LINEAR, FadeCurve.SMOOTH }) {
			float start = 1.0f;
			Crossfade fade = fading(curve, start);

			assertThat(poseDelta(table, at(fade, start), clip(idle, start)))
					.as("%s: first frame is the outgoing clip", curve).isLessThan(1e-5f);
			assertThat(poseDelta(table, at(fade, start + 0.25f), clip(reload, 0.25f)))
					.as("%s: after the fade, the incoming clip", curve).isLessThan(1e-5f);
			float mid = poseDelta(table, at(fade, start + 0.125f), clip(idle, start + 0.125f));
			assertThat(mid).as("%s: mid-fade differs from the outgoing clip", curve).isGreaterThan(0.01f);

			float dt = 1.0f / 480.0f;
			float fading = maxStep(fading(curve, start), start - 0.1f, start + 0.35f, dt);
			float playing = Math.max(maxStep(single(idle), start - 0.1f, start + 0.35f, dt),
					maxStep(single(reload), start - 0.1f, start + 0.35f, dt));
			assertThat(fading).as("%s: largest per-step change while fading vs either clip alone", curve)
					.isLessThan(playing * 3.0f);
		}
	}

	/**
	 * Fresh per sweep: {@link Crossfade#write} prunes, so time must run forward.
	 */
	private Crossfade fading(FadeCurve curve, float start) {
		Crossfade fade = new Crossfade(table);
		fade.play(idle, 0.0f, 0.0f, curve);
		fade.play(reload, start, 0.25f, curve);
		return fade;
	}

	private Crossfade inertial(float start, float duration) {
		Crossfade fade = new Crossfade(table);
		fade.play(idle, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(reload, start, duration, FadeCurve.INERTIAL);
		return fade;
	}

	private Crossfade single(GltfAnimation clip) {
		Crossfade fade = new Crossfade(table);
		fade.play(clip, 0.0f, 0.0f, FadeCurve.LINEAR);
		return fade;
	}

	@Test
	@DisplayName("an interrupted fade continues from what is on screen")
	void interrupted() {
		Crossfade fade = new Crossfade(table);
		fade.play(idle, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(reload, 1.0f, 0.4f, FadeCurve.LINEAR);
		float[] before = at(fade, 1.2f);
		fade.play(fire, 1.2f, 0.2f, FadeCurve.LINEAR);
		assertThat(fade.playing()).isEqualTo(3);
		assertThat(poseDelta(table, at(fade, 1.2f), before)).isLessThan(1e-5f);
		assertThat(BlendEvaluatorTest.maxDifference(at(fade, 1.45f), clip(fire, 0.25f))).isLessThan(1e-5f);
		assertThat(fade.playing()).as("covered entries dropped").isEqualTo(1);
		assertThat(fade.fading(1.45f)).isFalse();
	}

	@Test
	@DisplayName("weights are a partition of the layer weight and follow the curve")
	void weights() {
		Crossfade fade = new Crossfade(table);
		fade.play(idle, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(reload, 1.0f, 1.0f, FadeCurve.SMOOTH);
		AnimationBlend blend = new AnimationBlend();
		fade.write(1.25f, blend, 0.8f, BlendMask.ALL);
		assertThat(blend.layerCount()).isEqualTo(2);
		float s = 0.25f * 0.25f * (3 - 2 * 0.25f);
		assertThat(blend.weight(0)).as("incoming").isCloseTo(0.8f * s, within(1e-6f));
		assertThat(blend.weight(1)).as("outgoing").isCloseTo(0.8f * (1 - s), within(1e-6f));
		assertThat(blend.clip(0)).isSameAs(reload);
		assertThat(blend.time(0)).isCloseTo(0.25f, within(1e-6f));
	}

	@Test
	@DisplayName("inertial: continuous at the switch, exactly the target after it, no pop between")
	void inertial() {
		float start = 0.7f;
		float duration = 0.3f;
		Crossfade fade = inertial(start, duration);
		assertThat(fade.playing()).as("outgoing clip dropped, not evaluated").isEqualTo(1);

		assertThat(poseDelta(table, at(fade, start), clip(idle, start))).as("value at the switch")
				.isLessThan(1e-4f);
		assertThat(poseDelta(table, at(fade, start), clip(reload, 0.0f))).as("the switch is a real jump")
				.isGreaterThan(0.1f);

		assertThat(BlendEvaluatorTest.maxDifference(at(fade, start + duration + 0.01f),
				clip(reload, duration + 0.01f))).as("after the decay").isLessThan(1e-6f);
		assertThat(fade.fading(start + duration + 0.01f)).isFalse();
		AnimationBlend after = new AnimationBlend();
		fade.write(start + duration + 0.01f, after);
		assertThat(after.isPrivate()).isFalse();

		float dt = 1.0f / 480.0f;
		Crossfade sweep = inertial(start, duration);
		float across = poseDelta(table, at(single(idle), start - dt), at(sweep, start));
		float budget = Math.max(maxStep(single(idle), start - 0.05f, start + duration, dt),
				maxStep(single(reload), 0.0f, duration, dt)) * 3.0f;
		assertThat(across).as("the step across the switch").isLessThan(budget);
		assertThat(maxStep(sweep, start, start + duration, dt)).as("no pop anywhere in the decay")
				.isLessThan(budget);
	}

	@Test
	@DisplayName("inertial: a source closing on the target keeps its velocity through the switch")
	void inertialVelocity() {
		NodeTable one = BlendFixture.table(1, 4L);
		float speed = 2.0f;
		GltfAnimation slide = translateX(one, "slide", 0.0f, speed * 4.0f, 4.0f);
		GltfAnimation hold = translateX(one, "hold", 1.0f, 1.0f, 1.0f);
		Crossfade fade = new Crossfade(one);
		fade.play(slide, 0.0f, 0.0f, FadeCurve.LINEAR, 0.0f, 1.0f, false);
		fade.play(hold, 0.2f, 0.5f, FadeCurve.INERTIAL, 0.0f, 0.0f, false);
		float h = 1.0e-3f;
		float x0 = x(one, fade, 0.2f);
		float x1 = x(one, fade, 0.2f + h);
		assertThat(x0).as("source position at the switch").isCloseTo(0.4f, within(1e-4f));
		assertThat((x1 - x0) / h).as("velocity just after the switch").isCloseTo(speed, within(0.05f));
		assertThat(x(one, fade, 0.2f + 0.5f)).as("arrived").isCloseTo(1.0f, within(1e-5f));
	}

	private float x(NodeTable table, Crossfade fade, float now) {
		AnimationBlend blend = new AnimationBlend();
		fade.write(now, blend);
		float[] out = table.newScratch();
		BlendEvaluator.evaluate(table, blend, out, scratch);
		return out[0];
	}

	private static GltfAnimation translateX(NodeTable table, String name, float from, float to, float duration) {
		float[][] values = { { from, table.restTranslation(0, 1), table.restTranslation(0, 2) },
				{ to, table.restTranslation(0, 1), table.restTranslation(0, 2) } };
		return new GltfAnimation(name, List.of(new PoseChannel(
				new LinearInterpolatedChannel(new float[] { 0.0f, duration },
						values), 0, 3)), duration);
	}

	@Test
	@DisplayName("the quintic lands at zero with zero slope and never overshoots")
	void quintic() {
		float[] c = new float[7];
		for (float v0 : new float[] { -4.0f, -1.0f, 0.0f, 2.0f }) {
			float t1 = Inertialization.curve(c, 0, 1.0f, v0, 0.5f);
			assertThat(Inertialization.value(c, 0, 0.0f)).isCloseTo(1.0f, within(1e-6f));
			float eps = 1e-3f;
			float end = Inertialization.value(c, 0, t1 - eps);
			assertThat(end).as("v0=%s near t1", v0).isCloseTo(0.0f, within(1e-4f));
			for (float t = 0; t < t1; t += t1 / 200) {
				assertThat(Inertialization.value(c, 0, t)).as("v0=%s t=%s", v0, t).isGreaterThanOrEqualTo(-1e-4f);
			}
			if (v0 <= 0.0f) {
				float slope = (Inertialization.value(c, 0, eps) - Inertialization.value(c, 0, 0.0f)) / eps;
				assertThat(slope).as("initial slope").isCloseTo(v0, within(0.05f + Math.abs(v0) * 0.02f));
			}
		}
	}
	@Test
	@DisplayName("inertial right after play: a looping clip's wrap is not read as velocity")
	void inertialRightAfterPlay() {
		NodeTable one = BlendFixture.table(1, 4L);
		GltfAnimation ramp = translateX(one, "ramp", 0.0f, 10.0f, 1.0f);
		GltfAnimation hold = translateX(one, "hold", -5.0f, -5.0f, 1.0f);
		Crossfade fade = new Crossfade(one);
		fade.play(ramp, 0.0f, 0.0f, FadeCurve.LINEAR);
		float start = 0.001f;
		fade.play(hold, start, 0.5f, FadeCurve.INERTIAL, 0.0f, 0.0f, false);
		assertThat(x(one, fade, start)).isCloseTo(0.01f, within(1e-4f));
		assertThat(x(one, fade, start + 0.1f)).as("offset still decaying, not snapped to the target")
				.isGreaterThan(-1.0f);
		float h = 1.0e-3f;
		assertThat((x(one, fade, start + h) - x(one, fade, start)) / h).as("no false velocity")
				.isGreaterThan(-1.0f);
	}

	@Test
	@DisplayName("inertial: a rotation closing on the target keeps its angular velocity")
	void inertialRotationVelocity() {
		NodeTable one = BlendFixture.table(1, 4L);
		float rate = 0.5f;
		GltfAnimation turn = rotateY(one, "turn", 0.0f, rate * 4.0f, 4.0f);
		GltfAnimation hold = rotateY(one, "hold", 1.0f, 1.0f, 1.0f);
		Crossfade fade = new Crossfade(one);
		fade.play(turn, 0.0f, 0.0f, FadeCurve.LINEAR, 0.0f, 1.0f, false);
		fade.play(hold, 0.2f, 0.5f, FadeCurve.INERTIAL, 0.0f, 0.0f, false);
		float h = 1.0e-3f;
		float a0 = yaw(one, fade, 0.2f);
		assertThat(a0).as("source angle at the switch").isCloseTo(0.1f, within(1e-4f));
		assertThat((yaw(one, fade, 0.2f + h) - a0) / h).as("angular velocity just after the switch")
				.isCloseTo(rate, within(0.02f));
		assertThat(yaw(one, fade, 0.7f)).as("arrived").isCloseTo(1.0f, within(1e-5f));
	}

	@Test
	@DisplayName("inertial offset scales by the write's weight and mask")
	void inertialWeightAndMask() {
		NodeTable two = BlendFixture.table(2, 5L);
		GltfAnimation zero = translateXBoth(two, "zero", 0.0f);
		GltfAnimation unit = translateXBoth(two, "unit", 1.0f);
		Crossfade fade = new Crossfade(two);
		fade.play(zero, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(unit, 1.0f, 1.0f, FadeCurve.INERTIAL, 0.0f, 0.0f, false);
		float now = 1.2f;

		AnimationBlend full = new AnimationBlend();
		fade.write(now, full);
		float[] fullOut = two.newScratch();
		BlendEvaluator.evaluate(two, full, fullOut, scratch);
		float offset = fullOut[0] - 1.0f;
		assertThat(offset).isLessThan(-0.1f);
		assertThat(fullOut[NodeTable.TRS_STRIDE] - 1.0f).isCloseTo(offset, within(1e-6f));

		AnimationBlend part = new AnimationBlend();
		part.override(unit, 0.0f, 1.0f);
		fade.write(now, part, 0.5f, BlendMask.nodes(two, "n0"));
		float[] partOut = two.newScratch();
		BlendEvaluator.evaluate(two, part, partOut, scratch);
		assertThat(partOut[0] - 1.0f).as("masked node, half weight").isCloseTo(0.5f * offset, within(1e-5f));
		assertThat(partOut[NodeTable.TRS_STRIDE]).as("unmasked node").isCloseTo(1.0f, within(1e-6f));
	}

	private float yaw(NodeTable table, Crossfade fade, float now) {
		AnimationBlend blend = new AnimationBlend();
		fade.write(now, blend);
		float[] out = table.newScratch();
		BlendEvaluator.evaluate(table, blend, out, scratch);
		return 2.0f * (float) Math.atan2(out[NodeTable.ROTATION + 1], out[NodeTable.ROTATION + 3]);
	}

	private static GltfAnimation rotateY(NodeTable table, String name, float from, float to, float duration) {
		float[][] values = { { 0.0f, (float) Math.sin(from / 2), 0.0f, (float) Math.cos(from / 2) },
				{ 0.0f, (float) Math.sin(to / 2), 0.0f, (float) Math.cos(to / 2) } };
		return new GltfAnimation(name, List.of(new PoseChannel(
				new SphericalLinearInterpolatedChannel(
						new float[] { 0.0f, duration }, values), NodeTable.ROTATION, 4)), duration);
	}

	private static GltfAnimation translateXBoth(NodeTable table, String name, float x) {
		List<PoseDriver> drivers = new ArrayList<>();
		for (int n = 0; n < 2; n++) {
			float[][] values = { { x, table.restTranslation(n, 1), table.restTranslation(n, 2) },
					{ x, table.restTranslation(n, 1), table.restTranslation(n, 2) } };
			drivers.add(new PoseChannel(
					new LinearInterpolatedChannel(new float[] { 0.0f, 1.0f },
							values), n * NodeTable.TRS_STRIDE, 3));
		}
		return new GltfAnimation(name, drivers, 1.0f);
	}

	@Test
	@DisplayName("the key carries the inertial weight, quantized, and loads it back")
	void inertialWeightInKey() {
		Crossfade fade = new Crossfade(table);
		fade.play(idle, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(reload, 0.5f, 0.4f, FadeCurve.INERTIAL);
		AnimationBlend a = new AnimationBlend();
		AnimationBlend b = new AnimationBlend();
		AnimationBlend c = new AnimationBlend();
		fade.write(0.6f, a);
		fade.write(0.6f, b);
		fade.write(0.6f, c);
		b.inertialize(b.inertial(), b.inertialElapsed(), 0.5f, BlendMask.ALL);
		BlendKey ka = new BlendKey().set(this, 0, a, 0.0f, 256).copy();
		BlendKey kb = new BlendKey().set(this, 0, b, 0.0f, 256).copy();
		BlendKey kc = new BlendKey().set(this, 0, c, 0.0f, 256).copy();
		assertThat(ka).isEqualTo(kc);
		assertThat(ka).isNotEqualTo(kb);

		AnimationBlend loaded = new AnimationBlend();
		kb.load(loaded);
		float[] direct = table.newScratch();
		float[] viaKey = table.newScratch();
		BlendEvaluator.evaluate(table, b, direct, scratch);
		BlendEvaluator.evaluate(table, loaded, viaKey, scratch);
		assertThat(poseDelta(table, direct, viaKey)).isLessThan(1e-5f);
		float[] fullWeight = table.newScratch();
		BlendEvaluator.evaluate(table, a, fullWeight, scratch);
		assertThat(poseDelta(table, fullWeight, viaKey)).as("half offset differs from full").isGreaterThan(0.02f);
	}
}
