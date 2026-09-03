package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.bedrock.BedrockGeometry.Cube;
import com.wf.gemrender.bedrock.BedrockGeometry.Face;
import com.wf.gemrender.bedrock.BedrockGeometry.FaceUv;

class BedrockCubesTest {
	private static final int TEXTURE_WIDTH = 64;
	private static final int TEXTURE_HEIGHT = 32;

	private static Cube boxUvCube(Boolean mirror) {
		return new Cube(new float[] { 0.0f, 0.0f, 0.0f }, new float[] { 1.0f, 2.0f, 3.0f }, null, null,
				null, mirror, new float[] { 10.0f, 20.0f }, null);
	}

	private static BedrockCubes emit(Cube cube, float[] pivot, boolean mirror, float inflate) {
		BedrockCubes cubes = new BedrockCubes();
		cubes.add(cube, pivot, mirror, inflate, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		return cubes;
	}

	private static int base(Face face) {
		return face.ordinal() * 4;
	}

	@Test
	@DisplayName("a cube's corners are the X-flipped box, sixteen units to the block")
	void cubeCornersAreMirroredInXAndScaled() {
		Cube cube = new Cube(new float[] { 2.0f, 3.0f, 4.0f }, new float[] { 1.0f, 2.0f, 3.0f }, null, null,
				null, null, new float[] { 0.0f, 0.0f }, null);
		BedrockCubes cubes = emit(cube, new float[] { 0.0f, 0.0f, 0.0f }, false, 0.0f);

		float[] positions = cubes.positions();
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		for (int v = 0; v < cubes.vertexCount(); v++) {
			minX = Math.min(minX, positions[v * 3]);
			maxX = Math.max(maxX, positions[v * 3]);
			minY = Math.min(minY, positions[v * 3 + 1]);
			maxY = Math.max(maxY, positions[v * 3 + 1]);
			minZ = Math.min(minZ, positions[v * 3 + 2]);
			maxZ = Math.max(maxZ, positions[v * 3 + 2]);
		}

		assertThat(minX).isCloseTo(-(2.0f + 1.0f) / 16.0f, within(1e-6f));
		assertThat(maxX).isCloseTo(-2.0f / 16.0f, within(1e-6f));
		assertThat(minY).isCloseTo(3.0f / 16.0f, within(1e-6f));
		assertThat(maxY).isCloseTo(5.0f / 16.0f, within(1e-6f));
		assertThat(minZ).isCloseTo(4.0f / 16.0f, within(1e-6f));
		assertThat(maxZ).isCloseTo(7.0f / 16.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a cube symmetric about the Bedrock origin is symmetric after the flip")
	void symmetricCubeIsUnmovedByTheFlip() {
		Cube cube = new Cube(new float[] { -8.0f, 0.0f, -8.0f }, new float[] { 16.0f, 16.0f, 16.0f }, null,
				null, null, null, new float[] { 0.0f, 0.0f }, null);
		float[] positions = emit(cube, new float[] { 0.0f, 0.0f, 0.0f }, false, 0.0f).positions();

		for (int v = 0; v < positions.length / 3; v++) {
			assertThat(Math.abs(positions[v * 3])).isCloseTo(0.5f, within(1e-6f));
			assertThat(Math.abs(positions[v * 3 + 2])).isCloseTo(0.5f, within(1e-6f));
		}
	}

	@Test
	@DisplayName("the pivot is subtracted, so a cube is stored in its bone's space")
	void cubeIsRelativeToItsPivot() {
		Cube cube = new Cube(new float[] { 0.0f, 0.0f, 0.0f }, new float[] { 16.0f, 16.0f, 16.0f }, null,
				null, null, null, new float[] { 0.0f, 0.0f }, null);

		float[] atOrigin = emit(cube, new float[] { 0.0f, 0.0f, 0.0f }, false, 0.0f).positions();
		float[] shifted = emit(cube, new float[] { -4.0f, 8.0f, 16.0f }, false, 0.0f).positions();

		for (int v = 0; v < atOrigin.length / 3; v++) {
			assertThat(shifted[v * 3]).isCloseTo(atOrigin[v * 3] + 4.0f / 16.0f, within(1e-6f));
			assertThat(shifted[v * 3 + 1]).isCloseTo(atOrigin[v * 3 + 1] - 8.0f / 16.0f, within(1e-6f));
			assertThat(shifted[v * 3 + 2]).isCloseTo(atOrigin[v * 3 + 2] - 1.0f, within(1e-6f));
		}
	}

	@Test
	@DisplayName("inflate pushes the minimum corner out by one unit and each extent by two")
	void inflateGrowsTheBoxSymmetrically() {
		Cube cube = boxUvCube(null);
		float[] plain = emit(cube, new float[3], false, 0.0f).positions();
		float[] fat = emit(cube, new float[3], false, 2.0f).positions();

		for (int v = 0; v < plain.length / 3; v++) {
			for (int axis = 0; axis < 3; axis++) {
				assertThat(Math.abs(fat[v * 3 + axis] - plain[v * 3 + axis]))
						.isCloseTo(2.0f / 16.0f, within(1e-6f));
			}
		}
	}

	@Test
	@DisplayName("every face winds counter-clockwise seen from outside")
	void everyFaceWindsOutward() {
		BedrockCubes cubes = emit(boxUvCube(null), new float[3], false, 0.0f);
		float[] positions = cubes.positions();
		float[] normals = cubes.normals();

		assertThat(cubes.vertexCount()).isEqualTo(24);

		for (int face = 0; face < 6; face++) {
			int v0 = face * 4;
			float[] edgeA = edge(positions, v0, v0 + 1);
			float[] edgeB = edge(positions, v0, v0 + 2);
			float[] cross = {
					edgeA[1] * edgeB[2] - edgeA[2] * edgeB[1],
					edgeA[2] * edgeB[0] - edgeA[0] * edgeB[2],
					edgeA[0] * edgeB[1] - edgeA[1] * edgeB[0],
			};

			float dot = cross[0] * normals[v0 * 3] + cross[1] * normals[v0 * 3 + 1]
					+ cross[2] * normals[v0 * 3 + 2];
			assertThat(dot).as("face %d winds outward", face)
					.isGreaterThan(0.0f);

			float[] edgeC = edge(positions, v0, v0 + 3);
			float[] cross2 = {
					edgeB[1] * edgeC[2] - edgeB[2] * edgeC[1],
					edgeB[2] * edgeC[0] - edgeB[0] * edgeC[2],
					edgeB[0] * edgeC[1] - edgeB[1] * edgeC[0],
			};
			float dot2 = cross2[0] * normals[v0 * 3] + cross2[1] * normals[v0 * 3 + 1]
					+ cross2[2] * normals[v0 * 3 + 2];
			assertThat(dot2).as("face %d's second triangle winds outward", face)
					.isGreaterThan(0.0f);
		}
	}

	@Test
	@DisplayName("a face's four vertices all sit on that face, with its normal")
	void facesAreFlatAndNormalsAgree() {
		BedrockCubes cubes = emit(boxUvCube(null), new float[3], false, 0.0f);
		float[] positions = cubes.positions();
		float[] normals = cubes.normals();

		for (Face face : Face.values()) {
			int axis = switch (face) {
				case DOWN, UP -> 1;
				case NORTH, SOUTH -> 2;
				default -> 0;
			};
			int first = base(face);
			float plane = positions[first * 3 + axis];

			for (int i = 0; i < 4; i++) {
				assertThat(positions[(first + i) * 3 + axis]).as("%s is flat", face)
						.isCloseTo(plane, within(1e-6f));
				assertThat(normals[(first + i) * 3 + axis]).as("%s's normal points along its axis", face)
						.isEqualTo(BedrockCubes.NORMAL[face.ordinal()][axis]);
			}
		}
	}

	@Test
	@DisplayName("the box-UV net puts each face on its own rectangle")
	void boxUvNetMatchesTheFormat() {
		BedrockCubes cubes = emit(boxUvCube(null), new float[3], false, 0.0f);

		assertRect(cubes, Face.EAST, 10.0f, 13.0f, 23.0f, 25.0f);
		assertRect(cubes, Face.NORTH, 13.0f, 14.0f, 23.0f, 25.0f);
		assertRect(cubes, Face.WEST, 14.0f, 17.0f, 23.0f, 25.0f);
		assertRect(cubes, Face.SOUTH, 17.0f, 18.0f, 23.0f, 25.0f);
		assertRect(cubes, Face.UP, 13.0f, 14.0f, 20.0f, 23.0f);
		assertRect(cubes, Face.DOWN, 14.0f, 15.0f, 20.0f, 23.0f);
	}

	@Test
	@DisplayName("the box-UV net measures in floored integer units, and ignores inflate")
	void boxUvUsesFlooredSizeAndNotInflate() {
		Cube fractional = new Cube(new float[3], new float[] { 1.6f, 2.9f, 3.9f }, null, null, null, null,
				new float[] { 10.0f, 20.0f }, null);

		BedrockCubes plain = emit(fractional, new float[3], false, 0.0f);
		BedrockCubes inflated = emit(fractional, new float[3], false, 4.0f);

		assertRect(plain, Face.EAST, 10.0f, 13.0f, 23.0f, 25.0f);
		assertRect(plain, Face.NORTH, 13.0f, 14.0f, 23.0f, 25.0f);

		float[] a = plain.texCoords();
		float[] b = inflated.texCoords();
		for (int i = 0; i < a.length; i++) {
			assertThat(b[i]).as("inflate moves geometry, never texture coordinates")
					.isCloseTo(a[i], within(1e-7f));
		}
	}

	@Test
	@DisplayName("box-UV mirror reverses u and swaps the two side rectangles, and moves no geometry")
	void mirrorFlipsTheNetAndNotTheBox() {
		BedrockCubes plain = emit(boxUvCube(null), new float[3], false, 0.0f);
		BedrockCubes mirrored = emit(boxUvCube(null), new float[3], true, 0.0f);

		float[] before = plain.positions();
		float[] after = mirrored.positions();
		for (int i = 0; i < before.length; i++) {
			assertThat(after[i]).as("mirror is a texture operation, not a geometry one")
					.isCloseTo(before[i], within(1e-7f));
		}

		assertRect(mirrored, Face.WEST, 10.0f, 13.0f, 23.0f, 25.0f);
		assertRect(mirrored, Face.EAST, 14.0f, 17.0f, 23.0f, 25.0f);
		assertRect(mirrored, Face.NORTH, 13.0f, 14.0f, 23.0f, 25.0f);

		int north = base(Face.NORTH);
		assertThat(mirrored.texCoords()[north * 2]).as("corner 0's u is the other end of the rect")
				.isCloseTo(plain.texCoords()[(north + 1) * 2], within(1e-7f));
	}

	@Test
	@DisplayName("per-face UV takes the rectangles literally, and drops a face the file omits")
	void perFaceUvIsLiteral() {
		Cube cube = new Cube(new float[3], new float[] { 1.0f, 2.0f, 3.0f }, null, null, null, null, null,
				Map.of(Face.NORTH, new FaceUv(new float[] { 4.0f, 5.0f }, new float[] { 6.0f, 7.0f }, 0),
						Face.UP, new FaceUv(new float[] { 0.0f, 0.0f }, new float[] { 2.0f, 2.0f }, 0)));

		BedrockCubes cubes = emit(cube, new float[3], false, 0.0f);

		assertThat(cubes.vertexCount()).isEqualTo(8);
		assertThat(cubes.droppedFaces()).isEqualTo(4);

		assertRectAt(cubes, 0, 0.0f, 2.0f, 0.0f, 2.0f);
		assertRectAt(cubes, 4, 4.0f, 10.0f, 5.0f, 12.0f);
	}

	@Test
	@DisplayName("uv_rotation turns the rectangle a quarter turn at a time")
	void uvRotationRotatesTheCorners() {
		FaceUv rect = new FaceUv(new float[] { 4.0f, 5.0f }, new float[] { 6.0f, 7.0f }, 0);
		FaceUv turned = new FaceUv(new float[] { 4.0f, 5.0f }, new float[] { 6.0f, 7.0f }, 90);

		float[] plain = emit(new Cube(new float[3], new float[] { 1.0f, 2.0f, 3.0f }, null, null, null,
				null, null, Map.of(Face.NORTH, rect)), new float[3], false, 0.0f).texCoords();
		float[] rotated = emit(new Cube(new float[3], new float[] { 1.0f, 2.0f, 3.0f }, null, null, null,
				null, null, Map.of(Face.NORTH, turned)), new float[3], false, 0.0f).texCoords();

		for (int corner = 0; corner < 4; corner++) {
			int from = (corner + 1) % 4;
			assertThat(rotated[corner * 2]).isCloseTo(plain[from * 2], within(1e-7f));
			assertThat(rotated[corner * 2 + 1]).isCloseTo(plain[from * 2 + 1], within(1e-7f));
		}
	}

	@Test
	@DisplayName("a cube with a zero extent becomes a plane, not four zero-area triangles")
	void flatCubeKeepsOnlyItsTwoRealFaces() {
		Cube flat = new Cube(new float[3], new float[] { 0.0f, 4.0f, 6.0f }, null, null, null, null,
				new float[] { 0.0f, 0.0f }, null);
		BedrockCubes cubes = emit(flat, new float[3], false, 0.0f);

		assertThat(cubes.vertexCount()).isEqualTo(8);
		assertThat(cubes.droppedFaces()).isEqualTo(4);
		assertThat(cubes.indices()).hasSize(12);

		float[] positions = cubes.positions();
		for (int v = 0; v < 8; v++) {
			assertThat(positions[v * 3]).isCloseTo(0.0f, within(1e-7f));
		}
	}

	@Test
	@DisplayName("an inflated plane is a box again, so inflate reaches the drop test")
	void inflatedFlatCubeIsSolid() {
		Cube flat = new Cube(new float[3], new float[] { 0.0f, 4.0f, 6.0f }, null, null, null, null,
				new float[] { 0.0f, 0.0f }, null);

		assertThat(emit(flat, new float[3], false, 0.5f).vertexCount()).isEqualTo(24);
		assertThat(emit(flat, new float[3], false, 0.5f).droppedFaces()).isZero();
	}

	@Test
	@DisplayName("an inflate that shrinks a cube past nothing collapses it rather than inverting it")
	void overNegativeInflateCollapses() {
		Cube cube = new Cube(new float[3], new float[] { 2.0f, 16.0f, 16.0f }, null, null, null, null,
				new float[] { 0.0f, 0.0f }, null);
		BedrockCubes cubes = emit(cube, new float[3], false, -4.0f);

		assertThat(cubes.vertexCount()).isEqualTo(8);
		assertThat(cubes.droppedFaces()).isEqualTo(4);

		float[] positions = cubes.positions();
		for (int v = 0; v < cubes.vertexCount(); v++) {
			assertThat(positions[v * 3]).as("the collapsed axis is a single plane, not a negative box")
					.isCloseTo(2.0f / 16.0f, within(1e-6f));
		}
	}

	@Test
	@DisplayName("a cube with no extent at all contributes nothing")
	void fullyDegenerateCubeIsDropped() {
		Cube nothing = new Cube(new float[3], new float[3], null, null, null, null,
				new float[] { 0.0f, 0.0f }, null);
		BedrockCubes cubes = emit(nothing, new float[3], false, 0.0f);

		assertThat(cubes.vertexCount()).isZero();
		assertThat(cubes.droppedFaces()).isEqualTo(6);
	}

	@Test
	@DisplayName("two cubes on one bone are one buffer with rebased indices")
	void cubesAccumulate() {
		BedrockCubes cubes = new BedrockCubes();
		cubes.add(boxUvCube(null), new float[3], false, 0.0f, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		cubes.add(boxUvCube(null), new float[3], false, 0.0f, TEXTURE_WIDTH, TEXTURE_HEIGHT);

		assertThat(cubes.vertexCount()).isEqualTo(48);
		assertThat(cubes.indices()).hasSize(72);

		int[] indices = cubes.indices();
		for (int i = 36; i < 72; i++) {
			assertThat(indices[i]).as("the second cube's triangles use the second cube's vertices")
					.isGreaterThanOrEqualTo(24);
		}
		for (int index : indices) {
			assertThat(index).isBetween(0, 47);
		}
	}

	private static float[] edge(float[] positions, int from, int to) {
		return new float[] {
				positions[to * 3] - positions[from * 3],
				positions[to * 3 + 1] - positions[from * 3 + 1],
				positions[to * 3 + 2] - positions[from * 3 + 2],
		};
	}

	private static void assertRect(BedrockCubes cubes, Face face, float u0, float u1, float v0, float v1) {
		assertRectAt(cubes, base(face), u0, u1, v0, v1);
	}

	private static void assertRectAt(BedrockCubes cubes, int firstVertex, float u0, float u1, float v0,
			float v1) {
		float[] uv = cubes.texCoords();
		float minU = Float.MAX_VALUE;
		float maxU = -Float.MAX_VALUE;
		float minV = Float.MAX_VALUE;
		float maxV = -Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			minU = Math.min(minU, uv[(firstVertex + i) * 2]);
			maxU = Math.max(maxU, uv[(firstVertex + i) * 2]);
			minV = Math.min(minV, uv[(firstVertex + i) * 2 + 1]);
			maxV = Math.max(maxV, uv[(firstVertex + i) * 2 + 1]);
		}

		assertThat(minU * TEXTURE_WIDTH).isCloseTo(u0, within(1e-3f));
		assertThat(maxU * TEXTURE_WIDTH).isCloseTo(u1, within(1e-3f));
		assertThat(minV * TEXTURE_HEIGHT).isCloseTo(v0, within(1e-3f));
		assertThat(maxV * TEXTURE_HEIGHT).isCloseTo(v1, within(1e-3f));
	}
}
