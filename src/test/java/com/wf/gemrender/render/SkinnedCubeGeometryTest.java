package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkinnedCubeGeometryTest {
	@Test
	@DisplayName("every face is wound counter-clockwise seen from outside")
	void windingAgreesWithTheDeclaredNormal() {
		for (float[] face : SkinnedCubeGeometry.FACES) {
			Vector3f normal = new Vector3f(face[0], face[1], face[2]);

			assertWinding(face, normal, 0, 1, 2);
			assertWinding(face, normal, 0, 2, 3);
		}
	}

	@Test
	@DisplayName("the six faces are the six axis directions, once each")
	void everyFaceIsPresent() {
		Vector3f sum = new Vector3f();
		for (float[] face : SkinnedCubeGeometry.FACES) {
			sum.add(face[0], face[1], face[2]);
		}

		assertThat(SkinnedCubeGeometry.FACES.length).isEqualTo(6);
		assertThat(sum.length()).as("normals of a closed box cancel")
				.isLessThan(1e-6f);
	}

	@Test
	@DisplayName("every corner sits on the face its normal names")
	void cornersLieInTheirFacesPlane() {
		for (float[] face : SkinnedCubeGeometry.FACES) {
			Vector3f normal = new Vector3f(face[0], face[1], face[2]);

			float expected = corner(face, 0).dot(normal);
			for (int c = 1; c < 4; c++) {
				assertThat(corner(face, c).dot(normal))
						.as("corner %d of face %s lies in the same plane", c, normal)
						.isEqualTo(expected, within(1e-6f));
			}
		}
	}

	@Test
	@DisplayName("the two halves are split where the joints are")
	void bothJointsGetVertices() {
		int below = 0;
		int above = 0;
		for (float[] face : SkinnedCubeGeometry.FACES) {
			for (int c = 0; c < 4; c++) {
				if (corner(face, c).y > 0.5f) {
					above++;
				} else {
					below++;
				}
			}
		}

		assertThat(below).as("vertices on the lower joint")
				.isEqualTo(12);
		assertThat(above).as("vertices on the upper joint")
				.isEqualTo(12);
	}

	private static void assertWinding(float[] face, Vector3f normal, int a, int b, int c) {
		Vector3f edge1 = corner(face, b).sub(corner(face, a));
		Vector3f edge2 = corner(face, c).sub(corner(face, a));
		Vector3f geometric = edge1.cross(edge2);

		assertThat(geometric.dot(normal))
				.as("triangle (%d,%d,%d) of face %s faces the way its normal says", a, b, c, normal)
				.isGreaterThan(0.0f);
	}

	private static Vector3f corner(float[] face, int index) {
		int p = 3 + index * 3;
		return new Vector3f(face[p], face[p + 1], face[p + 2]);
	}
}
