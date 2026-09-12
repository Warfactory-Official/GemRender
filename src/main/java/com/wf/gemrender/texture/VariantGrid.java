package com.wf.gemrender.texture;

import org.jetbrains.annotations.Nullable;

public record VariantGrid(int cols, int rows, int bands) {
    public VariantGrid {
        if (cols < 1 || rows < 1 || bands < 1) {
            throw new IllegalArgumentException("a variant grid is at least 1x1 in 1 band, not " + cols + "x"
                    + rows + " in " + bands);
        }
    }

    public static VariantGrid single(int bands) {
        return new VariantGrid(1, 1, bands);
    }

    @Nullable
    public static VariantGrid fit(int wanted, int tileWidth, int tileHeight, int bands, int maxSize) {
        VariantGrid best = null;
        long bestArea = Long.MAX_VALUE;

        for (int cols = 1; cols <= Math.max(1, wanted); cols++) {
            int rows = (Math.max(1, wanted) + cols - 1) / cols;

            long width = (long) tileWidth * cols;
            long height = (long) tileHeight * rows * bands;
            if (width > maxSize || height > maxSize) {
                continue;
            }

            long area = width * height;
            if (area < bestArea) {
                bestArea = area;
                best = new VariantGrid(cols, rows, bands);
            }
        }
        return best;
    }

    public int capacity() {
        return cols * rows;
    }

    public int tilesDown() {
        return rows * bands;
    }

    public int sheetWidth(int tileWidth) {
        return tileWidth * cols;
    }

    public int sheetHeight(int tileHeight) {
        return tileHeight * tilesDown();
    }

    public int column(int variant) {
        return variant % cols;
    }

    public int row(int variant) {
        return variant / cols;
    }

    public int tileX(int variant, int tileWidth) {
        return column(variant) * tileWidth;
    }

    public int tileY(int variant, int band, int tileHeight) {
        return (band * rows + row(variant)) * tileHeight;
    }

    public VariantUv offset(int variant) {
        return new VariantUv(column(variant) / (float) cols, row(variant) / (float) tilesDown());
    }

    public SpriteUv onSheet(SpriteUv tileUv) {
        return new SpriteUv(tileUv.uOffset() / cols, tileUv.vOffset() / tilesDown(),
                tileUv.uScale() / cols, tileUv.vScale() / tilesDown());
    }
}
