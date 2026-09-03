package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.SpriteUv;

class PartMergeTest {
	private static final int SHEET = 64;

	private static MeshGeometry quad(int u, int v, int width, int height) {
		float[] positions = { 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0 };
		float[] normals = { 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1 };
		float[] uvs = {
				u / (float) SHEET, v / (float) SHEET,
				(u + width) / (float) SHEET, v / (float) SHEET,
				(u + width) / (float) SHEET, (v + height) / (float) SHEET,
				u / (float) SHEET, (v + height) / (float) SHEET };
		int[] indices = { 0, 1, 2, 0, 2, 3 };

		return MeshGeometry.of(positions, normals, uvs, indices, VertexSkinning.rigid(4, 0),
				SpriteUv.IDENTITY, 0);
	}

	private static PartMerge.Pixels tiles(int... tileColours) {
		return new PartMerge.Pixels() {
			@Override
			public int width() {
				return SHEET;
			}

			@Override
			public int height() {
				return SHEET;
			}

			@Override
			public int argb(int x, int y) {
				int tile = y / 8 * (SHEET / 8) + x / 8;
				return tile < tileColours.length ? tileColours[tile] : 0xFF000000;
			}
		};
	}

	@Test
	void twoRectanglesHoldingTheSamePixelsAreTheSameMesh() {
		PartMerge.Pixels sheet = tiles(0xFF112233, 0xFF112233);

		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(8, 0, 8, 8), sheet)).isTrue();
	}

	@Test
	void twoRectanglesHoldingDifferentPixelsAreNot() {
		PartMerge.Pixels sheet = tiles(0xFF112233, 0xFF445566);

		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(8, 0, 8, 8), sheet)).isFalse();
	}

	@Test
	void withoutTheSheetOnlyIdenticalUvsMerge() {
		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(0, 0, 8, 8), null)).isTrue();
		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(8, 0, 8, 8), null)).isFalse();
	}

	@Test
	void aRectangleOfADifferentSizeNeverMerges() {
		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(8, 0, 16, 8), tiles(new int[64]))).isFalse();
	}

	@Test
	void aFlippedUnwrapNeverMergesWithAPlainOne() {
		MeshGeometry plain = quad(0, 0, 8, 8);
		float[] flipped = {
				8 / (float) SHEET, 0, 0, 0, 0, 8 / (float) SHEET, 8 / (float) SHEET, 8 / (float) SHEET };
		MeshGeometry mirrored = MeshGeometry.of(
				new float[] { 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0 },
				new float[] { 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1 },
				flipped, new int[] { 0, 1, 2, 0, 2, 3 }, VertexSkinning.rigid(4, 0), SpriteUv.IDENTITY, 0);

		assertThat(PartMerge.matches(plain, mirrored, tiles(new int[64]))).isFalse();
	}

	@Test
	void fullyTransparentPixelsCountAsEqualWhateverIsUnderThem() {
		PartMerge.Pixels sheet = tiles(0x00FF0000, 0x0000FF00);

		assertThat(PartMerge.matches(quad(0, 0, 8, 8), quad(8, 0, 8, 8), sheet)).isTrue();
	}

	@Test
	void canonicalPointsEveryDuplicateAtTheFirstOfItsKind() {
		PartMerge.Pixels sheet = tiles(0xFF112233, 0xFF112233, 0xFF445566, 0xFF112233);

		List<MeshGeometry> meshes = List.of(quad(0, 0, 8, 8), quad(8, 0, 8, 8), quad(16, 0, 8, 8),
				quad(24, 0, 8, 8));

		assertThat(PartMerge.canonical(meshes, sheet)).containsExactly(0, 0, 2, 0);
	}

	@Test
	void aPartWithoutGeometryMapsToItself() {
		assertThat(PartMerge.canonical(Arrays.asList(quad(0, 0, 8, 8), null, quad(0, 0, 8, 8)), null))
				.containsExactly(0, 1, 0);
	}
}
