package com.wf.gemrender.texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AtlasLayout {
	public record Sprite(int x, int y, int width, int height) {
	}

	private final int width;
	private final int height;
	private final Sprite[] sprites;
	private final Sprite[] cells;

	private AtlasLayout(int width, int height, Sprite[] sprites, Sprite[] cells) {
		this.width = width;
		this.height = height;
		this.sprites = sprites;
		this.cells = cells;
	}

	public static AtlasLayout pack(int[] widths, int[] heights, int padding, int maxSize) {
		return pack(widths, heights, padding, maxSize, 1);
	}

	public static AtlasLayout pack(int[] widths, int[] heights, int padding, int maxSize,
			int alignment) {
		if (widths.length != heights.length) {
			throw new IllegalArgumentException("widths and heights must be the same length");
		}
		if (alignment < 1) {
			throw new IllegalArgumentException("alignment must be at least 1");
		}
		if (widths.length == 0) {
			return null;
		}

		List<Integer> order = new ArrayList<>(widths.length);
		long area = 0;
		int widestCell = 1;
		for (int i = 0; i < widths.length; i++) {
			if (widths[i] <= 0 || heights[i] <= 0) {
				return null;
			}
			area += (long) align(widths[i] + 2 * padding, alignment)
					* align(heights[i] + 2 * padding, alignment);
			widestCell = Math.max(widestCell, align(widths[i] + 2 * padding, alignment));
			order.add(i);
		}

		order.sort(Comparator.<Integer>comparingInt(i -> -heights[i])
				.thenComparingInt(i -> i));

		if (widestCell > maxSize) {
			return null;
		}

		int sheetWidth = Math.max(widestCell, (int) Math.ceil(Math.sqrt(area)));
		for (; sheetWidth <= maxSize; sheetWidth *= 2) {
			AtlasLayout packed = tryPack(widths, heights, order, padding, sheetWidth, maxSize, alignment);
			if (packed != null) {
				return packed;
			}
		}
		return null;
	}

	private static int align(int value, int alignment) {
		return alignment <= 1 ? value : (value + alignment - 1) / alignment * alignment;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int count() {
		return sprites.length;
	}

	public Sprite sprite(int index) {
		return sprites[index];
	}

	public Sprite cell(int index) {
		return cells[index];
	}

	public SpriteUv uv(int index) {
		Sprite sprite = sprites[index];
		return new SpriteUv(sprite.x() / (float) width, sprite.y() / (float) height,
				sprite.width() / (float) width, sprite.height() / (float) height);
	}

	public float occupancy() {
		long used = 0;
		for (Sprite sprite : sprites) {
			used += (long) sprite.width() * sprite.height();
		}
		return used / (float) ((long) width * height);
	}

	private static AtlasLayout tryPack(int[] widths, int[] heights, List<Integer> order, int padding,
			int sheetWidth, int maxSize, int alignment) {
		Sprite[] placed = new Sprite[widths.length];
		Sprite[] cells = new Sprite[widths.length];

		int shelfY = 0;
		int shelfHeight = 0;
		int cursorX = 0;
		int usedWidth = 0;

		for (int index : order) {
			int cellWidth = align(widths[index] + 2 * padding, alignment);
			int cellHeight = align(heights[index] + 2 * padding, alignment);

			if (cellWidth > sheetWidth) {
				return null;
			}
			if (cursorX + cellWidth > sheetWidth) {
				shelfY += shelfHeight;
				shelfHeight = 0;
				cursorX = 0;
			}

			placed[index] = new Sprite(cursorX + padding, shelfY + padding, widths[index], heights[index]);
			cells[index] = new Sprite(cursorX, shelfY, cellWidth, cellHeight);
			cursorX += cellWidth;
			shelfHeight = Math.max(shelfHeight, cellHeight);
			usedWidth = Math.max(usedWidth, cursorX);
		}

		int sheetHeight = shelfY + shelfHeight;
		if (sheetHeight > maxSize) {
			return null;
		}

		return new AtlasLayout(usedWidth, sheetHeight, placed, cells);
	}
}
