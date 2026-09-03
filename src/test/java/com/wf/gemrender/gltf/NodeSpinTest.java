package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeSpinTest {
	private final GltfPaletteLayout layout = RigFixture.layout();
	private final NodeTable table = RigFixture.layout()
			.nodeTable();

	private static final float TURNS_PER_SECOND = 0.25f;

	@Test
	@DisplayName("at time zero a spin leaves the model at its rest pose")
	void zeroTimeIsRest() {
		GltfAnimation spin = GltfAnimation.procedural("spin",
				NodeSpin.aboutY(table, RigFixture.NODE_BONE0, TURNS_PER_SECOND));

		Matrix4f[] rest = RigFixture.pose(null, 0.0f);
		Matrix4f[] spun = RigFixture.pose(spin, 0.0f);

		for (int slot = 0; slot < rest.length; slot++) {
			assertThat(MatrixScalar.maxDifference(rest[slot], spun[slot])).as("slot %d at t=0", slot)
					.isLessThan(1e-6f);
		}
	}

	@Test
	@DisplayName("a quarter of the period is a quarter turn about the node's own axis")
	void quarterPeriodIsAQuarterTurn() {
		int slot = RigFixture.NODE_BONE0;
		GltfAnimation spin = GltfAnimation.procedural("spin",
				NodeSpin.aboutY(table, slot, TURNS_PER_SECOND));

		Matrix4f rest = RigFixture.pose(null, 0.0f)[slot];
		Matrix4f spun = RigFixture.pose(spin, 1.0f / (4.0f * TURNS_PER_SECOND))[slot];

		Matrix4f contributed = new Matrix4f(rest).invert()
				.mul(spun);
		Vector3f x = contributed.transformDirection(new Vector3f(1.0f, 0.0f, 0.0f));

		assertThat(x.x).as("local +X after a quarter turn about +Y, x")
				.isCloseTo(0.0f, within(1e-5f));
		assertThat(x.y).isCloseTo(0.0f, within(1e-5f));
		assertThat(x.z).as("local +X after a quarter turn about +Y, z")
				.isCloseTo(-1.0f, within(1e-5f));
	}

	@Test
	@DisplayName("the axis is local, so an existing orientation is kept rather than overwritten")
	void theAxisIsLocal() {

		int slot = RigFixture.NODE_BONE0;
		float quarterTurn = 1.0f / (4.0f * TURNS_PER_SECOND);

		NodeSpin tilt = NodeSpin.about(table, slot, 1.0f, 0.0f, 0.0f, TURNS_PER_SECOND);
		GltfAnimation tiltOnly = GltfAnimation.procedural("tilt", tilt);
		GltfAnimation tiltThenSpin = GltfAnimation.procedural("tilt+spin", tilt,
				NodeSpin.aboutY(table, slot, TURNS_PER_SECOND));

		Matrix4f tilted = RigFixture.pose(tiltOnly, quarterTurn)[slot];
		Matrix4f spun = RigFixture.pose(tiltThenSpin, quarterTurn)[slot];

		Vector3f tiltedUp = tilted.transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));
		Vector3f spunUp = spun.transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));

		assertThat(spunUp.distance(tiltedUp)).as("the spin axis after a quarter turn about it")
				.isLessThan(1e-5f);

		assertThat(tiltedUp.distance(new Vector3f(0.0f, 1.0f, 0.0f))).as("the first driver tilted the node")
				.isGreaterThan(0.5f);

		assertThat(MatrixScalar.maxDifference(tilted, spun)).as("the node did turn")
				.isGreaterThan(0.5f);
	}

	@Test
	@DisplayName("a spun node carries its children with it")
	void childrenFollow() {
		int parent = firstSlotWithAChild();
		int child = firstChildOf(parent);

		GltfAnimation spin = GltfAnimation.procedural("spin", NodeSpin.aboutY(table, parent, TURNS_PER_SECOND));

		Matrix4f rest = RigFixture.pose(null, 0.0f)[child];
		Matrix4f spun = RigFixture.pose(spin, 1.0f / (4.0f * TURNS_PER_SECOND))[child];

		assertThat(MatrixScalar.maxDifference(rest, spun)).as("the child of a spun node moved")
				.isGreaterThan(1e-3f);
	}

	@Test
	@DisplayName("one full turn is the clip's period, so it loops seamlessly")
	void thePeriodIsOneTurn() {
		NodeSpin driver = NodeSpin.aboutY(table, RigFixture.NODE_BONE0, TURNS_PER_SECOND);
		GltfAnimation spin = GltfAnimation.procedural("spin", driver);

		assertThat(driver.cycleSeconds()).isCloseTo(1.0f / TURNS_PER_SECOND, within(1e-6f));
		assertThat(spin.duration()).isCloseTo(1.0f / TURNS_PER_SECOND, within(1e-6f));

		Matrix4f start = RigFixture.pose(spin, spin.loop(0.0f))[RigFixture.NODE_BONE0];
		Matrix4f wrapped = RigFixture.pose(spin, spin.loop(spin.duration()))[RigFixture.NODE_BONE0];

		assertThat(MatrixScalar.maxDifference(start, wrapped)).as("the pose across the loop point")
				.isLessThan(1e-5f);
	}

	@Test
	@DisplayName("two spins describing the same motion are equal, so the pose cache collapses them")
	void identicalSpinsAreEqual() {
		NodeSpin one = NodeSpin.aboutY(table, RigFixture.NODE_BONE0, TURNS_PER_SECOND);
		NodeSpin two = NodeSpin.aboutY(table, RigFixture.NODE_BONE0, TURNS_PER_SECOND);
		NodeSpin faster = NodeSpin.aboutY(table, RigFixture.NODE_BONE0, TURNS_PER_SECOND * 2.0f);

		assertThat(one).isEqualTo(two)
				.hasSameHashCodeAs(two);
		assertThat(one).isNotEqualTo(faster);

		assertThat(GltfAnimation.procedural("spin", one))
				.isEqualTo(GltfAnimation.procedural("spin", two))
				.hasSameHashCodeAs(GltfAnimation.procedural("spin", two));
		assertThat(GltfAnimation.procedural("spin", one))
				.isNotEqualTo(GltfAnimation.procedural("spin", faster));
	}

	@Test
	@DisplayName("an overlay turns relative to the clip rather than fighting it")
	void overlayComposesOntoAnAuthoredClip() {
		int slot = RigFixture.NODE_BONE0;
		GltfAnimation clip = RigFixture.animation();
		GltfAnimation overlaid = clip.with(NodeSpin.aboutY(table, slot, TURNS_PER_SECOND));

		assertThat(overlaid.duration()).isEqualTo(Math.max(clip.duration(), 1.0f / TURNS_PER_SECOND));
		assertThat(overlaid.drivers()).hasSize(clip.drivers()
				.size() + 1);

		Matrix4f keyed = RigFixture.pose(clip, RigFixture.MID_CLIP)[slot];
		Matrix4f both = RigFixture.pose(overlaid, RigFixture.MID_CLIP)[slot];

		assertThat(MatrixScalar.maxDifference(keyed, both)).as("the overlay changed the pose")
				.isGreaterThan(1e-3f);

		Vector3f keyedUp = keyed.transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));
		Vector3f bothUp = both.transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));
		assertThat(bothUp.distance(keyedUp)).as("the overlay's axis, in the clip's frame")
				.isLessThan(1e-5f);
	}

	@Test
	@DisplayName("a zero axis or an unknown slot is refused rather than rendered wrong")
	void badInputIsRefused() {
		assertThatThrownBy(() -> NodeSpin.about(table, RigFixture.NODE_BONE0, 0.0f, 0.0f, 0.0f, 1.0f))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("axis");

		assertThatThrownBy(() -> NodeSpin.aboutY(table, table.nodeCount() + 10, 1.0f))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no node in slot");
		assertThatThrownBy(() -> NodeSpin.aboutY(table, -1, 1.0f))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private int firstSlotWithAChild() {
		for (int parent : layout.parentSlots()) {
			if (parent >= 0) {
				return parent;
			}
		}
		throw new AssertionError("the rig has no hierarchy");
	}

	private int firstChildOf(int parent) {
		int[] parents = layout.parentSlots();
		for (int slot = 0; slot < parents.length; slot++) {
			if (parents[slot] == parent) {
				return slot;
			}
		}
		throw new AssertionError("slot " + parent + " has no children");
	}
}
