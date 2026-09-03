package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.bedrock.BedrockParts.RigidPart;
import com.wf.gemrender.gltf.DutyCycle;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.NodeSpin;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PartsPose;

class PartsLayerTest {

	@Test
	void twoLayersDrawTheSameThingAsTheOneMergedClipTheyReplace() {
		Fixture fixture = new Fixture(Set.of("arm", "hand"));

		GltfAnimation arm = fixture.spinOf("arm", 0.25f);
		GltfAnimation hand = fixture.spinOf("hand", 0.4f);
		GltfAnimation merged = arm.with(hand.drivers()
				.toArray(new com.wf.gemrender.gltf.PoseDriver[0]));

		for (float time : new float[] { 0.0f, 0.37f, 1.5f, 3.25f }) {
			Matrix4f[] layered = fixture.model.newTransforms();
			Matrix4f[] single = fixture.model.newTransforms();

			PartsPose.evaluate(fixture.model, new GltfAnimation[] { arm, hand },
					new float[] { time, time }, layered, null, fixture.scratch);
			PartsPose.evaluate(fixture.model, merged, time, single, fixture.scratch);

			for (int i = 0; i < layered.length; i++) {
				assertThat(layered[i]).as("part %s at t=%s", i, time)
						.isEqualTo(single[i]);
			}
		}
	}

	@Test
	void aLayerThatMovedOnItsOwnLandsWhereEvaluatingBothWould() {
		Fixture fixture = new Fixture(Set.of("arm", "hand"));

		GltfAnimation arm = fixture.spinOf("arm", 0.25f);
		GltfAnimation hand = fixture.spinOf("hand", 0.4f);
		boolean[] handOnly = fixture.model.withAncestors(fixture.model.drivenBy(hand));

		Matrix4f[] sparse = fixture.model.newTransforms();
		Matrix4f[] full = fixture.model.newTransforms();

		PartsPose.evaluate(fixture.model, new GltfAnimation[] { arm, hand }, new float[] { 1.0f, 1.0f },
				sparse, null, fixture.scratch);

		for (float handTime : new float[] { 1.25f, 2.0f, 3.75f }) {
			PartsPose.evaluate(fixture.model, new GltfAnimation[] { arm, hand },
					new float[] { 1.0f, handTime }, sparse, handOnly, fixture.scratch);
			PartsPose.evaluate(fixture.model, new GltfAnimation[] { arm, hand },
					new float[] { 1.0f, handTime }, full, null, fixture.scratch);

			for (int i = 0; i < full.length; i++) {
				assertThat(sparse[i]).as("part %s with the hand alone at t=%s", i, handTime)
						.isEqualTo(full[i]);
			}
		}
	}

	@Test
	void aLayerReachesFewerPartsThanTheTwoTogether() {
		Fixture fixture = new Fixture(Set.of("arm", "hand"));

		int arm = GemRenderPartsModel.countTrue(fixture.model.drivenBy(fixture.spinOf("arm", 0.25f)));
		int hand = GemRenderPartsModel.countTrue(fixture.model.drivenBy(fixture.spinOf("hand", 0.4f)));

		assertThat(hand).isLessThan(arm);
		assertThat(arm).isLessThan(fixture.model.partCount());
	}

	@Test
	void widenedToItsAncestorsAPartAlwaysHasSomewhereToHangFrom() {
		Fixture fixture = new Fixture(Set.of("arm", "hand"));
		boolean[] hand = fixture.model.drivenBy(fixture.spinOf("hand", 0.4f));
		boolean[] widened = fixture.model.withAncestors(hand);

		assertThat(GemRenderPartsModel.countTrue(widened))
				.isGreaterThan(GemRenderPartsModel.countTrue(hand));

		for (int i = 0; i < widened.length; i++) {
			int parent = fixture.model.parts()
					.get(i)
					.parent();
			if (widened[i] && parent >= 0) {
				assertThat(widened[parent]).as("part %s is placed relative to part %s", i, parent)
						.isTrue();
			}
		}
	}

	@Test
	void aHoldingLayerStopsAdvancingAndStaysWhereItStopped() {
		assertThat(DutyCycle.held(0.0f, 4.0f, 0.25f)).isZero();
		assertThat(DutyCycle.held(0.5f, 4.0f, 0.25f)).isEqualTo(0.5f);
		assertThat(DutyCycle.held(1.0f, 4.0f, 0.25f)).isEqualTo(1.0f);
		assertThat(DutyCycle.held(2.0f, 4.0f, 0.25f)).isEqualTo(1.0f);
		assertThat(DutyCycle.held(3.9f, 4.0f, 0.25f)).isEqualTo(1.0f);
		assertThat(DutyCycle.held(4.5f, 4.0f, 0.25f)).isEqualTo(4.5f);
		assertThat(DutyCycle.held(6.0f, 4.0f, 0.25f)).isEqualTo(5.0f);
	}

	@Test
	void holdingIsIdempotentSoItCanBeAppliedTwice() {
		for (float time = 0.0f; time < 8.0f; time += 0.13f) {
			float once = DutyCycle.held(time, 4.0f, 0.25f);
			assertThat(DutyCycle.held(once, 4.0f, 0.25f)).isEqualTo(once);
		}
	}

	@Test
	void aFullDutyLayerIsLeftExactlyAsItWas() {
		com.wf.gemrender.gltf.PoseDriver spin = new Fixture(Set.of("arm")).spin("arm", 0.25f);
		assertThat(DutyCycle.of(spin, 1.0f)).isSameAs(spin);
	}

	private static final class Fixture {
		private final BedrockSkeleton skeleton;
		private final GemRenderPartsModel model;
		private final PartsPose.Scratch scratch = new PartsPose.Scratch();

		private Fixture(Set<String> moving) {
			BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.CHAIN);
			this.skeleton = BedrockSkeleton.of(geometry);

			List<RigidPart> rigid = BedrockParts.partition(geometry, skeleton, moving);
			List<GemRenderPartsModel.Part> parts = new ArrayList<>(rigid.size());
			int[] slotToPart = new int[skeleton.slotCount()];

			for (int i = 0; i < rigid.size(); i++) {
				RigidPart part = rigid.get(i);
				parts.add(new GemRenderPartsModel.Part(part.name(), part.rootSlot(), part.parent(),
						part.toParent(), null));
				for (int slot : part.slots()) {
					slotToPart[slot] = i;
				}
			}

			this.model = new GemRenderPartsModel(parts, GltfPaletteLayout.ofNodes(skeleton.table()),
					slotToPart, Map.of(), new Vector4f(), 0, List.of());
		}

		private NodeSpin spin(String bone, float turnsPerSecond) {
			NodeTable table = skeleton.table();
			return NodeSpin.aboutY(table, table.slotOfName(bone), turnsPerSecond);
		}

		private GltfAnimation spinOf(String bone, float turnsPerSecond) {
			return GltfAnimation.procedural(bone, spin(bone, turnsPerSecond));
		}
	}
}
