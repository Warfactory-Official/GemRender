package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AtlasLayoutTest {
	private static final int PADDING = ModelAtlas.PADDING;

	@Test
	@DisplayName("the radar's three 1024s tile without being rounded up to 4096")
	void packsTheRadar() {
		AtlasLayout layout = square(1024, 3);
		assertThat(layout).isNotNull();
		long bytes = (long) layout.width() * layout.height() * 4;
		long separately = 3L * 1024 * 1024 * 4;

		assertThat(bytes)
				.as("a %dx%d sheet, against the %d MB the three textures cost separately",
						layout.width(), layout.height(), separately / (1024 * 1024))
				.isLessThan(separately * 5 / 4);
		assertThat(layout.occupancy()).isGreaterThan(0.7f);
		assertThat(Math.max(layout.width(), layout.height())).isLessThanOrEqualTo(ModelAtlas.MAX_SIZE);
	}

	@Test
	@DisplayName("a lone sprite gets a sheet its own size, not a square one")
	void oneSpriteIsNotPaddedIntoASquare() {
		AtlasLayout layout = AtlasLayout.pack(new int[] { 256 }, new int[] { 16 }, PADDING,
				ModelAtlas.MAX_SIZE);

		assertThat(layout.width()).isEqualTo(256 + 2 * PADDING);
		assertThat(layout.height()).isEqualTo(16 + 2 * PADDING);
	}

	@Test
	@DisplayName("no two sprites overlap, gutters included")
	void spritesAndTheirGuttersAreDisjoint() {
		int[] widths = { 512, 256, 256, 128, 64, 1024, 32, 200 };
		int[] heights = { 256, 256, 128, 128, 64, 512, 32, 100 };

		AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);
		assertThat(layout).isNotNull();

		for (int a = 0; a < widths.length; a++) {
			AtlasLayout.Sprite first = padded(layout.sprite(a));
			assertThat(first.x()).isGreaterThanOrEqualTo(0);
			assertThat(first.y()).isGreaterThanOrEqualTo(0);
			assertThat(first.x() + first.width()).isLessThanOrEqualTo(layout.width());
			assertThat(first.y() + first.height()).isLessThanOrEqualTo(layout.height());

			for (int b = a + 1; b < widths.length; b++) {
				AtlasLayout.Sprite second = padded(layout.sprite(b));
				assertThat(overlaps(first, second))
						.as("sprites %d %s and %d %s overlap", a, first, b, second)
						.isFalse();
			}
		}
	}

	@Test
	@DisplayName("every sprite keeps its own size")
	void spritesAreNotResized() {
		int[] widths = { 64, 128, 32 };
		int[] heights = { 32, 128, 16 };
		AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);

		for (int i = 0; i < widths.length; i++) {
			assertThat(layout.sprite(i)
					.width()).isEqualTo(widths[i]);
			assertThat(layout.sprite(i)
					.height()).isEqualTo(heights[i]);
		}
	}

	@Test
	@DisplayName("0 and 1 map to the sprite's own edges")
	void uvSpansExactlyTheSprite() {
		AtlasLayout layout = square(256, 4);

		for (int i = 0; i < 4; i++) {
			AtlasLayout.Sprite sprite = layout.sprite(i);
			SpriteUv uv = layout.uv(i);
			float uScale = 1.0f / layout.width();
			float vScale = 1.0f / layout.height();

			assertThat(uv.u(0.0f)).isCloseTo(sprite.x() * uScale, within(1e-6f));
			assertThat(uv.v(0.0f)).isCloseTo(sprite.y() * vScale, within(1e-6f));
			assertThat(uv.u(1.0f)).isCloseTo((sprite.x() + sprite.width()) * uScale, within(1e-6f));
			assertThat(uv.v(1.0f)).isCloseTo((sprite.y() + sprite.height()) * vScale, within(1e-6f));

			assertThat(uv.u(0.5f)).isCloseTo((sprite.x() + sprite.width() * 0.5f) * uScale, within(1e-6f));
		}
	}

	@Test
	@DisplayName("a coordinate of exactly 1 samples inside the gutter, not the next sprite")
	void theRightEdgeLandsInTheGutter() {
		AtlasLayout layout = square(64, 4);

		for (int i = 0; i < 4; i++) {
			AtlasLayout.Sprite sprite = layout.sprite(i);
			int texel = (int) (layout.uv(i)
					.u(1.0f) * layout.width());

			assertThat(texel)
					.as("the texel a u of 1.0 samples, for sprite %d", i)
					.isEqualTo(sprite.x() + sprite.width());
			assertThat(texel - (sprite.x() + sprite.width()))
					.as("distance into the gutter, which must stay under the padding")
					.isLessThan(PADDING);
		}
	}

	@Test
	@DisplayName("the same sprites always pack the same way")
	void packingIsDeterministic() {
		int[] widths = { 128, 128, 128, 64, 64 };
		int[] heights = { 128, 128, 128, 64, 64 };

		AtlasLayout first = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);
		for (int attempt = 0; attempt < 5; attempt++) {
			AtlasLayout again = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);
			for (int i = 0; i < widths.length; i++) {
				assertThat(again.sprite(i)).isEqualTo(first.sprite(i));
			}
		}
	}

	@Test
	@DisplayName("packing declines rather than overflowing when it will not fit")
	void refusesWhatWillNotFit() {
		assertThat(AtlasLayout.pack(new int[] { 8192 }, new int[] { 8192 }, PADDING, 4096)).isNull();
		assertThat(square(2048, 16)).isNull();
		assertThat(square(1024, 64)).isNull();
		assertThat(AtlasLayout.pack(new int[] {}, new int[] {}, PADDING, 4096)).isNull();
	}

	@Test
	@DisplayName("a sprite exactly as wide as the sheet still needs its gutter to fit")
	void paddingIsCountedAgainstTheSheet() {
		assertThat(AtlasLayout.pack(new int[] { 4096 }, new int[] { 4096 }, PADDING, 4096)).isNull();
		assertThat(AtlasLayout.pack(new int[] { 4096 - 2 * PADDING }, new int[] { 4096 - 2 * PADDING },
				PADDING, 4096)).isNotNull();
	}

	@Test
	@DisplayName("no compression block is ever shared by two sprites")
	void alignedCellsDoNotShareBlocks() {
		int block = BlockCompressor.BLOCK;
		int[] widths = { 30, 17, 64, 5, 129, 100 };
		int[] heights = { 30, 63, 64, 5, 12, 101 };

		AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE, block);
		assertThat(layout).isNotNull();

		assertThat(layout.width() % block).as("sheet width").isZero();
		assertThat(layout.height() % block).as("sheet height").isZero();

		for (int i = 0; i < widths.length; i++) {
			AtlasLayout.Sprite cell = layout.cell(i);
			assertThat(cell.x() % block).as("cell %d x", i).isZero();
			assertThat(cell.y() % block).as("cell %d y", i).isZero();
			assertThat(cell.width() % block).as("cell %d width", i).isZero();
			assertThat(cell.height() % block).as("cell %d height", i).isZero();

			assertThat(cell.x()).isEqualTo(layout.sprite(i)
					.x() - PADDING);
			assertThat(cell.width()).isGreaterThanOrEqualTo(widths[i] + 2 * PADDING);

			for (int j = i + 1; j < widths.length; j++) {
				assertThat(overlaps(cell, layout.cell(j)))
						.as("cells %d %s and %d %s share a block", i, cell, j, layout.cell(j))
						.isFalse();
			}
		}
	}

	@Test
	@DisplayName("a cell covers its sprite and its gutter, aligned or not")
	void cellsCoverContentAndGutter() {
		int[] widths = { 64, 128, 32 };
		int[] heights = { 32, 128, 16 };
		AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);

		for (int i = 0; i < widths.length; i++) {
			AtlasLayout.Sprite sprite = layout.sprite(i);
			AtlasLayout.Sprite cell = layout.cell(i);

			assertThat(cell.x()).isEqualTo(sprite.x() - PADDING);
			assertThat(cell.y()).isEqualTo(sprite.y() - PADDING);
			assertThat(cell.width()).isEqualTo(sprite.width() + 2 * PADDING);
			assertThat(cell.height()).isEqualTo(sprite.height() + 2 * PADDING);
			assertThat(cell.x() + cell.width()).isLessThanOrEqualTo(layout.width());
			assertThat(cell.y() + cell.height()).isLessThanOrEqualTo(layout.height());
		}
	}

	@Test
	@DisplayName("alignment does not move a sprite off its own coordinates")
	void alignmentKeepsTheCoordinateMapHonest() {
		int[] widths = { 30, 17, 100 };
		int[] heights = { 30, 63, 101 };
		AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE,
				BlockCompressor.BLOCK);

		for (int i = 0; i < widths.length; i++) {
			AtlasLayout.Sprite sprite = layout.sprite(i);
			assertThat(sprite.width()).isEqualTo(widths[i]);
			assertThat(sprite.height()).isEqualTo(heights[i]);

			SpriteUv uv = layout.uv(i);
			assertThat(uv.u(0.0f)).isCloseTo(sprite.x() / (float) layout.width(), within(1e-6f));
			assertThat(uv.u(1.0f)).isCloseTo((sprite.x() + sprite.width()) / (float) layout.width(),
					within(1e-6f));
		}
	}

	private static AtlasLayout square(int edge, int count) {
		int[] widths = new int[count];
		int[] heights = new int[count];
		java.util.Arrays.fill(widths, edge);
		java.util.Arrays.fill(heights, edge);
		return AtlasLayout.pack(widths, heights, PADDING, ModelAtlas.MAX_SIZE);
	}

	private static AtlasLayout.Sprite padded(AtlasLayout.Sprite sprite) {
		return new AtlasLayout.Sprite(sprite.x() - PADDING, sprite.y() - PADDING,
				sprite.width() + 2 * PADDING, sprite.height() + 2 * PADDING);
	}

	private static boolean overlaps(AtlasLayout.Sprite a, AtlasLayout.Sprite b) {
		return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
				&& a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
	}
}
