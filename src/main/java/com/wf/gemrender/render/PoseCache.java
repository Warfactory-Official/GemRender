package com.wf.gemrender.render;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;

/** Shares one palette evaluation between instances that land in the same quantised instant. */
public final class PoseCache {
	public static final float DEFAULT_QUANTUM_SECONDS = 1.0f / 128.0f;

	private static final PoseCache INSTANCE = new PoseCache(
			Float.parseFloat(System.getProperty("gemrender.posequantum",
					Float.toString(DEFAULT_QUANTUM_SECONDS))));

	public record Pose(int boneBase, int morphBase, Vector4fc sphere) {
	}

	/**
	 * Mutable so a lookup costs nothing: {@link #probe} is set and handed to {@code get}, which never
	 * keeps it, and only a miss copies one to store.
	 */
	private static final class Key {
		private GltfPaletteLayout layout;
		private GltfAnimation[] clips;
		private int[] buckets;
		private int layers;
		private int lod;

		private Key set(GltfPaletteLayout layout, GltfAnimation[] clips, int[] buckets, int layers, int lod) {
			this.layout = layout;
			this.clips = clips;
			this.buckets = buckets;
			this.layers = layers;
			this.lod = lod;
			return this;
		}

		private Key copy() {
			return new Key().set(layout, Arrays.copyOf(clips, layers), Arrays.copyOf(buckets, layers), layers,
					lod);
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Key that) || layers != that.layers || lod != that.lod
					|| layout != that.layout) {
				return false;
			}
			for (int layer = 0; layer < layers; layer++) {
				if (buckets[layer] != that.buckets[layer]
						|| !Objects.equals(clips[layer], that.clips[layer])) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int hashCode() {
			int hash = System.identityHashCode(layout) * 31 + lod;
			for (int layer = 0; layer < layers; layer++) {
				hash = (hash * 31 + Objects.hashCode(clips[layer])) * 31 + buckets[layer];
			}
			return hash;
		}
	}

	private static final class Local {
		private final Key probe = new Key();
		private final GltfPose.Scratch scratch = new GltfPose.Scratch();

		/** Reused so the single-layer call, which is nearly all of them, allocates nothing on a hit. */
		private final GltfAnimation[] oneClip = new GltfAnimation[1];
		private final int[] oneBucket = new int[1];

		private int[] buckets = new int[4];
		private float[] times = new float[4];

		private int[] buckets(int layers) {
			if (buckets.length < layers) {
				buckets = new int[layers];
			}
			return buckets;
		}

		private float[] times(int layers) {
			if (times.length < layers) {
				times = new float[layers];
			}
			return times;
		}
	}

	private final float quantumSeconds;
	private final Map<Key, Pose> poses = new ConcurrentHashMap<>();

	private final ThreadLocal<Local> local = ThreadLocal.withInitial(Local::new);

	private final AtomicInteger requests = new AtomicInteger();
	private final AtomicInteger evaluations = new AtomicInteger();

	private final AtomicInteger lodSum = new AtomicInteger();
	private final AtomicInteger lodMax = new AtomicInteger();

	private volatile int requestsLastFrame;
	private volatile int evaluationsLastFrame;
	private volatile int lodSumLastFrame;
	private volatile int lodMaxLastFrame;

	PoseCache(float quantumSeconds) {
		this.quantumSeconds = quantumSeconds;
	}

	public static PoseCache getInstance() {
		return INSTANCE;
	}

	public float quantumSeconds() {
		return quantumSeconds;
	}

	public Pose pose(GltfPaletteLayout layout, SkinnedBounds bounds, GltfAnimation clip, float timeSeconds) {
		return pose(layout, bounds, GltfMorphLayout.NONE, clip, timeSeconds, 0);
	}

	public Pose pose(GltfPaletteLayout layout, SkinnedBounds bounds, GltfMorphLayout morphs,
			GltfAnimation clip, float timeSeconds) {
		return pose(layout, bounds, morphs, clip, timeSeconds, 0);
	}

	/**
	 * The only supported way to get a {@code boneBase}. Keyed on layout, clip, lod and quantised time.
	 *
	 * <p>Two calls collide, and so cost one evaluation between them, when all four agree: the same
	 * {@link GltfPaletteLayout} by identity, {@linkplain GltfAnimation#equals equal} clips, the same lod,
	 * and times that round into one bucket of {@link #quantumSeconds(int)}. Anything else is a miss and
	 * costs a full palette evaluation, so <b>the caller decides the cost of the frame by choosing what it
	 * passes as the time</b>. Sharing lasts a frame; {@link #endFrame()} clears the table.
	 *
	 * <p>That makes a continuous per-instance clock the one thing to avoid. A value integrated per
	 * instance -- elapsed time in a state, distance travelled, an accumulating angle -- lands every
	 * instance in a bucket of its own, and a hundred copies cost a hundred evaluations instead of one.
	 * Where instances must differ, draw their times from a fixed set rather than a continuum; see
	 * {@link com.wf.gemrender.gltf.AnimationPhase#snap} for the counting argument. Whether that was achieved is observable:
	 * {@link #evaluationsLastFrame()} over {@link #requestsLastFrame()} is the sharing actually obtained,
	 * and it reaching 1 means none.
	 */
	public Pose pose(GltfPaletteLayout layout, SkinnedBounds bounds, GltfMorphLayout morphs,
			GltfAnimation clip, float timeSeconds, int lod) {
		Local thread = local.get();
		thread.oneClip[0] = clip;
		thread.oneBucket[0] = bucket(timeSeconds, lod);
		return pose(layout, bounds, morphs, thread.oneClip, thread.oneBucket, 1, lod, thread);
	}

	/**
	 * Several clips at once, each at its own instant, layered in order onto one pose.
	 *
	 * <p>For a copy whose parts answer to different things: a mob whose legs run on distance travelled
	 * and whose jaws run on an attack timer cannot express both on one clock, and merging the two into
	 * one clip would need a clip per pair of instants. Layered, the two are separate keys, so a swarm
	 * mid-stride and a swarm mid-bite cost what each costs on its own rather than the product.
	 *
	 * <p>Everything {@link #pose(GltfPaletteLayout, SkinnedBounds, GltfMorphLayout, GltfAnimation, float,
	 * int) the single-clip form} says about sharing still holds, once per layer: two calls collide only
	 * when <em>every</em> layer agrees, so an extra layer can only ever split the table further. Adding a
	 * layer that hardly ever varies -- a damage state, a variant -- is close to free; adding one that
	 * varies per instance is what makes a crowd cost a pose each.
	 *
	 * <p>{@code clips} and {@code times} must be the same length, and a layer may be null to sit out.
	 */
	public Pose pose(GltfPaletteLayout layout, SkinnedBounds bounds, GltfMorphLayout morphs,
			GltfAnimation[] clips, float[] times, int lod) {
		if (clips.length != times.length) {
			throw new IllegalArgumentException("pose has " + clips.length + " clips but " + times.length
					+ " times");
		}

		Local thread = local.get();
		int[] buckets = thread.buckets(clips.length);
		for (int layer = 0; layer < clips.length; layer++) {
			buckets[layer] = bucket(times[layer], lod);
		}
		return pose(layout, bounds, morphs, clips, buckets, clips.length, lod, thread);
	}

	private Pose pose(GltfPaletteLayout layout, SkinnedBounds bounds, GltfMorphLayout morphs,
			GltfAnimation[] clips, int[] buckets, int layers, int lod, Local thread) {
		requests.incrementAndGet();
		lodSum.addAndGet(lod);
		lodMax.accumulateAndGet(lod, Math::max);

		Key probe = thread.probe.set(layout, clips, buckets, layers, lod);

		Pose hit = poses.get(probe);
		if (hit != null) {
			return hit;
		}

		return poses.computeIfAbsent(probe.copy(), key -> evaluate(key, bounds, morphs, thread));
	}

	private Pose evaluate(Key key, SkinnedBounds bounds, GltfMorphLayout morphs, Local thread) {
		evaluations.incrementAndGet();
		long startNanos = System.nanoTime();

		GltfPose.Scratch scratch = thread.scratch;
		int size = key.layout.size();
		Matrix4f[] palette = scratch.palette(size);

		int morphFloats = morphs.blockFloats();
		float[] morphBlock = morphs.isEmpty() ? null : scratch.morphBlock(morphFloats);

		float[] times = thread.times(key.layers);
		for (int layer = 0; layer < key.layers; layer++) {
			times[layer] = representativeTime(key.buckets[layer], key.lod);
		}

		GltfPose.evaluate(key.layout, key.clips, times, palette, morphs, morphBlock, scratch);

		Vector4f sphere = new Vector4f();
		bounds.evaluate(palette, sphere);

		int boneBase = BoneBuffer.getInstance()
				.addPalette(palette, size);
		int morphBase = morphBlock == null ? 0
				: BoneBuffer.getInstance()
						.addMorphBlock(morphBlock, morphFloats);

		FrameCost.getInstance()
				.addPoseNanos(System.nanoTime() - startNanos);

		return new Pose(boneBase, morphBase, sphere);
	}

	public void endFrame() {
		poses.clear();
		requestsLastFrame = requests.getAndSet(0);
		evaluationsLastFrame = evaluations.getAndSet(0);
		lodSumLastFrame = lodSum.getAndSet(0);
		lodMaxLastFrame = lodMax.getAndSet(0);
	}

	public int requestsLastFrame() {
		return requestsLastFrame;
	}

	public int evaluationsLastFrame() {
		return evaluationsLastFrame;
	}

	public int lodMaxLastFrame() {
		return lodMaxLastFrame;
	}

	public int lodMeanCentisLastFrame() {
		int requests = requestsLastFrame;
		return requests == 0 ? 0 : lodSumLastFrame * 100 / requests;
	}

	public float quantumSeconds(int lod) {
		return quantumSeconds * PoseLod.quantumScale(lod);
	}

	private int bucket(float timeSeconds, int lod) {
		if (quantumSeconds <= 0.0f) {
			return Float.floatToIntBits(timeSeconds == 0.0f ? 0.0f : timeSeconds);
		}
		return Math.round(timeSeconds / quantumSeconds(lod));
	}

	private float representativeTime(int bucket, int lod) {
		return quantumSeconds <= 0.0f ? Float.intBitsToFloat(bucket) : bucket * quantumSeconds(lod);
	}
}
