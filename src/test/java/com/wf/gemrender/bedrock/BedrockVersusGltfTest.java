package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultNodeModel;

class BedrockVersusGltfTest {

	private static final float[][] PIVOTS = { { 1, 2, 3 }, { 7, 11, 13 }, { 17, 19, 23 } };
	private static final float[][] ROTATIONS = { { 10, 20, 30 }, { -5, 0, 45 }, { 0, 0, 0 } };

	private static List<NodeModel> gltfChain(float[][] extraTranslation) {
		DefaultNodeModel[] nodes = new DefaultNodeModel[3];

		for (int i = 0; i < 3; i++) {
			float[] pivot = PIVOTS[i];
			float[] parent = i == 0 ? new float[3] : PIVOTS[i - 1];

			float[] translation = {
					(-pivot[0] - -parent[0]) / 16.0f + extraTranslation[i][0],
					(pivot[1] - parent[1]) / 16.0f + extraTranslation[i][1],
					(pivot[2] - parent[2]) / 16.0f + extraTranslation[i][2],
			};

			Quaternionf rotation = new Quaternionf()
					.rotateZ((float) Math.toRadians(ROTATIONS[i][2]))
					.rotateY((float) Math.toRadians(-ROTATIONS[i][1]))
					.rotateX((float) Math.toRadians(-ROTATIONS[i][0]));

			nodes[i] = new DefaultNodeModel();
			nodes[i].setName("gltf" + i);
			nodes[i].setTranslation(translation);
			nodes[i].setRotation(new float[] { rotation.x, rotation.y, rotation.z, rotation.w });
			nodes[i].setScale(new float[] { 1.0f, 1.0f, 1.0f });
			if (i > 0) {
				nodes[i - 1].addChild(nodes[i]);
			}
		}

		return List.of(nodes[0], nodes[1], nodes[2]);
	}

	private static Matrix4f[] evaluate(GltfPaletteLayout layout, GltfAnimation clip, float time) {
		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, clip, time, palette);
		return palette;
	}

	@Test
	@DisplayName("a Bedrock rig and the equivalent glTF rig give the same rest palette")
	void restPalettesAgree() {
		BedrockSkeleton bedrock = BedrockFixture.skeleton(BedrockFixture.CHAIN);
		GltfPaletteLayout bedrockLayout = GltfPaletteLayout.ofNodes(bedrock.table());
		GltfPaletteLayout gltfLayout = GltfPaletteLayout.ofNodes(
				NodeTable.of(gltfChain(new float[3][3])));

		Matrix4f[] fromBedrock = evaluate(bedrockLayout, null, 0.0f);
		Matrix4f[] fromGltf = evaluate(gltfLayout, null, 0.0f);

		assertThat(bedrockLayout.size()).isEqualTo(3);
		assertThat(gltfLayout.size()).isEqualTo(3);
		for (int slot = 0; slot < 3; slot++) {
			assertMatrix(fromBedrock[slot], fromGltf[slot], "slot " + slot);
		}
	}

	@Test
	@DisplayName("a Bedrock position clip lands where the equivalent glTF translation does")
	void posedTranslationsAgree() {
		BedrockSkeleton bedrock = BedrockFixture.skeleton(BedrockFixture.CHAIN);
		Map<String, GltfAnimation> clips = BedrockAnimations.parse(BedrockFixture.json("""
				{"animations": {"shift": {"animation_length": 1, "bones": {"arm": {
				  "position": {"0.0": [0, 0, 0], "1.0": [32, 0, 16]}}}}}}
				"""), bedrock, "test");

		float[][] extra = new float[3][3];
		extra[1] = new float[] { -2.0f, 0.0f, 1.0f };

		Matrix4f[] fromBedrock = evaluate(GltfPaletteLayout.ofNodes(bedrock.table()), clips.get("shift"),
				1.0f);
		Matrix4f[] fromGltf = evaluate(GltfPaletteLayout.ofNodes(NodeTable.of(gltfChain(extra))), null,
				0.0f);

		for (int slot = 0; slot < 3; slot++) {
			assertMatrix(fromBedrock[slot], fromGltf[slot], "slot " + slot);
		}
	}

	@Test
	@DisplayName("a Bedrock rotation clip lands where the equivalent glTF rotation does")
	void posedRotationsAgree() {
		BedrockSkeleton bedrock = BedrockFixture.skeleton(BedrockFixture.CHAIN);
		Map<String, GltfAnimation> clips = BedrockAnimations.parse(BedrockFixture.json("""
				{"animations": {"bend": {"animation_length": 1, "bones": {"arm": {
				  "rotation": {"0.0": [0, 0, 0], "1.0": [35, 15, 10]}}}}}}
				"""), bedrock, "test");

		float[][] rotations = { { 10, 20, 30 }, { 30, 15, 55 }, { 0, 0, 0 } };
		Matrix4f[] fromBedrock = evaluate(GltfPaletteLayout.ofNodes(bedrock.table()), clips.get("bend"),
				1.0f);
		Matrix4f[] fromGltf = evaluate(GltfPaletteLayout.ofNodes(NodeTable.of(chainWith(rotations))), null,
				0.0f);

		for (int slot = 0; slot < 3; slot++) {
			assertMatrix(fromBedrock[slot], fromGltf[slot], "slot " + slot);
		}
	}

	@Test
	@DisplayName("a Bedrock cube's vertices land where the equivalent glTF vertices do")
	void geometryAgrees() {
		BedrockSkeleton skeleton = BedrockFixture.skeleton(BedrockFixture.UNIT);
		Matrix4f[] palette = evaluate(GltfPaletteLayout.ofNodes(skeleton.table()), null, 0.0f);

		BedrockCubes cubes = new BedrockCubes();
		cubes.add(skeleton.parts()
				.get(0)
				.cube(), skeleton.pivot(0), false, 0.0f, 16, 16);

		float[] positions = cubes.positions();
		for (int v = 0; v < cubes.vertexCount(); v++) {
			Vector3f world = palette[0].transformPosition(
					new Vector3f(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]));

			assertThat(Math.abs(world.x)).isCloseTo(0.5f, within(1e-6f));
			assertThat(Math.min(Math.abs(world.y), Math.abs(world.y - 1.0f)))
					.isCloseTo(0.0f, within(1e-6f));
			assertThat(Math.abs(world.z)).isCloseTo(0.5f, within(1e-6f));
		}
	}

	private static List<NodeModel> chainWith(float[][] rotations) {
		DefaultNodeModel[] nodes = new DefaultNodeModel[3];
		for (int i = 0; i < 3; i++) {
			float[] pivot = PIVOTS[i];
			float[] parent = i == 0 ? new float[3] : PIVOTS[i - 1];

			Quaternionf rotation = new Quaternionf()
					.rotateZ((float) Math.toRadians(rotations[i][2]))
					.rotateY((float) Math.toRadians(-rotations[i][1]))
					.rotateX((float) Math.toRadians(-rotations[i][0]));

			nodes[i] = new DefaultNodeModel();
			nodes[i].setName("gltf" + i);
			nodes[i].setTranslation(new float[] {
					(-pivot[0] + parent[0]) / 16.0f,
					(pivot[1] - parent[1]) / 16.0f,
					(pivot[2] - parent[2]) / 16.0f,
			});
			nodes[i].setRotation(new float[] { rotation.x, rotation.y, rotation.z, rotation.w });
			nodes[i].setScale(new float[] { 1.0f, 1.0f, 1.0f });
			if (i > 0) {
				nodes[i - 1].addChild(nodes[i]);
			}
		}
		return List.of(nodes[0], nodes[1], nodes[2]);
	}

	private static void assertMatrix(Matrix4f actual, Matrix4f expected, String what) {
		float[] a = new float[16];
		float[] b = new float[16];
		actual.get(a);
		expected.get(b);
		for (int i = 0; i < 16; i++) {
			assertThat(a[i]).as("%s, element %d", what, i)
					.isCloseTo(b[i], within(1e-5f));
		}
	}
}
