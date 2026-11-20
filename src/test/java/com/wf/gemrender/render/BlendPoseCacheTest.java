package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.ThreadMXBean;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.MatrixScalar;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.RigFixture;
import com.wf.gemrender.gltf.blend.AdditiveReference;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.BlendMask;
import com.wf.gemrender.gltf.blend.Crossfade;
import com.wf.gemrender.gltf.blend.FadeCurve;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import java.lang.management.ManagementFactory;

class BlendPoseCacheTest {
	private static final float QUANTUM = 1.0f / 128.0f;

	private final GltfAnimation curl = RigFixture.animation();
	private final SkinnedBounds bounds = RigFixture.bounds();

	private static AnimationBlend fade(GltfAnimation clip, float weight) {
		AnimationBlend blend = new AnimationBlend();
		blend.override(clip, 0.25f, 1.0f - weight);
		blend.override(clip, 1.5f, weight);
		return blend;
	}

	@Test
	@DisplayName("equal blends share one evaluation; a different weight does not")
	void sharing() {
		PoseCache cache = new PoseCache(QUANTUM);
		PoseCache.Pose a = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f), 0);
		PoseCache.Pose b = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f), 0);
		PoseCache.Pose c = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.25f), 0);
		assertThat(b.boneBase()).isEqualTo(a.boneBase());
		assertThat(c.boneBase()).isNotEqualTo(a.boneBase());
		cache.endFrame();
		assertThat(cache.requestsLastFrame()).isEqualTo(3);
		assertThat(cache.evaluationsLastFrame()).isEqualTo(2);
	}

	@Test
	@DisplayName("weights inside one quantum share at lod 0, and lod coarsens the quantum")
	void weightQuantum() {
		PoseCache cache = new PoseCache(QUANTUM);
		float step = 1.0f / PoseCache.weightSteps(0);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f), 0);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f + step * 0.3f), 0);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f + step * 3), 0);
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(2);

		assertThat(PoseCache.weightSteps(3)).isEqualTo(32);
		float coarse = 1.0f / PoseCache.weightSteps(3);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f), 3);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, fade(curl, 0.5f + coarse * 0.3f), 3);
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("a one-clip blend is the single-clip key: it shares with the legacy call")
	void singleClipSharesWithLegacy() {
		PoseCache cache = new PoseCache(QUANTUM);
		PoseCache.Pose legacy = cache.pose(RigFixture.layout(), bounds, curl, 0.5f);
		AnimationBlend blend = new AnimationBlend();
		blend.override(curl, 0.5f, 1.0f);
		PoseCache.Pose blended = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
		assertThat(blended.boneBase()).isEqualTo(legacy.boneBase());
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("zero-weight layers drop out: a finished fade is the single-clip key again")
	void zeroWeightLayersDropOut() {
		PoseCache cache = new PoseCache(QUANTUM);
		PoseCache.Pose legacy = cache.pose(RigFixture.layout(), bounds, curl, 0.5f);
		AnimationBlend blend = new AnimationBlend();
		blend.override(curl, 1.5f, 0.0f);
		blend.override(curl, 0.5f, 1.0f);
		blend.additive(curl, 1.0f, 0.0f, BlendMask.ALL,
				AdditiveReference.rest(RigFixture.layout().nodeTable()));
		PoseCache.Pose blended = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
		assertThat(blended.boneBase()).isEqualTo(legacy.boneBase());
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("the cached palette is the quantized blend's own palette")
	void paletteMatchesDirectEvaluation() {
		PoseCache cache = new PoseCache(QUANTUM);
		AnimationBlend blend = fade(curl, 0.5f);
		blend.additive(curl, 1.0f, 0.5f, BlendMask.ALL,
				AdditiveReference.rest(RigFixture.layout().nodeTable()));
		PoseCache.Pose pose = cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);

		Matrix4f[] direct = new Matrix4f[RigFixture.layout().size()];
		for (int i = 0; i < direct.length; i++) {
			direct[i] = new Matrix4f();
		}
		GltfPose.evaluate(RigFixture.layout(), blend, direct, GltfMorphLayout.NONE, null, new GltfPose.Scratch());
		for (int slot = 0; slot < RigFixture.NODE_COUNT; slot++) {
			assertThat(MatrixScalar.maxDifference(pose.boneMatrix(slot, new Matrix4f()), direct[slot]))
					.as("slot %d", slot).isLessThan(1e-3f);
		}
		cache.endFrame();
	}

	@Test
	@DisplayName("a blended lookup that hits allocates nothing")
	void hitAllocatesNothing() {
		ThreadMXBean bean =
				(ThreadMXBean) ManagementFactory.getThreadMXBean();
		PoseCache cache = new PoseCache(QUANTUM);
		AnimationBlend blend = fade(curl, 0.5f);
		for (int i = 0; i < 20_000; i++) {
			cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
		}
		long before = bean.getCurrentThreadAllocatedBytes();
		for (int i = 0; i < 1_000; i++) {
			cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
		}
		assertThat(bean.getCurrentThreadAllocatedBytes() - before).as("bytes over 1000 hits").isLessThan(1024L);
		cache.endFrame();
	}

	@Test
	@DisplayName("an inertialized pose is private to its instance")
	void inertialIsPrivate() {
		PoseCache cache = new PoseCache(QUANTUM);
		NodeTable table = RigFixture.layout().nodeTable();
		Crossfade one = new Crossfade(table);
		Crossfade two = new Crossfade(table);
		for (Crossfade fade : new Crossfade[] { one, two }) {
			fade.play(curl, 0.0f, 0.0f, FadeCurve.LINEAR);
			fade.play(null, 0.5f, 0.3f, FadeCurve.INERTIAL);
		}
		AnimationBlend a = new AnimationBlend();
		AnimationBlend b = new AnimationBlend();
		one.write(0.6f, a);
		two.write(0.6f, b);
		assertThat(a.isPrivate()).isTrue();
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, a, 0);
		cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, b, 0);
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(2);
	}
	@Test
	@DisplayName("an inertializing instance allocates nothing per frame once warm")
	void inertialAllocatesNothing() {
		ThreadMXBean bean =
				(ThreadMXBean) ManagementFactory.getThreadMXBean();
		PoseCache cache = new PoseCache(QUANTUM);
		Crossfade fade = new Crossfade(RigFixture.layout().nodeTable());
		fade.play(curl, 0.0f, 0.0f, FadeCurve.LINEAR);
		fade.play(null, 0.5f, 1000.0f, FadeCurve.INERTIAL);
		AnimationBlend blend = new AnimationBlend();
		float now = 0.5f;
		long bytes = 0;
		for (int frame = 0; frame < 3_000; frame++) {
			now += 1.0f / 60.0f;
			fade.write(now, blend.clear());
			long before = bean.getCurrentThreadAllocatedBytes();
			cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
			cache.pose(RigFixture.layout(), bounds, GltfMorphLayout.NONE, blend, 0);
			if (frame >= 2_000) {
				bytes += bean.getCurrentThreadAllocatedBytes() - before;
			}
			cache.endFrame();
		}
		assertThat(blend.isPrivate()).isTrue();
		assertThat(bytes).as("bytes over 1000 frames, 2 private poses each").isLessThan(4096L);
	}
}
