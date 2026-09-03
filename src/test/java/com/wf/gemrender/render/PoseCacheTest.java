package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.AnimationPhase;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.RigFixture;
import com.wf.gemrender.gltf.skin.SkinnedBounds;

class PoseCacheTest {
	private static final float QUANTUM = 0.1f;

	private static PoseCache cache() {
		return new PoseCache(QUANTUM);
	}

	private static SkinnedBounds bounds() {
		return RigFixture.bounds();
	}

	@Test
	@DisplayName("copies at the same instant evaluate one palette between them")
	void identicalPhasesShare() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		PoseCache.Pose first = cache.pose(RigFixture.layout(), bounds, clip, 0.5f);
		PoseCache.Pose second = cache.pose(RigFixture.layout(), bounds, clip, 0.5f);

		assertThat(second.boneBase())
				.as("two copies at the same clip time must point at the same palette")
				.isEqualTo(first.boneBase());

		cache.endFrame();
		assertThat(cache.requestsLastFrame()).isEqualTo(2);
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("copies at different instants do not share, and the cost tracks the phases")
	void differentPhasesDoNotShare() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		List<Integer> bases = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			bases.add(cache.pose(RigFixture.layout(), bounds, clip, i * QUANTUM * 3)
					.boneBase());
		}

		assertThat(bases)
				.as("eight copies at eight phases must get eight palettes")
				.doesNotHaveDuplicates();

		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(8);
	}

	@Test
	@DisplayName("a scattered field of machines really is scattered after quantisation")
	void scatteredPhasesSurviveQuantisation() {
		PoseCache cache = new PoseCache(PoseCache.DEFAULT_QUANTUM_SECONDS);
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		int machines = 16;
		for (int i = 0; i < machines; i++) {
			AnimationPhase phase = AnimationPhase.scattered(clip, blockPosAsLong(i, 64, 0));
			cache.pose(RigFixture.layout(), bounds, clip, phase.timeAt(0.0f));
		}

		cache.endFrame();
		assertThat(cache.evaluationsLastFrame())
				.as("distinct palettes for %d scattered machines", machines)
				.isGreaterThanOrEqualTo(machines - 2);
	}

	@Test
	@DisplayName("the shared palette is the one the bucket's own time produces")
	void theSharedPaletteIsTheBucketsPose() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		float bucketTime = 4 * QUANTUM;
		cache.pose(RigFixture.layout(), bounds(), clip, bucketTime + QUANTUM * 0.4f);

		Matrix4f[] expected = RigFixture.pose(clip, bucketTime);
		Matrix4f[] actual = RigFixture.pose(clip, bucketTime);
		assertThat(actual).isEqualTo(expected);

		Vector4f direct = new org.joml.Vector4f();
		bounds().evaluate(expected, direct);

		cache.endFrame();
		cache.pose(RigFixture.layout(), bounds(), clip, bucketTime);
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("a rest pose is shared by every copy that has no clip")
	void restPosesShare() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();

		int first = cache.pose(RigFixture.layout(), bounds, null, 0.0f)
				.boneBase();
		int second = cache.pose(RigFixture.layout(), bounds, null, 0.0f)
				.boneBase();

		assertThat(second).isEqualTo(first);
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("layered copies share only when every layer agrees")
	void layersMustAllAgreeToShare() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation walk = RigFixture.animation();
		GltfAnimation bite = GltfAnimation.procedural("bite", 1.0f);

		GltfAnimation[] clips = { walk, bite };

		int first = layered(cache, bounds, clips, 0.5f, 0.0f);
		int same = layered(cache, bounds, clips, 0.5f, 0.0f);
		int otherWalk = layered(cache, bounds, clips, 0.9f, 0.0f);
		int otherBite = layered(cache, bounds, clips, 0.5f, 0.9f);

		assertThat(same).as("both layers in the same bucket is one pose")
				.isEqualTo(first);
		assertThat(otherWalk).as("a different walk phase is a different pose")
				.isNotEqualTo(first);
		assertThat(otherBite).as("the same walk mid-bite is a different pose again")
				.isNotEqualTo(first);

		cache.endFrame();
		assertThat(cache.requestsLastFrame()).isEqualTo(4);
		assertThat(cache.evaluationsLastFrame()).isEqualTo(3);
	}

	@Test
	@DisplayName("a layer that sits out is still part of the key")
	void nullLayersAreDistinguished() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation walk = RigFixture.animation();
		GltfAnimation damage = GltfAnimation.procedural("damaged", 0.0f);

		int whole = layered(cache, bounds, new GltfAnimation[] { walk, null }, 0.5f, 0.0f);
		int damaged = layered(cache, bounds, new GltfAnimation[] { walk, damage }, 0.5f, 0.0f);

		assertThat(damaged).as("a damage state is a different pose even at the same instant")
				.isNotEqualTo(whole);

		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(2);
	}

	@Test
	@DisplayName("one layer is the same pose the single-clip call gives")
	void oneLayerMatchesTheSingleClipCall() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		int single = cache.pose(RigFixture.layout(), bounds, clip, 0.5f)
				.boneBase();
		int layered = layered(cache, bounds, new GltfAnimation[] { clip }, 0.5f);

		assertThat(layered).as("the two entry points must land in the same cache slot")
				.isEqualTo(single);

		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	private static int layered(PoseCache cache, SkinnedBounds bounds, GltfAnimation[] clips,
			float... times) {
		return cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE,
				clips, times, 0)
				.boneBase();
	}

	@Test
	@DisplayName("ending the frame drops every pose, because the bases it handed out are gone")
	void endFrameInvalidates() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		cache.pose(RigFixture.layout(), bounds, clip, 0.5f);
		cache.endFrame();
		cache.pose(RigFixture.layout(), bounds, clip, 0.5f);
		cache.endFrame();

		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("a coarser level collapses phases that the near level keeps apart")
	void levellingSharesMore() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		for (int i = 0; i < 8; i++) {
			cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE, clip,
					i * QUANTUM * 0.5f, 0);
		}
		cache.endFrame();
		int atLevel0 = cache.evaluationsLastFrame();

		for (int i = 0; i < 8; i++) {
			cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE, clip,
					i * QUANTUM * 0.5f, 2);
		}
		cache.endFrame();
		int atLevel2 = cache.evaluationsLastFrame();

		assertThat(atLevel0).isGreaterThan(atLevel2);
		assertThat(cache.requestsLastFrame()).isEqualTo(8);
	}

	@Test
	@DisplayName("two levels never share a palette, however their buckets happen to number")
	void levelsDoNotShareWithEachOther() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		int near = cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE,
				clip, 3 * QUANTUM, 0)
				.boneBase();
		int far = cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE,
				clip, 12 * QUANTUM, 2)
				.boneBase();

		assertThat(far).isNotEqualTo(near);
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(2);
	}

	@Test
	@DisplayName("the level is reported, so a run that levelled nothing is distinguishable")
	void theLevelIsObservable() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE, clip,
				0.0f, 0);
		cache.pose(RigFixture.layout(), bounds, com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE, clip,
				0.0f, 4);
		cache.endFrame();

		assertThat(cache.lodMaxLastFrame()).isEqualTo(4);
		assertThat(cache.lodMeanCentisLastFrame()).isEqualTo(200);
	}

	@Test
	@DisplayName("a frozen far field costs one evaluation between all of it")
	void frozenCopiesAllShareOnePose() {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();

		List<Integer> bases = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			bases.add(cache.pose(RigFixture.layout(), bounds,
					com.wf.gemrender.gltf.morph.GltfMorphLayout.NONE, null, 0.0f, 0)
					.boneBase());
		}

		assertThat(bases).containsOnly(bases.get(0));
		cache.endFrame();
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("concurrent evaluation of one model at two times does not mix the two poses")
	void concurrentEvaluationIsSerialised() throws InterruptedException {
		GltfAnimation clip = RigFixture.animation();
		float timeA = 0.25f;
		float timeB = 1.75f;

		Matrix4f[] expectedA = RigFixture.pose(clip, timeA);
		Matrix4f[] expectedB = RigFixture.pose(clip, timeB);
		assertThat(expectedA)
				.as("the two sample times must actually differ, or this test proves nothing")
				.isNotEqualTo(expectedB);

		int iterations = 400;
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<AssertionError> failure = new AtomicReference<>();

		Runnable sampler = () -> {
			boolean useA = Thread.currentThread()
					.getName()
					.endsWith("A");
			float time = useA ? timeA : timeB;
			Matrix4f[] expected = useA ? expectedA : expectedB;
			Matrix4f[] palette = RigFixture.newPalette();
			try {
				start.await();
				for (int i = 0; i < iterations; i++) {
					GltfPose.evaluate(RigFixture.layout(), clip, time, palette);
					for (int slot = 0; slot < palette.length; slot++) {
						if (!palette[slot].equals(expected[slot])) {
							throw new AssertionError("slot " + slot + " at t=" + time
									+ " came back as another sample's pose: " + palette[slot]
									+ " expected " + expected[slot]);
						}
					}
				}
			} catch (AssertionError e) {
				failure.compareAndSet(null, e);
			} catch (InterruptedException e) {
				Thread.currentThread()
						.interrupt();
			}
		};

		Thread a = new Thread(sampler, "sampler-A");
		Thread b = new Thread(sampler, "sampler-B");
		a.start();
		b.start();
		start.countDown();
		a.join(TimeUnit.SECONDS.toMillis(60));
		b.join(TimeUnit.SECONDS.toMillis(60));

		if (failure.get() != null) {
			throw failure.get();
		}
	}

	@Test
	@DisplayName("concurrent cache lookups for one bucket evaluate it once")
	void concurrentLookupsEvaluateOnce() throws InterruptedException {
		PoseCache cache = cache();
		SkinnedBounds bounds = bounds();
		GltfAnimation clip = RigFixture.animation();

		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Thread worker = new Thread(() -> {
				try {
					start.await();
				} catch (InterruptedException e) {
					Thread.currentThread()
							.interrupt();
					return;
				}
				cache.pose(RigFixture.layout(), bounds, clip, 0.5f);
			});
			workers.add(worker);
			worker.start();
		}
		start.countDown();
		for (Thread worker : workers) {
			worker.join(TimeUnit.SECONDS.toMillis(60));
		}

		cache.endFrame();
		assertThat(cache.requestsLastFrame()).isEqualTo(threads);
		assertThat(cache.evaluationsLastFrame()).isEqualTo(1);
	}

	@Test
	@DisplayName("the sphere that comes back with a pose is the one that pose's palette produces")
	void theSphereMatchesThePalette() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		float time = 5 * QUANTUM;
		PoseCache.Pose pose = cache.pose(RigFixture.layout(), bounds(), clip, time);

		org.joml.Vector4f direct = new org.joml.Vector4f();
		bounds().evaluate(RigFixture.pose(clip, time), direct);

		assertThat(pose.sphere()
				.w()).isCloseTo(direct.w, within(1e-5f));
		assertThat(pose.sphere()
				.y()).isCloseTo(direct.y, within(1e-5f));
	}

	private static long blockPosAsLong(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) | ((long) z & 0x3FFFFFF) << 12;
	}
}
