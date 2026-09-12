package com.wf.gemrender.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.mixin.texture.NativeImageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public final class ModelAtlas {
    public static final int PADDING = 2;

    public static final int MAX_SIZE = 4096;

    private static final boolean DUMP = Boolean.getBoolean("gemrender.dumpatlas");

    private static final boolean COMPRESS =
            !"false".equalsIgnoreCase(System.getProperty("gemrender.compress", "true"));

    private static final int MIN_COMPRESSED_AREA = 256 * 256;

    private static final Semaphore ENCODERS = new Semaphore(1);

    private static boolean warnedAboutPixels;

    private final ResourceLocation texture;
    private final Map<MaterialMaps, SpriteUv> sprites;
    private final int width;
    private final int height;
    private final int bands;
    private final List<VariantUv> variants;

    private ModelAtlas(ResourceLocation texture, Map<MaterialMaps, SpriteUv> sprites,
                       int width, int height, int bands, List<VariantUv> variants) {
        this.texture = texture;
        this.sprites = sprites;
        this.width = width;
        this.height = height;
        this.bands = bands;
        this.variants = List.copyOf(variants);
    }

    public static ModelAtlas stitch(ResourceLocation atlasId, List<MaterialMaps> materials) {
        return stitch(atlasId, materials, List.of(Map.of()));
    }

    public static ModelAtlas stitch(ResourceLocation atlasId, List<MaterialMaps> materials,
                                    List<Map<ResourceLocation, ResourceLocation>> variants) {
        int wanted = Math.max(1, variants.size());

        int bands = 1;
        for (Map<ResourceLocation, ResourceLocation> swaps : variants) {
            for (MaterialMaps material : materials) {
                if (material.swapped(swaps)
                        .pbr()) {
                    bands = SurfaceBake.BANDS;
                    break;
                }
            }
        }

        if (materials.size() < 2 && bands == 1 && wanted < 2) {
            return null;
        }

        List<SurfaceBake> bakes = new ArrayList<>(materials.size());
        NativeImage sheet = null;
        try {
            for (MaterialMaps material : materials) {
                SurfaceBake bake = SurfaceBake.of(material.swapped(variants.get(0)), bands > 1);
                if (bake == null) {
                    GemRender.LOGGER.warn("Not atlasing {}: could not read {}", atlasId, material.baseColor());
                    return null;
                }
                bakes.add(bake);
            }

            int[] widths = new int[bakes.size()];
            int[] heights = new int[bakes.size()];
            for (int i = 0; i < bakes.size(); i++) {
                widths[i] = bakes.get(i)
                        .width();
                heights[i] = bakes.get(i)
                        .height();
            }

            boolean alignable = COMPRESS && bands == 1;
            AtlasLayout layout = AtlasLayout.pack(widths, heights, PADDING, MAX_SIZE,
                    alignable ? BlockCompressor.BLOCK : 1);
            if (layout == null) {
                GemRender.LOGGER.info("Not atlasing {}: its {} materials do not fit in {}px", atlasId,
                        materials.size(), MAX_SIZE);
                return null;
            }

            VariantGrid grid = VariantGrid.fit(wanted, layout.width(), layout.height(), bands, MAX_SIZE);
            if (grid == null) {
                GemRender.LOGGER.info("Not atlasing {}: {} variant(s) of a {}x{} tile in {} band(s) do not "
                        + "fit in {}px", atlasId, wanted, layout.width(), layout.height(), bands, MAX_SIZE);
                return null;
            }

            int width = grid.sheetWidth(layout.width());
            int height = grid.sheetHeight(layout.height());

            sheet = new NativeImage(width, height, false);

            Map<MaterialMaps, SpriteUv> uvs = new LinkedHashMap<>();
            for (int i = 0; i < bakes.size(); i++) {
                uvs.put(materials.get(i), grid.onSheet(layout.uv(i)));
            }

            List<VariantUv> offsets = new ArrayList<>(wanted);
            for (int variant = 0; variant < wanted; variant++) {
                offsets.add(grid.offset(variant));

                if (variant > 0) {
                    for (SurfaceBake bake : bakes) {
                        bake.close();
                    }
                    bakes.clear();
                    for (MaterialMaps material : materials) {
                        SurfaceBake bake = SurfaceBake.of(material.swapped(variants.get(variant)), bands > 1);
                        if (bake == null) {
                            throw new IllegalArgumentException(atlasId + " variant " + variant
                                    + " could not read a texture for " + material.baseColor());
                        }
                        bakes.add(bake);
                    }
                    checkShape(atlasId, variant, materials, bakes, widths, heights);
                }

                for (int i = 0; i < bakes.size(); i++) {
                    for (int band = 0; band < bands; band++) {
                        blit(bakes.get(i)
                                        .band(band), sheet, layout.sprite(i), layout.cell(i),
                                grid.tileX(variant, layout.width()),
                                grid.tileY(variant, band, layout.height()));
                    }
                }
            }

            dump(atlasId, sheet);

            boolean compressed = alignable
                    && (long) width * height >= MIN_COMPRESSED_AREA
                    && compress(atlasId, sheet, width, height);

            if (!compressed) {
                register(atlasId, sheet, bands);

                GemRender.LOGGER.info(
                        "Atlased {} materials x {} variant(s) into {} ({}x{} in {} band(s), {}% occupied, "
                                + "{} KB)",
                        materials.size(), wanted, atlasId, width, height, bands,
                        Math.round(layout.occupancy() * 100), (long) width * height * 4 / 1024);
            }
            sheet = null;

            return new ModelAtlas(atlasId, uvs, width, height, bands, offsets);
        } finally {
            for (SurfaceBake bake : bakes) {
                bake.close();
            }
            if (sheet != null) {
                sheet.close();
            }
        }
    }

    private static void checkShape(ResourceLocation atlasId, int variant, List<MaterialMaps> materials,
                                   List<SurfaceBake> bakes, int[] widths, int[] heights) {
        for (int i = 0; i < bakes.size(); i++) {
            SurfaceBake bake = bakes.get(i);
            if (bake.width() != widths[i] || bake.height() != heights[i]) {
                throw new IllegalArgumentException(atlasId + " variant " + variant + " bakes "
                        + materials.get(i)
                        .baseColor()
                        + " at " + bake.width() + "x" + bake.height() + ", but variant 0 is " + widths[i]
                        + "x" + heights[i] + ". Every variant shares one packed layout, so its textures "
                        + "have to be the same size as the first one's.");
            }
        }
    }

    private static void dump(ResourceLocation atlasId, NativeImage sheet) {
        if (!DUMP) {
            return;
        }
        try {
            java.io.File out = new java.io.File(Minecraft.getInstance().gameDirectory,
                    "gemrender_atlases/" + atlasId.getPath()
                            .replace('/', '_') + ".png");
            out.getParentFile()
                    .mkdirs();
            sheet.writeToFile(out);
            GemRender.LOGGER.info("Dumped atlas to {}", out);
        } catch (IOException e) {
            GemRender.LOGGER.warn("Could not dump atlas {}", atlasId, e);
        }
    }

    private static boolean compress(ResourceLocation atlasId, NativeImage sheet, int width,
                                    int height) {
        try {
            long start = System.nanoTime();

            ByteBuffer pixels = pixels(sheet, width, height);
            BlockCache cache = BlockCache.instance();
            String key = BlockCache.key(width, height, pixels);

            BlockCompressor.Blocks blocks = cache.read(key);
            boolean cached = blocks != null;
            if (!cached) {
                byte[] rgba = new byte[pixels.remaining()];
                pixels.duplicate()
                        .get(rgba);
                blocks = encode(width, height, rgba);
                cache.write(key, blocks);
            }

            long millis = (System.nanoTime() - start) / 1_000_000L;

            CompressedTexture texture = new CompressedTexture(blocks);
            onRenderThread(() -> {
                texture.upload();
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(atlasId, texture);
            });

            sheet.close();

            GemRender.LOGGER.info("Atlased into {} ({}x{}, BC7, {} KB, down from {} KB, {} in {} ms)",
                    atlasId, width, height, blocks.data().length / 1024,
                    blocks.uncompressedBytes() / 1024, cached ? "read from the block cache" : "encoded",
                    millis);
            return true;
        } catch (IOException | RuntimeException e) {
            GemRender.LOGGER.warn("Could not compress {}, keeping it as RGBA8 ({})", atlasId, e.toString());
            return false;
        }
    }

    private static BlockCompressor.Blocks encode(int width, int height, byte[] rgba) throws IOException {
        ENCODERS.acquireUninterruptibly();
        try {
            return BlockCompressor.toBc7(width, height, rgba);
        } finally {
            ENCODERS.release();
        }
    }

    private static ByteBuffer pixels(NativeImage sheet, int width, int height) {
        long expected = (long) width * height * KtxImage.BYTES_PER_PIXEL;
        try {
            NativeImageAccessor accessor = (NativeImageAccessor) (Object) sheet;
            long address = accessor.gemrender$pixels();
            if (address != 0L && accessor.gemrender$size() == expected && expected <= Integer.MAX_VALUE) {
                return MemoryUtil.memByteBuffer(address, (int) expected);
            }
        } catch (Throwable e) {
            if (!warnedAboutPixels) {
                warnedAboutPixels = true;
                GemRender.LOGGER.warn("Reading atlas pixels a row at a time; NativeImageAccessor did not "
                        + "apply ({})", e.toString());
            }
        }
        return ByteBuffer.wrap(rgba(sheet, width, height));
    }

    private static byte[] rgba(NativeImage sheet, int width, int height) {
        byte[] out = new byte[width * height * KtxImage.BYTES_PER_PIXEL];

        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = Pixels.get(sheet, x, y);
                out[i] = (byte) pixel;
                out[i + 1] = (byte) (pixel >> 8);
                out[i + 2] = (byte) (pixel >> 16);
                out[i + 3] = (byte) (pixel >>> 24);
                i += KtxImage.BYTES_PER_PIXEL;
            }
        }
        return out;
    }

    private static void register(ResourceLocation atlasId, NativeImage sheet, int bands) {
        onRenderThread(() -> Minecraft.getInstance()
                .getTextureManager()
                .register(atlasId, new AtlasTexture(sheet, bands)));
    }

    private static void onRenderThread(Runnable work) {
        if (RenderSystem.isOnRenderThread()) {
            work.run();
            return;
        }
        //? if >=26.1 {
        /*Minecraft.getInstance().execute(work);
         *///?} else {
        RenderSystem.recordRenderCall(work::run);
        //?}
    }

    private static void blit(NativeImage from, NativeImage sheet, AtlasLayout.Sprite sprite,
                             AtlasLayout.Sprite cell, int xOffset, int yOffset) {
        for (int y = cell.y(); y < cell.y() + cell.height(); y++) {
            for (int x = cell.x(); x < cell.x() + cell.width(); x++) {
                int sx = Mth.clamp(x - sprite.x(), 0, sprite.width() - 1);
                int sy = Mth.clamp(y - sprite.y(), 0, sprite.height() - 1);
                Pixels.set(sheet, xOffset + x, yOffset + y, Pixels.get(from, sx, sy));
            }
        }
    }

    public ResourceLocation texture() {
        return texture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int bands() {
        return bands;
    }

    public List<VariantUv> variants() {
        return variants;
    }

    public SpriteUv uv(MaterialMaps material) {
        return sprites.getOrDefault(material, SpriteUv.IDENTITY);
    }

    public boolean contains(MaterialMaps material) {
        return sprites.containsKey(material);
    }
}
