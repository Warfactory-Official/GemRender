package com.wf.gemrender.rig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.MatrixScalar;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeOscillate;
import com.wf.gemrender.gltf.NodeSwing;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;

class RigSkeletonTest {
	private static final Vector3f UPPER_PIVOT = new Vector3f(0.0f, 0.25f, 0.0f);
	private static final Vector3f LOWER_PIVOT = new Vector3f(0.5625f, 0.25f, 0.0f);

	private static final Vector3f VERTEX = new Vector3f(0.75f, 0.3f, -0.1f);

	private static final float YAW = 0.31f;
	private static final float HIP = -0.22f;
	private static final float KNEE = 1.04f;

	private final RigBuilder rig = new RigBuilder("limb");
	private final int body = rig.bone("body", RigBuilder.ROOT, 0.0f, 0.0f, 0.0f);
	private final int upper = rig.bone("upper", body, UPPER_PIVOT.x, UPPER_PIVOT.y, UPPER_PIVOT.z);
	private final int lower = rig.bone("lower", upper, LOWER_PIVOT.x - UPPER_PIVOT.x,
			LOWER_PIVOT.y - UPPER_PIVOT.y, LOWER_PIVOT.z - UPPER_PIVOT.z);

	private final NodeTable table = rig.table();
	private final GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(table);

	private GltfAnimation held() {
		return GltfAnimation.procedural("held",
				NodeOscillate.fixed(table, upper, 0.0f, 1.0f, 0.0f, YAW),
				NodeOscillate.fixed(table, upper, 0.0f, 0.0f, 1.0f, HIP),
				NodeOscillate.fixed(table, lower, 0.0f, 0.0f, 1.0f, KNEE));
	}

	private Matrix4f[] pose(GltfAnimation clip, float time) {
		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, clip, time, palette);
		return palette;
	}

	private static Vector3f chained() {
		Matrix4f limb = new Matrix4f()
				.translate(UPPER_PIVOT)
				.rotateY(YAW)
				.rotateZ(HIP)
				.translate(-UPPER_PIVOT.x, -UPPER_PIVOT.y, -UPPER_PIVOT.z);
		limb.translate(LOWER_PIVOT)
				.rotateZ(KNEE)
				.translate(-LOWER_PIVOT.x, -LOWER_PIVOT.y, -LOWER_PIVOT.z);
		return limb.transformPosition(new Vector3f(VERTEX));
	}

	private static Vector3f baked(Vector3f pivot) {
		return new Vector3f(VERTEX).sub(pivot);
	}

	@Test
	@DisplayName("a posed bone puts its geometry where the old pivot chain put it")
	void matchesTheChain() {
		Matrix4f[] palette = pose(held(), 0.0f);

		Vector3f skinned = palette[lower].transformPosition(baked(LOWER_PIVOT));

		assertThat(skinned.distance(chained()))
				.as("skinned %s against chained %s", skinned, chained())
				.isLessThan(1e-5f);
	}

	@Test
	@DisplayName("a bone's translation is relative to its parent, not to the model")
	void translationsAreRelative() {
		Matrix4f[] rest = pose(null, 0.0f);

		assertThat(rest[upper].transformPosition(new Vector3f()))
				.isEqualTo(new Vector3f(UPPER_PIVOT));
		assertThat(rest[lower].transformPosition(new Vector3f()))
				.as("the child's rest origin is the sum down the chain, not its own offset")
				.isEqualTo(new Vector3f(LOWER_PIVOT));
	}

	@Test
	@DisplayName("two drivers on one node compose in the order they were added")
	void axesComposeInOrder() {
		Matrix4f expected = new Matrix4f().translate(UPPER_PIVOT)
				.rotateY(YAW)
				.rotateZ(HIP);

		Matrix4f actual = pose(held(), 0.0f)[upper];

		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				assertThat(actual.get(column, row)).as("m%d%d", column, row)
						.isCloseTo(expected.get(column, row), within(1e-6f));
			}
		}

		GltfAnimation reversed = GltfAnimation.procedural("reversed",
				NodeOscillate.fixed(table, upper, 0.0f, 0.0f, 1.0f, HIP),
				NodeOscillate.fixed(table, upper, 0.0f, 1.0f, 0.0f, YAW));
		assertThat(pose(reversed, 0.0f)[upper])
				.as("Ry then Rz is not Rz then Ry, and the rig has to be able to say which")
				.isNotEqualTo(actual);
	}

	@Test
	@DisplayName("an oscillator is base plus amplitude at a quarter of its period")
	void oscillatorPhase() {
		float base = 0.4f;
		float amplitude = 0.15f;
		float period = 2.0f;

		GltfAnimation clip = GltfAnimation.procedural("rock",
				NodeOscillate.about(table, upper, 0.0f, 0.0f, 1.0f, base, amplitude, period, 0.0f));

		assertThat(angleZ(pose(clip, 0.0f)[upper])).as("t=0 is the base angle")
				.isCloseTo(base, within(1e-5f));
		assertThat(angleZ(pose(clip, period * 0.25f)[upper])).as("a quarter period is the peak")
				.isCloseTo(base + amplitude, within(1e-5f));
		assertThat(angleZ(pose(clip, period * 0.75f)[upper])).as("three quarters is the trough")
				.isCloseTo(base - amplitude, within(1e-5f));
		assertThat(angleZ(pose(clip, period)[upper])).as("a whole period is back to the base")
				.isCloseTo(base, within(1e-5f));

		GltfAnimation lagged = GltfAnimation.procedural("rock",
				NodeOscillate.about(table, upper, 0.0f, 0.0f, 1.0f, base, amplitude, period, 0.25f));
		assertThat(angleZ(pose(lagged, 0.0f)[upper])).isCloseTo(base + amplitude, within(1e-5f));
	}

	@Test
	@DisplayName("a swing holds its stops outside the clip and is linear between them")
	void swingHoldsItsStops() {
		float open = 0.66f;
		GltfAnimation clip = GltfAnimation.procedural("grip",
				NodeSwing.open(table, upper, 0.0f, 0.0f, 1.0f, open));

		assertThat(clip.duration()).as("a swing is one unit long, so a drive maps straight onto it")
				.isEqualTo(1.0f);
		assertThat(angleZ(pose(clip, 0.0f)[upper])).isCloseTo(0.0f, within(1e-5f));
		assertThat(angleZ(pose(clip, 0.5f)[upper])).isCloseTo(open * 0.5f, within(1e-5f));
		assertThat(angleZ(pose(clip, 1.0f)[upper])).isCloseTo(open, within(1e-5f));
		assertThat(angleZ(pose(clip, 4.0f)[upper])).as("past the end it holds the stop")
				.isCloseTo(open, within(1e-5f));
		assertThat(angleZ(pose(clip, -4.0f)[upper])).as("before the start it holds the other")
				.isCloseTo(0.0f, within(1e-5f));
	}

	@Test
	@DisplayName("hiding a bone takes its whole subtree with it, and costs no clip time")
	void hideCollapsesTheSubtree() {
		GltfAnimation clip = GltfAnimation.procedural("damaged", NodeHide.of(table, upper));

		assertThat(clip.duration()).as("a damage state is a pose, not an animation")
				.isEqualTo(0.0f);

		Matrix4f[] palette = pose(clip, 0.0f);
		assertThat(palette[lower].transformPosition(baked(LOWER_PIVOT))
				.distance(new Vector3f(UPPER_PIVOT)))
				.as("the child collapses onto the hidden parent's own origin")
				.isLessThan(1e-6f);
		assertThat(palette[body].transformPosition(new Vector3f(VERTEX)))
				.as("a bone outside the subtree is untouched")
				.isEqualTo(new Vector3f(VERTEX));
	}

	@Test
	@DisplayName("layers applied in order are the same pose as one clip holding all of them")
	void layersComposeLikeOneClip() {
		GltfAnimation hip = GltfAnimation.procedural("hip",
				NodeOscillate.fixed(table, upper, 0.0f, 1.0f, 0.0f, YAW),
				NodeOscillate.fixed(table, upper, 0.0f, 0.0f, 1.0f, HIP));
		GltfAnimation knee = GltfAnimation.procedural("knee",
				NodeOscillate.fixed(table, lower, 0.0f, 0.0f, 1.0f, KNEE));

		Matrix4f[] layered = new Matrix4f[layout.size()];
		for (int i = 0; i < layered.length; i++) {
			layered[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, new GltfAnimation[] { hip, null, knee }, new float[] { 0.0f, 0.0f, 0.0f },
				layered, GltfMorphLayout.NONE, null, new GltfPose.Scratch());

		Matrix4f[] merged = pose(held(), 0.0f);
		for (int slot = 0; slot < merged.length; slot++) {
			assertThat(MatrixScalar.maxDifference(merged[slot], layered[slot])).as("slot %d", slot)
					.isLessThan(1e-6f);
		}
	}

	@Test
	@DisplayName("the skeleton is frozen once a clip has been built against it")
	void skeletonFreezes() {
		assertThatThrownBy(() -> rig.bone("late", body, 0.0f, 0.0f, 0.0f))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("frozen");
	}

	@Test
	@DisplayName("a mistyped mesh name names the ones the file does have")
	void unknownMeshIsNamed() {
		assertThatThrownBy(() -> rig.attach(body, java.util.Map.of("Body", geometry()), "Bdoy"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Body");
	}

	private static RigGeometry geometry() {
		return new RigGeometry(new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }, null,
				new float[] { 0, 0, 1, 0, 0, 1 }, new int[] { 0, 1, 2 });
	}

	private static float angleZ(Matrix4f matrix) {
		return (float) Math.atan2(matrix.m01(), matrix.m00());
	}
}
