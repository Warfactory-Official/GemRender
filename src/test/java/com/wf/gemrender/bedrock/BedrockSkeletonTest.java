package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;

class BedrockSkeletonTest {

	private static final int ROOT = 0;
	private static final int ARM = 1;
	private static final int HAND = 2;

	private final BedrockSkeleton skeleton = BedrockFixture.skeleton(BedrockFixture.CHAIN);

	@Test
	@DisplayName("a slot is a bone, in file order, and nothing else takes one")
	void slotsFollowTheFile() {
		NodeTable table = skeleton.table();

		assertThat(table.nodeName(ROOT)).isEqualTo("root");
		assertThat(table.nodeName(ARM)).isEqualTo("arm");
		assertThat(table.nodeName(HAND)).isEqualTo("hand");
		assertThat(table.slotOfName("arm")).isEqualTo(ARM);

		assertThat(table.nodeCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("a bone's parent comes from the name, whichever order the file declares them")
	void parentsResolveByNameInAnyOrder() {
		NodeTable table = skeleton.table();
		assertThat(table.parentSlots()[ROOT]).isEqualTo(-1);
		assertThat(table.parentSlots()[ARM]).isEqualTo(ROOT);
		assertThat(table.parentSlots()[HAND]).isEqualTo(ARM);

		NodeTable loose = BedrockFixture.skeleton(BedrockFixture.LOOSE)
				.table();
		assertThat(loose.parentSlots()[0]).as("forward reference to a bone declared later")
				.isEqualTo(1);
		assertThat(loose.parentSlots()[2]).as("an unknown parent becomes a root rather than a crash")
				.isEqualTo(-1);
	}

	@Test
	@DisplayName("a bone's translation is its pivot minus its parent's, X-flipped and over sixteen")
	void translationIsTheRelativePivot() {
		float[] scratch = skeleton.table()
				.newScratch();

		assertTranslation(scratch, ROOT, -1.0f / 16.0f, 2.0f / 16.0f, 3.0f / 16.0f);
		assertTranslation(scratch, ARM, (-7.0f + 1.0f) / 16.0f, (11.0f - 2.0f) / 16.0f,
				(13.0f - 3.0f) / 16.0f);
		assertTranslation(scratch, HAND, (-17.0f + 7.0f) / 16.0f, (19.0f - 11.0f) / 16.0f,
				(23.0f - 13.0f) / 16.0f);
	}

	@Test
	@DisplayName("a bone's rotation is Rz * Ry * Rx with X and Y negated")
	void rotationUsesTheBedrockEulerConvention() {
		float[] scratch = skeleton.table()
				.newScratch();

		Quaternionf expected = new Quaternionf()
				.rotateZ((float) Math.toRadians(30.0))
				.rotateY((float) Math.toRadians(-20.0))
				.rotateX((float) Math.toRadians(-10.0));

		assertRotation(scratch, ROOT, expected);

		Quaternionf unflipped = new Quaternionf()
				.rotateZ((float) Math.toRadians(30.0))
				.rotateY((float) Math.toRadians(20.0))
				.rotateX((float) Math.toRadians(10.0));
		assertThat(unflipped.difference(expected, new Quaternionf()).w)
				.as("the flipped and unflipped rotations must not coincide, or this proves nothing")
				.isLessThan(0.999f);
	}

	@Test
	@DisplayName("the Euler order is ZYX, not XYZ")
	void eulerOrderIsNotArbitrary() {
		Quaternionf zyx = new Quaternionf();
		BedrockSkeleton.quaternion(0.3f, 0.4f, 0.5f, zyx);

		Quaternionf xyz = new Quaternionf().rotateX(0.3f)
				.rotateY(0.4f)
				.rotateZ(0.5f);

		assertThat(zyx.x).isCloseTo(new Quaternionf().rotateZ(0.5f)
				.rotateY(0.4f)
				.rotateX(0.3f).x, within(1e-6f));
		assertThat(Math.abs(zyx.difference(xyz, new Quaternionf()).w)).isLessThan(0.999f);
	}

	@Test
	@DisplayName("scale is one everywhere, because Bedrock geometry has no scale")
	void restScaleIsUnity() {
		float[] scratch = skeleton.table()
				.newScratch();
		for (int slot = 0; slot < skeleton.slotCount(); slot++) {
			for (int axis = 0; axis < 3; axis++) {
				assertThat(scratch[slot * NodeTable.TRS_STRIDE + NodeTable.SCALE + axis]).isEqualTo(1.0f);
			}
		}
	}

	@Test
	@DisplayName("with no rotations anywhere, a bone's rest matrix lands on its own absolute pivot")
	void unrotatedChainRestoresAbsolutePivots() {
		BedrockSkeleton flat = BedrockFixture.skeleton(BedrockFixture.LOOSE);
		GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(flat.table());

		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, null, 0.0f, palette);

		for (int slot = 0; slot < flat.slotCount(); slot++) {
			Vector3f origin = palette[slot].transformPosition(new Vector3f());
			float[] pivot = flat.pivot(slot);
			assertThat(origin.x).isCloseTo(pivot[0] / 16.0f, within(1e-6f));
			assertThat(origin.y).isCloseTo(pivot[1] / 16.0f, within(1e-6f));
			assertThat(origin.z).isCloseTo(pivot[2] / 16.0f, within(1e-6f));
		}
	}

	@Test
	@DisplayName("a rotated chain matches a matrix chain composed by hand")
	void rotatedChainMatchesAnIndependentComposition() {
		GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(skeleton.table());
		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, null, 0.0f, palette);

		Matrix4f expectedRoot = bone(new Matrix4f(), 1, 2, 3, 10, 20, 30);
		Matrix4f expectedArm = bone(new Matrix4f(expectedRoot), 7 - 1, 11 - 2, 13 - 3, -5, 0, 45);
		Matrix4f expectedHand = bone(new Matrix4f(expectedArm), 17 - 7, 19 - 11, 23 - 13, 0, 0, 0);

		assertMatrix(palette[ROOT], expectedRoot);
		assertMatrix(palette[ARM], expectedArm);
		assertMatrix(palette[HAND], expectedHand);
	}

	@Test
	@DisplayName("a cube's own rotation is baked into its vertices, about its own pivot")
	void rotatedCubeIsBakedIntoItsGeometry() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16},
				  "bones": [{"name": "b", "pivot": [0, 0, 0], "cubes": [
				    {"origin": [0, 0, 0], "size": [16, 16, 16], "uv": [0, 0],
				     "pivot": [0, 0, 0], "rotation": [0, 90, 0]}]}]}]}
				""";

		BedrockSkeleton turned = BedrockFixture.skeleton(json);
		assertThat(turned.slotCount()).isEqualTo(1);
		assertThat(turned.parts()
				.get(0)
				.slot()).isEqualTo(0);
		assertThat(turned.parts()
				.get(0)
				.placement()).isNotNull();

		BedrockCubes cubes = new BedrockCubes();
		BedrockSkeleton.Part part = turned.parts()
				.get(0);
		cubes.add(part.cube(), part.pivot(), part.mirror(), part.inflate(), 16, 16, part.placement());

		float[] positions = cubes.positions();
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		for (int v = 0; v < cubes.vertexCount(); v++) {
			minX = Math.min(minX, positions[v * 3]);
			maxX = Math.max(maxX, positions[v * 3]);
			minZ = Math.min(minZ, positions[v * 3 + 2]);
			maxZ = Math.max(maxZ, positions[v * 3 + 2]);
		}

		assertThat(minX).isCloseTo(-1.0f, within(1e-6f));
		assertThat(maxX).isCloseTo(0.0f, within(1e-6f));
		assertThat(minZ).isCloseTo(-1.0f, within(1e-6f));
		assertThat(maxZ).isCloseTo(0.0f, within(1e-6f));

		float[] normals = cubes.normals();
		boolean anyAlongZ = false;
		for (int v = 0; v < cubes.vertexCount(); v++) {
			assertThat(Math.abs(normals[v * 3 + 1]) + Math.abs(normals[v * 3])
					+ Math.abs(normals[v * 3 + 2])).as("a rotated normal is still a unit axis vector")
					.isCloseTo(1.0f, within(1e-5f));
			anyAlongZ |= Math.abs(normals[v * 3 + 2] - 1.0f) < 1e-5f;
		}
		assertThat(anyAlongZ).isTrue();
	}

	@Test
	@DisplayName("the rest Euler angles a rotation channel adds to are the converted ones")
	void restEulerIsExposedInConvertedRadians() {
		assertThat(skeleton.restEuler(ROOT, 0)).isCloseTo((float) Math.toRadians(-10.0), within(1e-6f));
		assertThat(skeleton.restEuler(ROOT, 1)).isCloseTo((float) Math.toRadians(-20.0), within(1e-6f));
		assertThat(skeleton.restEuler(ROOT, 2)).isCloseTo((float) Math.toRadians(30.0), within(1e-6f));
	}

	@Test
	@DisplayName("a bone's cubes are filed under its slot, with the bone's mirror and inflate")
	void partsCarryTheResolvedDefaults() {
		assertThat(skeleton.parts()).hasSize(3);
		assertThat(skeleton.parts()
				.get(0)
				.slot()).isEqualTo(ROOT);
		assertThat(skeleton.parts()
				.get(1)
				.inflate()).isEqualTo(0.5f);
		assertThat(skeleton.parts()
				.get(2)
				.slot()).as("a rotated cube stays on its bone's slot; its rotation is baked")
				.isEqualTo(HAND);
		assertThat(skeleton.parts()
				.get(0)
				.placement()).as("an unrotated cube needs no placement at all")
				.isNull();

		BedrockSkeleton legacy = BedrockFixture.skeleton(BedrockFixture.LEGACY);
		assertThat(legacy.parts()
				.get(0)
				.mirror()).isTrue();
	}

	private static Matrix4f bone(Matrix4f parent, float px, float py, float pz, float rx, float ry,
			float rz) {
		return parent.translate(-px / 16.0f, py / 16.0f, pz / 16.0f)
				.rotateZ((float) Math.toRadians(rz))
				.rotateY((float) Math.toRadians(-ry))
				.rotateX((float) Math.toRadians(-rx));
	}

	private static void assertMatrix(Matrix4f actual, Matrix4f expected) {
		float[] a = new float[16];
		float[] b = new float[16];
		actual.get(a);
		expected.get(b);
		for (int i = 0; i < 16; i++) {
			assertThat(a[i]).isCloseTo(b[i], within(1e-5f));
		}
	}

	private static void assertTranslation(float[] scratch, int slot, float x, float y, float z) {
		int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
		assertThat(scratch[base]).isCloseTo(x, within(1e-6f));
		assertThat(scratch[base + 1]).isCloseTo(y, within(1e-6f));
		assertThat(scratch[base + 2]).isCloseTo(z, within(1e-6f));
	}

	private static void assertRotation(float[] scratch, int slot, Quaternionf expected) {
		Quaternionf actual = rotationOf(scratch, slot);
		assertThat(actual.x).isCloseTo(expected.x, within(1e-5f));
		assertThat(actual.y).isCloseTo(expected.y, within(1e-5f));
		assertThat(actual.z).isCloseTo(expected.z, within(1e-5f));
		assertThat(actual.w).isCloseTo(expected.w, within(1e-5f));
	}

	private static Quaternionf rotationOf(float[] scratch, int slot) {
		int base = slot * NodeTable.TRS_STRIDE + NodeTable.ROTATION;
		return new Quaternionf(scratch[base], scratch[base + 1], scratch[base + 2], scratch[base + 3]);
	}
}
