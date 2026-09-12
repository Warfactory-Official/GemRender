package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VariantGridTest {
	private static final int MAX = 4096;

	@Test
	@DisplayName("a model with no variants is one tile, however many bands it has")
	void single() {
		assertThat(VariantGrid.single(3)).isEqualTo(new VariantGrid(1, 1, 3));
		assertThat(VariantGrid.single(3)
				.offset(0)).isEqualTo(VariantUv.NONE);
		assertThat(VariantGrid.single(1)
				.onSheet(SpriteUv.IDENTITY)).isEqualTo(SpriteUv.IDENTITY);
	}

	@Test
	@DisplayName("a variant offset and a band offset compose to the tile's own position")
	void bandsAndVariantsNest() {
		for (int bands : new int[] { 1, 3 }) {
			for (int cols = 1; cols <= 4; cols++) {
				for (int rows = 1; rows <= 3; rows++) {
					VariantGrid grid = new VariantGrid(cols, rows, bands);

					for (int variant = 0; variant < grid.capacity(); variant++) {
						for (int band = 0; band < bands; band++) {
							float composed = grid.offset(variant)
									.v() + band / (float) bands;

							float blitted = grid.tileY(variant, band, 64)
									/ (float) grid.sheetHeight(64);

							assertThat(composed)
									.as("grid %dx%d in %d band(s), variant %d band %d", cols, rows, bands,
											variant, band)
									.isEqualTo(blitted, within(1.0e-6f));
						}
					}
				}
			}
		}
	}

	@Test
	@DisplayName("a mesh's coordinates land inside variant zero's tile")
	void meshCoordinatesStayInTileZero() {
		VariantGrid grid = new VariantGrid(3, 2, 3);

		SpriteUv onSheet = grid.onSheet(SpriteUv.IDENTITY);

		assertThat(onSheet.u(0.0f)).isEqualTo(0.0f);
		assertThat(onSheet.u(1.0f)).isEqualTo(1.0f / 3.0f, within(1.0e-6f));
		assertThat(onSheet.v(0.0f)).isEqualTo(0.0f);
		assertThat(onSheet.v(1.0f)).isEqualTo(1.0f / 6.0f, within(1.0e-6f));
	}

	@Test
	@DisplayName("a coordinate offset to a variant lands in that variant's pixels")
	void offsetLandsInTheRightTile() {
		int tile = 64;
		VariantGrid grid = new VariantGrid(3, 2, 3);
		SpriteUv onSheet = grid.onSheet(SpriteUv.IDENTITY);

		int width = grid.sheetWidth(tile);
		int height = grid.sheetHeight(tile);

		for (int variant = 0; variant < grid.capacity(); variant++) {
			for (int band = 0; band < grid.bands(); band++) {

				float u = onSheet.u(0.5f) + grid.offset(variant)
						.u();
				float v = onSheet.v(0.5f) + grid.offset(variant)
						.v() + band / (float) grid.bands();

				int x = (int) (u * width);
				int y = (int) (v * height);

				assertThat(x)
						.as("variant %d band %d, x", variant, band)
						.isBetween(grid.tileX(variant, tile), grid.tileX(variant, tile) + tile - 1);
				assertThat(y)
						.as("variant %d band %d, y", variant, band)
						.isBetween(grid.tileY(variant, band, tile),
								grid.tileY(variant, band, tile) + tile - 1);
			}
		}
	}

	@Test
	@DisplayName("distinct variants occupy distinct tiles")
	void variantsDoNotOverlap() {
		VariantGrid grid = new VariantGrid(3, 2, 3);

		for (int a = 0; a < grid.capacity(); a++) {
			for (int b = a + 1; b < grid.capacity(); b++) {
				assertThat(grid.offset(a))
						.as("variants %d and %d", a, b)
						.isNotEqualTo(grid.offset(b));
			}
		}
	}

	@Test
	@DisplayName("the fit is the smallest sheet that holds them")
	void fitPicksTheSmallestSheet() {

		VariantGrid mob = VariantGrid.fit(6, 68, 68, 1, MAX);
		assertThat(mob).isNotNull();
		assertThat(mob.capacity()).isGreaterThanOrEqualTo(6);
		assertThat(mob.sheetWidth(68)).isLessThanOrEqualTo(MAX);
		assertThat(mob.sheetHeight(68)).isLessThanOrEqualTo(MAX);
	}

	@Test
	@DisplayName("a banded sheet runs out of height, so the fit has to be wide")
	void bandedSheetsGoWide() {

		VariantGrid three = VariantGrid.fit(3, 1028, 1028, 3, MAX);
		assertThat(three)
				.as("three variants fit across, at 3084x3084")
				.isEqualTo(new VariantGrid(3, 1, 3));

		assertThat(VariantGrid.fit(4, 1028, 1028, 3, MAX))
				.as("a fourth needs either 4112 across or 6168 down, and the packer declines rather than "
						+ "quietly dropping one")
				.isNull();
	}

	@Test
	@DisplayName("one variant of an oversized tile does not fit either")
	void tileItselfCanBeTooBig() {
		assertThat(VariantGrid.fit(1, 4096, 2048, 3, MAX)).isNull();
		assertThat(VariantGrid.fit(1, 4096, 4096, 1, MAX)).isEqualTo(new VariantGrid(1, 1, 1));
	}

	@Test
	@DisplayName("a grid is at least one tile in one band")
	void degenerateGridsAreRefused() {
		assertThatThrownBy(() -> new VariantGrid(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VariantGrid(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
