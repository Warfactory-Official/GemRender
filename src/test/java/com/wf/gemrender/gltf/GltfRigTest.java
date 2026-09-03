package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;

class GltfRigTest {
	private static final int NODE_COUNT = RigFixture.NODE_COUNT;
	private static final int JOINT_COUNT = RigFixture.JOINT_COUNT;
	private static final int SKIN_BASE = RigFixture.SKIN_BASE;

	@Test
	@DisplayName("a skin gets its own palette block, after the node slots")
	void skinBlockFollowsTheNodeSlots() {
		GltfPaletteLayout layout = RigFixture.layout();

		assertThat(layout.nodes()).hasSize(NODE_COUNT);
		assertThat(layout.skins()).hasSize(1);
		assertThat(layout.size()).isEqualTo(NODE_COUNT + JOINT_COUNT);

		GltfPaletteLayout.SkinBlock block = layout.skins()
				.get(0);
		assertThat(block.base()).isEqualTo(SKIN_BASE);
		assertThat(block.jointCount()).isEqualTo(JOINT_COUNT);

		assertThat(layout.jointSlots(block.skin())).containsExactly(6, 7, 8, 9);
		assertThat(layout.nodeSlot(RigFixture.node(RigFixture.NODE_BONE0)))
				.isEqualTo(RigFixture.NODE_BONE0);
	}

	@Test
	@DisplayName("inverse bind matrices come through untransposed")
	void inverseBindMatricesAreRead() {
		Matrix4f[] ibm = RigFixture.layout()
				.skins()
				.get(0)
				.inverseBind();

		for (int j = 0; j < JOINT_COUNT; j++) {
			Vector3f translation = ibm[j].getTranslation(new Vector3f());
			assertThat(translation.x).as("ibm[%d].x", j).isEqualTo(0.0f, within(1e-6f));
			assertThat(translation.y).as("ibm[%d].y", j).isEqualTo(-j, within(1e-6f));
			assertThat(translation.z).as("ibm[%d].z", j).isEqualTo(0.0f, within(1e-6f));
		}
	}

	@Test
	@DisplayName("skin-local joint indices are remapped onto palette slots")
	void jointIndicesAreRemapped() {
		VertexSkinning skinning = RigFixture.columnSkinning();

		for (int v = 0; v < columnVertexCount(); v++) {
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				int slot = BoneAttributeCodec.unpackJoint(skinning.packedJoints(v), influence);
				assertThat(slot)
						.as("vertex %d influence %d", v, influence)
						.isBetween(SKIN_BASE, SKIN_BASE + JOINT_COUNT - 1);
			}
		}
	}

	@Test
	@DisplayName("the asset really does have four-influence vertices")
	void theRigExercisesMultiInfluenceBlending() {
		assertThat(RigFixture.columnSkinning()
				.maxInfluences()).isEqualTo(BoneAttributeCodec.INFLUENCES);
	}

	@Test
	@DisplayName("every vertex's weights survive Flywheel's truncating packer summing to exactly 1.0")
	void quantisedWeightsSumToOneAfterPacking() {
		VertexSkinning skinning = RigFixture.columnSkinning();

		for (int v = 0; v < columnVertexCount(); v++) {
			int sum = 0;
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				sum += BoneAttributeCodec.packNormU8(skinning.weightChannel(v, influence));
			}
			assertThat(sum)
					.as("vertex %d weights sum after packing", v)
					.isEqualTo(BoneAttributeCodec.WEIGHT_SUM);
		}
	}

	@Test
	@DisplayName("a rigid primitive in a skinned file binds to its node, not to the skin")
	void theRigidPartBindsToItsOwnNode() {
		MeshPrimitiveModel flag = RigFixture.primitive(RigFixture.NODE_FLAG);
		assertThat(flag.getAttributes()).doesNotContainKey("JOINTS_0");

		VertexSkinning skinning = RigFixture.flagSkinning();
		assertThat(skinning.uniform()).isTrue();
		assertThat(BoneAttributeCodec.unpackJoint(skinning.packedJoints(0), 0))
				.isEqualTo(RigFixture.NODE_FLAG);
		assertThat(skinning.maxInfluences()).isEqualTo(1);
	}

	@Test
	@DisplayName("at the bind pose every skin matrix is the identity")
	void bindPoseIsTheIdentity() {
		Matrix4f[] palette = RigFixture.pose(null, 0.0f);

		for (int j = 0; j < JOINT_COUNT; j++) {
			assertThat(isIdentity(palette[SKIN_BASE + j]))
					.as("skin slot %d at rest: %s", j, palette[SKIN_BASE + j])
					.isTrue();
		}
	}

	@Test
	@DisplayName("the rest pose is restored, not whatever the last sample left in the node graph")
	void theRestPoseSurvivesAnEarlierSample() {
		RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP);

		Matrix4f[] rest = RigFixture.pose(null, 0.0f);
		for (int j = 0; j < JOINT_COUNT; j++) {
			assertThat(isIdentity(rest[SKIN_BASE + j]))
					.as("skin slot %d after a clip was sampled: %s", j, rest[SKIN_BASE + j])
					.isTrue();
		}

		assertThat(rest[RigFixture.NODE_FLAG].getTranslation(new Vector3f())
				.distance(0.0f, 4.0f, 0.0f))
				.as("the flag returns to the top of the column")
				.isLessThan(1e-5f);
	}

	@Test
	@DisplayName("a posed skin matrix matches an independently computed joint chain")
	void posedSkinMatricesMatchTheChain() {
		Matrix4f[] palette = RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP);

		Matrix4f expected = new Matrix4f();
		for (int j = 0; j < JOINT_COUNT; j++) {
			if (j > 0) {
				expected.translate(0.0f, 1.0f, 0.0f)
						.rotateZ(RigFixture.CURL_RADIANS);
			}

			Matrix4f jointMatrix = new Matrix4f(expected).translate(0.0f, -j, 0.0f);
			assertMatrixEquals(jointMatrix, palette[SKIN_BASE + j], "skin slot " + j);
		}

		assertThat(isIdentity(palette[SKIN_BASE + JOINT_COUNT - 1])).isFalse();
	}

	@Test
	@DisplayName("vertices bound to the unanimated root bone do not move")
	void theBaseOfTheColumnStaysPut() {
		Matrix4f[] palette = RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP);

		VertexSkinning skinning = RigFixture.columnSkinning();
		float[] positions = RigFixture.positions(RigFixture.NODE_ARMATURE);

		for (int v = 0; v < RigFixture.RING_WIDTH; v++) {
			Vector3f bind = new Vector3f(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
			Vector3f skinned = RigFixture.skin(skinning, palette, v, bind);

			assertThat(skinned.distance(bind))
					.as("bottom-ring vertex %d displacement", v)
					.isLessThan(0.12f);
		}
	}

	@Test
	@DisplayName("a multi-influence vertex lands between its bones, not on one of them")
	void blendingIsNotSnapping() {
		Matrix4f[] palette = RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP);

		VertexSkinning skinning = RigFixture.columnSkinning();
		float[] positions = RigFixture.positions(RigFixture.NODE_ARMATURE);

		int vertex = busiestVertex(skinning);
		Vector3f bind = new Vector3f(positions[vertex * 3], positions[vertex * 3 + 1],
				positions[vertex * 3 + 2]);
		Vector3f blended = RigFixture.skin(skinning, palette, vertex, bind);

		boolean matchesSomeSingleBone = false;
		for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
			if (skinning.blendWeight(vertex, influence) <= 0.0f) {
				continue;
			}
			int slot = BoneAttributeCodec.unpackJoint(skinning.packedJoints(vertex), influence);
			Vector3f single = palette[slot].transformPosition(new Vector3f(bind));
			matchesSomeSingleBone |= single.distance(blended) < 1e-3f;
		}

		assertThat(matchesSomeSingleBone)
				.as("vertex %d is posed by exactly one of its bones rather than by all of them", vertex)
				.isFalse();
	}

	private static int columnVertexCount() {
		return RigFixture.vertexCount(RigFixture.NODE_ARMATURE);
	}

	private static int busiestVertex(VertexSkinning skinning) {
		int best = 0;
		int bestCount = 0;
		for (int v = 0; v < columnVertexCount(); v++) {
			int count = 0;
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				if (skinning.blendWeight(v, influence) > 0.0f) {
					count++;
				}
			}
			if (count > bestCount) {
				bestCount = count;
				best = v;
			}
		}
		return best;
	}

	private static boolean isIdentity(Matrix4f m) {
		return m.equals(new Matrix4f(), 1e-5f);
	}

	private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String description) {
		float[] want = new float[16];
		float[] got = new float[16];
		expected.get(want);
		actual.get(got);
		for (int i = 0; i < 16; i++) {
			assertThat(got[i])
					.as("%s, element %d (expected %s, got %s)", description, i, expected, actual)
					.isEqualTo(want[i], within(1e-4f));
		}
	}
}
