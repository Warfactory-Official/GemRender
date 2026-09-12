package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;

class ExternalPoseTest {
	private final GltfPaletteLayout layout = RigFixture.layout();
	private final NodeTable table = layout.nodeTable();

	private Matrix4f[] palette() {
		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		return palette;
	}

	@Test
	@DisplayName("a rest state composes to the palette a clipless evaluation gives")
	void restStateMatchesNoClip() {
		Matrix4f[] fromClipPath = palette();
		GltfPose.evaluate(layout, (GltfAnimation) null, 0.0f, fromClipPath);

		Matrix4f[] fromState = palette();
		float[] state = table.newScratch();
		GltfPose.evaluate(layout, state, fromState, GltfMorphLayout.NONE, null, new GltfPose.Scratch());

		for (int slot = 0; slot < layout.size(); slot++) {
			assertThat(MatrixScalar.maxDifference(fromClipPath[slot], fromState[slot]))
					.as("slot %d", slot)
					.isLessThan(1e-6f);
		}
	}

	@Test
	@DisplayName("a rotation written onto a bone turns that bone and everything under it")
	void aWrittenRotationPropagates() {
		Matrix4f[] rest = palette();
		GltfPose.evaluate(layout, (GltfAnimation) null, 0.0f, rest);

		float[] state = table.newScratch();
		Quaternionf quarterTurn = new Quaternionf().rotateY((float) Math.PI / 2.0f);
		table.setRotation(state, RigFixture.NODE_BONE0, quarterTurn);

		Matrix4f[] posed = palette();
		GltfPose.evaluate(layout, state, posed, GltfMorphLayout.NONE, null, new GltfPose.Scratch());

		assertThat(MatrixScalar.maxDifference(rest[RigFixture.NODE_BONE0], posed[RigFixture.NODE_BONE0]))
				.as("the bone the rotation was written on")
				.isGreaterThan(0.1f);
		assertThat(MatrixScalar.maxDifference(rest[RigFixture.NODE_FLAG], posed[RigFixture.NODE_FLAG]))
				.as("a descendant of it")
				.isGreaterThan(0.1f);

		assertThat(MatrixScalar.maxDifference(rest[RigFixture.NODE_ARMATURE], posed[RigFixture.NODE_ARMATURE]))
				.as("the bone above it")
				.isLessThan(1e-6f);
	}

	@Test
	@DisplayName("the composed transform is the rest chain times what was written, and the skin blocks follow")
	void theCompositionIsTheRestChainTimesTheWrite() {
		float[] state = table.newScratch();
		Quaternionf turn = new Quaternionf().rotateY(0.4f)
				.rotateX(-0.2f);
		table.setRotation(state, RigFixture.NODE_BONE0, turn);
		table.setTranslation(state, RigFixture.NODE_BONE0, 0.25f, 0.5f, -0.125f);
		table.setScale(state, RigFixture.NODE_BONE0, 2.0f, 2.0f, 2.0f);

		Matrix4f[] posed = palette();
		GltfPose.evaluate(layout, state, posed, GltfMorphLayout.NONE, null, new GltfPose.Scratch());

		int parent = table.parentSlots()[RigFixture.NODE_BONE0];
		Matrix4f expected = parent < 0 ? new Matrix4f() : new Matrix4f(posed[parent]);
		expected.mul(new Matrix4f().translationRotateScale(0.25f, 0.5f, -0.125f, turn.x, turn.y, turn.z,
				turn.w, 2.0f, 2.0f, 2.0f));
		assertThat(MatrixScalar.maxDifference(expected, posed[RigFixture.NODE_BONE0]))
				.isLessThan(1e-6f);

		for (GltfPaletteLayout.SkinBlock block : layout.skins()) {
			for (int joint = 0; joint < block.jointCount(); joint++) {
				Matrix4f wanted = new Matrix4f(posed[block.jointSlots()[joint]])
						.mul(block.inverseBind()[joint]);
				assertThat(MatrixScalar.maxDifference(wanted, posed[block.base() + joint]))
						.as("skin joint %d", joint)
						.isLessThan(1e-6f);
			}
		}
	}

	@Test
	@DisplayName("a node the file declared as a matrix reports that it cannot be posed")
	void aMatrixNodeIsNotPosable() {

		for (int slot = 0; slot < table.nodeCount(); slot++) {
			assertThat(table.isPosable(slot)).as("slot %d", slot)
					.isTrue();
		}
		assertThat(table.isPosable(-1)).isFalse();
		assertThat(table.isPosable(table.nodeCount())).isFalse();
	}
}
