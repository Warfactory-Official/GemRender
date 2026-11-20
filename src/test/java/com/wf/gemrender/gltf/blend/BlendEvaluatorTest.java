package com.wf.gemrender.gltf.blend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sun.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.MorphFixture;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeSwing;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.gltf.RigFixture;
import java.lang.management.ManagementFactory;

class BlendEvaluatorTest {
	private static final int NODES = 31;
	private final NodeTable table = BlendFixture.table(NODES, 7L);
	private final GltfAnimation walk = BlendFixture.clip(table, "walk", 1.0f, 5, 0.8f, 11L);
	private final GltfAnimation run = BlendFixture.clip(table, "run", 0.6f, 4, 0.8f, 12L);
	private final BlendEvaluator.Scratch scratch = new BlendEvaluator.Scratch();

	private float[] evaluate(AnimationBlend blend) {
		float[] out = table.newScratch();
		BlendEvaluator.evaluate(table, blend, out, scratch);
		return out;
	}

	private float[] sample(GltfAnimation clip, float time) {
		float[] out = table.newScratch();
		if (clip != null) {
			clip.apply(time, out);
		}
		return out;
	}

	private static Quaternionf rotation(float[] state, int slot) {
		int r = slot * NodeTable.TRS_STRIDE + NodeTable.ROTATION;
		return new Quaternionf(state[r], state[r + 1], state[r + 2], state[r + 3]);
	}

	private static float angleBetween(Quaternionf a, Quaternionf b) {
		float dot = Math.abs(a.dot(b)) / (float) Math.sqrt(a.lengthSquared() * b.lengthSquared());
		return 2.0f * (float) Math.acos(Math.min(1.0f, dot));
	}

	@Test
	@DisplayName("one full-weight unmasked layer is bit-identical to applying the clip")
	void singleLayerIsTheClip() {
		AnimationBlend blend = new AnimationBlend();
		blend.override(walk, 0.37f, 1.0f);
		assertThat(evaluate(blend)).containsExactly(sample(walk, 0.37f));
	}

	@Test
	@DisplayName("two layers weighted a and 1-a interpolate translation and scale linearly")
	void weightsInterpolate() {
		float a = 0.3f;
		AnimationBlend blend = new AnimationBlend();
		blend.override(walk, 0.2f, 1.0f - a);
		blend.override(run, 0.5f, a);
		float[] out = evaluate(blend);
		float[] w = sample(walk, 0.2f);
		float[] r = sample(run, 0.5f);
		for (int n = 0; n < NODES; n++) {
			int b = n * NodeTable.TRS_STRIDE;
			for (int k : new int[] { 0, 1, 2, 7, 8, 9 }) {
				assertThat(out[b + k]).as("node %d component %d", n, k)
						.isCloseTo((1 - a) * w[b + k] + a * r[b + k], within(1e-5f));
			}
		}
	}

	@Test
	@DisplayName("weights above 1 in total are normalized per node, below 1 filled with rest")
	void normalizationAndRestFill() {
		AnimationBlend heavy = new AnimationBlend();
		heavy.override(walk, 0.2f, 1.0f);
		heavy.override(run, 0.5f, 1.0f);
		AnimationBlend half = new AnimationBlend();
		half.override(walk, 0.2f, 0.5f);
		half.override(run, 0.5f, 0.5f);
		assertThat(evaluate(heavy)).as("1+1 == 0.5+0.5").containsExactly(evaluate(half));

		AnimationBlend light = new AnimationBlend();
		light.override(walk, 0.2f, 0.25f);
		float[] out = evaluate(light);
		float[] w = sample(walk, 0.2f);
		float[] rest = table.newScratch();
		assertThat(out[3 * NodeTable.TRS_STRIDE]).isCloseTo(0.25f * w[3 * NodeTable.TRS_STRIDE]
				+ 0.75f * rest[3 * NodeTable.TRS_STRIDE], within(1e-6f));
	}

	@Test
	@DisplayName("a subtree mask confines a layer; its complement under it replaces exactly there")
	void masks() {
		BlendMask arm = BlendMask.subtree(table, "n1");
		assertThat(arm.weight(1)).isEqualTo(1.0f);
		assertThat(arm.weight(3)).as("child of n1").isEqualTo(1.0f);
		assertThat(arm.weight(2)).as("sibling").isEqualTo(0.0f);

		AnimationBlend blend = new AnimationBlend();
		blend.override(walk, 0.2f, 1.0f, arm.complement(table));
		blend.override(run, 0.5f, 1.0f, arm);
		float[] out = evaluate(blend);
		float[] w = sample(walk, 0.2f);
		float[] r = sample(run, 0.5f);
		for (int n = 0; n < NODES; n++) {
			float[] expected = arm.weight(n) > 0 ? r : w;
			for (int k = 0; k < NodeTable.TRS_STRIDE; k++) {
				int i = n * NodeTable.TRS_STRIDE + k;
				assertThat(out[i]).as("node %d[%d]", n, k).isCloseTo(expected[i], within(1e-5f));
			}
		}

		assertThatThrownBy(() -> BlendMask.subtree(table, "missing")).isInstanceOf(IllegalArgumentException.class);
		NodeTable other = BlendFixture.table(5, 1L);
		AnimationBlend wrong = new AnimationBlend();
		wrong.override(walk, 0.0f, 0.5f, arm);
		assertThatThrownBy(() -> BlendEvaluator.evaluate(other, wrong, other.newScratch(), scratch))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("drivenBy masks exactly the nodes a clip writes")
	void drivenBy() {
		int slot = 4;
		GltfAnimation swing = GltfAnimation.procedural("swing", NodeSwing.open(table, slot, 1, 0, 0, 1.0f));
		BlendMask mask = BlendMask.drivenBy(table, swing);
		for (int n = 0; n < NODES; n++) {
			assertThat(mask.weight(n)).as("node %d", n).isEqualTo(n == slot ? 1.0f : 0.0f);
		}
	}

	@Test
	@DisplayName("additive: rest reference adds the clip's offset; full weight lands on base * delta")
	void additive() {
		int slot = 5;
		float angle = 0.9f;
		GltfAnimation swing = GltfAnimation.procedural("swing", NodeSwing.open(table, slot, 0, 1, 0, angle));
		AdditiveReference rest = AdditiveReference.rest(table);

		AnimationBlend blend = new AnimationBlend();
		blend.override(walk, 0.3f, 1.0f);
		blend.additive(swing, 1.0f, 1.0f, BlendMask.ALL, rest);
		float[] out = evaluate(blend);
		float[] base = sample(walk, 0.3f);

		Quaternionf restQ = rotation(table.newScratch(), slot);
		Quaternionf swung = rotation(sample(swing, 1.0f), slot);
		Quaternionf delta = new Quaternionf(restQ).conjugate().mul(swung);
		Quaternionf expected = rotation(base, slot).mul(delta);
		assertThat(angleBetween(rotation(out, slot), expected)).isLessThan(1e-3f);
		assertThat(angleBetween(delta, new Quaternionf())).isCloseTo(angle, within(1e-4f));
		for (int n = 0; n < NODES; n++) {
			if (n != slot) {
				assertThat(angleBetween(rotation(out, n), rotation(base, n))).as("node %d untouched", n)
						.isLessThan(1e-3f);
			}
		}

		AnimationBlend halfBlend = new AnimationBlend();
		halfBlend.override(walk, 0.3f, 1.0f);
		halfBlend.additive(swing, 1.0f, 0.5f, BlendMask.ALL, rest);
		float half = angleBetween(rotation(evaluate(halfBlend), slot), rotation(base, slot));
		assertThat(half).as("nlerp(identity, delta, 0.5) is the half angle").isCloseTo(angle / 2, within(1e-3f));

		AnimationBlend self = new AnimationBlend();
		self.override(walk, 0.3f, 1.0f);
		self.additive(run, 0.25f, 1.0f, BlendMask.ALL, AdditiveReference.frame(table, run, 0.25f));
		float[] unchanged = evaluate(self);
		for (int i = 0; i < unchanged.length; i++) {
			assertThat(unchanged[i]).as("sample == reference => no-op, [%d]", i).isCloseTo(base[i], within(1e-5f));
		}
	}

	@Test
	@DisplayName("additive translation adds, scale multiplies")
	void additiveTranslationAndScale() {
		AdditiveReference ref = AdditiveReference.frame(table, run, 0.0f);
		AnimationBlend blend = new AnimationBlend();
		blend.override(walk, 0.4f, 1.0f);
		blend.additive(run, 0.3f, 0.5f, BlendMask.nodes(table, "n2"), ref);
		float[] out = evaluate(blend);
		float[] base = sample(walk, 0.4f);
		float[] s = sample(run, 0.3f);
		float[] r = ref.state();
		int b = 2 * NodeTable.TRS_STRIDE;
		for (int k = 0; k < 3; k++) {
			assertThat(out[b + k]).isCloseTo(base[b + k] + 0.5f * (s[b + k] - r[b + k]), within(1e-6f));
			assertThat(out[b + 7 + k]).isCloseTo(base[b + 7 + k] * (1 + 0.5f * (s[b + 7 + k] / r[b + 7 + k] - 1)),
					within(1e-6f));
		}
		int other = 6 * NodeTable.TRS_STRIDE;
		assertThat(out[other]).as("outside the mask").isEqualTo(base[other]);
	}

	@Test
	@DisplayName("hemisphere: q and -q blend to q, not to a shrunken or flipped quaternion")
	void hemisphere() {
		float angle = 2.8f;
		NodeTable tiny = BlendFixture.table(1, 3L);
		Quaternionf a = new Quaternionf().rotateY(angle / 2);
		Quaternionf b = new Quaternionf().rotateY(-angle / 2);
		GltfAnimation pa = pinned(tiny, a, false);
		GltfAnimation pb = pinned(tiny, b, true);
		AnimationBlend blend = new AnimationBlend();
		blend.override(pa, 0.0f, 0.5f);
		blend.override(pb, 0.0f, 0.5f);
		float[] out = new float[tiny.scratchFloats()];
		BlendEvaluator.evaluate(tiny, blend, out, scratch);
		Quaternionf mid = rotation(out, 0);
		assertThat((float) Math.sqrt(mid.lengthSquared())).isCloseTo(1.0f, within(1e-5f));
		assertThat(angleBetween(mid, new Quaternionf())).as("short-arc midpoint of +-%s about Y", angle / 2)
				.isLessThan(1e-4f);

		AnimationBlend same = new AnimationBlend();
		same.override(pinned(tiny, a, false), 0.0f, 0.5f);
		same.override(pinned(tiny, a, true), 0.0f, 0.5f);
		BlendEvaluator.evaluate(tiny, same, out, scratch);
		assertThat(angleBetween(rotation(out, 0), a)).as("q and -q").isLessThan(1e-4f);
	}

	private static GltfAnimation pinned(NodeTable table, Quaternionf q, boolean negate) {
		float s = negate ? -1.0f : 1.0f;
		PoseDriver driver = new PoseDriver() {
			@Override
			public void apply(float time, float[] scratch) {
				int r = NodeRotation.offsetOf(table, 0);
				scratch[r] = s * q.x;
				scratch[r + 1] = s * q.y;
				scratch[r + 2] = s * q.z;
				scratch[r + 3] = s * q.w;
			}

			@Override
			public float cycleSeconds() {
				return 0.0f;
			}
		};
		return GltfAnimation.procedural("pin" + negate, driver);
	}

	@Test
	@DisplayName("morph weights blend linearly and additively, masked by their node")
	void morphWeights() {
		NodeTable morphTable = MorphFixture.layout().nodeTable();
		GltfAnimation pump = MorphFixture.animation();
		int base = morphTable.weightBase(MorphFixture.NODE_PUMP);
		assertThat(base).isNotNegative();

		float[] rest = morphTable.newScratch();
		float[] mid = morphTable.newScratch();
		pump.apply(MorphFixture.MID_CLIP, mid);

		AnimationBlend blend = new AnimationBlend();
		blend.override(pump, MorphFixture.MID_CLIP, 0.4f);
		float[] out = new float[morphTable.scratchFloats()];
		BlendEvaluator.evaluate(morphTable, blend, out, scratch);
		for (int k = 0; k < morphTable.weightCount(MorphFixture.NODE_PUMP); k++) {
			assertThat(out[base + k]).isCloseTo(0.4f * mid[base + k] + 0.6f * rest[base + k], within(1e-6f));
		}

		AnimationBlend added = new AnimationBlend();
		added.override(null, 0.0f, 1.0f);
		added.additive(pump, MorphFixture.MID_CLIP, 0.5f, BlendMask.ALL, AdditiveReference.rest(morphTable));
		BlendEvaluator.evaluate(morphTable, added, out, scratch);
		for (int k = 0; k < morphTable.weightCount(MorphFixture.NODE_PUMP); k++) {
			assertThat(out[base + k]).isCloseTo(rest[base + k] + 0.5f * (mid[base + k] - rest[base + k]),
					within(1e-6f));
		}
	}

	@Test
	@DisplayName("a sync group runs the lighter layer at the heavier layer's phase fraction")
	void syncGroups() {
		AnimationBlend blend = new AnimationBlend();
		int w = blend.override(walk, 0.25f, 0.7f);
		int r = blend.override(run, 0.0f, 0.3f);
		blend.sync(w, 1).sync(r, 1);
		blend.resolve();
		assertThat(blend.resolvedTime(r)).isCloseTo(0.25f * run.duration(), within(1e-6f));
		assertThat(blend.resolvedTime(w)).isEqualTo(0.25f);
	}

	@Test
	@DisplayName("skinned rig: blend of the rig clip at two instants matches the golden model")
	void rigAgainstReference() {
		NodeTable rig = RigFixture.layout().nodeTable();
		GltfAnimation curl = RigFixture.animation();
		AnimationBlend blend = new AnimationBlend();
		blend.override(curl, 0.3f, 0.6f);
		blend.override(curl, 1.4f, 0.6f, BlendMask.subtree(rig, rig.nodeName(RigFixture.NODE_BONE0)));
		blend.additive(curl, 1.0f, 0.5f, BlendMask.ALL, AdditiveReference.frame(rig, curl, 0.0f));
		float[] out = new float[rig.scratchFloats()];
		BlendEvaluator.evaluate(rig, blend, out, scratch);
		float[] golden = BlendReference.evaluate(rig, layers(blend));
		assertThat(maxDifference(out, golden)).isLessThan(1e-5f);
	}

	@Test
	@DisplayName("random blends over 50 keyframed nodes match the double-precision golden model")
	void goldenRandom() {
		NodeTable big = BlendFixture.table(50, 21L);
		GltfAnimation[] clips = new GltfAnimation[4];
		for (int i = 0; i < clips.length; i++) {
			clips[i] = BlendFixture.clip(big, "c" + i, 0.5f + i * 0.3f, 3 + i, 1.2f + i, 100L + i);
		}
		Random random = new Random(5L);
		float worst = 0.0f;
		for (int trial = 0; trial < 300; trial++) {
			AnimationBlend blend = new AnimationBlend();
			int layers = 1 + random.nextInt(5);
			for (int l = 0; l < layers; l++) {
				GltfAnimation clip = clips[random.nextInt(clips.length)];
				float time = random.nextFloat() * 2.0f;
				float weight = random.nextFloat() * 1.2f;
				BlendMask mask = switch (random.nextInt(3)) {
					case 0 -> BlendMask.ALL;
					case 1 -> BlendMask.subtree(big, "n" + random.nextInt(50));
					default -> {
						float[] w = new float[50];
						for (int k = 0; k < w.length; k++) {
							w[k] = random.nextFloat();
						}
						yield BlendMask.weights(big, w);
					}
				};
				if (l > 0 && random.nextInt(3) == 0) {
					blend.additive(clip, time, weight, mask,
							random.nextBoolean() ? AdditiveReference.rest(big) : AdditiveReference.frame(big, clip, 0.0f));
				} else {
					blend.override(clip, time, weight, mask);
				}
			}
			float[] out = big.newScratch();
			BlendEvaluator.evaluate(big, blend, out, scratch);
			worst = Math.max(worst, maxDifference(out, BlendReference.evaluate(big, layers(blend))));
		}
		assertThat(worst).as("max |impl - golden| over 300 random blends").isLessThan(2e-4f);
	}

	static List<BlendReference.Layer> layers(AnimationBlend blend) {
		blend.resolve();
		List<BlendReference.Layer> out = new ArrayList<>();
		for (int i = 0; i < blend.layerCount(); i++) {
			out.add(new BlendReference.Layer(blend.clip(i), blend.resolvedTime(i), blend.weight(i), blend.mask(i),
					blend.mode(i), blend.reference(i)));
		}
		return out;
	}

	static float maxDifference(float[] a, float[] b) {
		float worst = 0.0f;
		for (int i = 0; i < a.length; i++) {
			worst = Math.max(worst, Math.abs(a[i] - b[i]));
		}
		return worst;
	}

	@Test
	@DisplayName("steady-state evaluation allocates nothing")
	void noAllocation() {
		ThreadMXBean bean =
				(ThreadMXBean) ManagementFactory.getThreadMXBean();
		AnimationBlend blend = new AnimationBlend();
		AdditiveReference rest = AdditiveReference.rest(table);
		BlendMask arm = BlendMask.subtree(table, "n1");
		Crossfade fade = new Crossfade(table);
		fade.play(walk, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(run, 0.1f, 10.0f, FadeCurve.SMOOTH);
		float[] out = table.newScratch();
		for (int i = 0; i < 20_000; i++) {
			frame(blend, fade, arm, rest, out, i);
		}
		long before = bean.getCurrentThreadAllocatedBytes();
		for (int i = 0; i < 1_000; i++) {
			frame(blend, fade, arm, rest, out, i);
		}
		long allocated = bean.getCurrentThreadAllocatedBytes() - before;
		assertThat(allocated).as("bytes over 1000 frames").isLessThan(1024L);
	}

	private void frame(AnimationBlend blend, Crossfade fade, BlendMask arm, AdditiveReference rest, float[] out,
			int i) {
		blend.clear();
		fade.write(0.2f + i * 1e-4f, blend);
		blend.additive(walk, i * 0.01f, 0.5f, arm, rest);
		BlendEvaluator.evaluate(table, blend, out, scratch);
	}
	@Test
	@DisplayName("mask, additive reference and crossfade refuse another table of the same size")
	void boundToTheirTable() {
		NodeTable twin = BlendFixture.table(NODES, 8L);
		GltfAnimation twinWalk = BlendFixture.clip(twin, "walk", 1.0f, 5, 0.8f, 11L);

		AnimationBlend masked = new AnimationBlend();
		masked.override(walk, 0.0f, 0.5f, BlendMask.subtree(twin, "n1"));
		assertThatThrownBy(() -> evaluate(masked)).isInstanceOf(IllegalArgumentException.class);

		AnimationBlend additive = new AnimationBlend();
		additive.additive(walk, 0.2f, 1.0f, BlendMask.ALL, AdditiveReference.rest(twin));
		assertThatThrownBy(() -> evaluate(additive)).isInstanceOf(IllegalArgumentException.class);
		assertThat(AdditiveReference.rest(twin)).isNotEqualTo(AdditiveReference.rest(table));
		assertThat(BlendMask.subtree(twin, "n1")).isNotEqualTo(BlendMask.subtree(table, "n1"));

		Crossfade fade = new Crossfade(twin);
		fade.play(twinWalk, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(null, 0.5f, 0.3f, FadeCurve.INERTIAL);
		AnimationBlend inertial = new AnimationBlend();
		fade.write(0.6f, inertial);
		assertThatThrownBy(() -> evaluate(inertial)).isInstanceOf(IllegalArgumentException.class);

		Crossfade own = new Crossfade(table);
		own.play(walk, 0.0f, 0.0f, FadeCurve.LINEAR);
		own.play(null, 0.5f, 0.3f, FadeCurve.INERTIAL);
		AnimationBlend ownMaskedWrong = new AnimationBlend();
		own.write(0.6f, ownMaskedWrong);
		ownMaskedWrong.inertialize(ownMaskedWrong.inertial(), ownMaskedWrong.inertialElapsed(), 1.0f,
				BlendMask.subtree(twin, "n1"));
		assertThatThrownBy(() -> evaluate(ownMaskedWrong)).as("inertial mask").isInstanceOf(
				IllegalArgumentException.class);
	}

	@Test
	@DisplayName("morph weight offsets map to their node; past the end is -1")
	void morphSlots() {
		NodeTable morphs = BlendFixture.morphTable(9, 3, 2L);
		for (int n = 0; n < morphs.nodeCount(); n++) {
			for (int k = 0; k < 3; k++) {
				assertThat(morphs.slotOfOffset(morphs.weightBase(n) + k)).isEqualTo(n);
			}
		}
		assertThat(morphs.slotOfOffset(morphs.scratchFloats())).isEqualTo(-1);
		assertThat(morphs.slotOfOffset(-1)).isEqualTo(-1);
	}
}
