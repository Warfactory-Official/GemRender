package com.wf.gemrender.texture;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class ModelAtlas {
	public static final int PADDING = 2;

	public static final int MAX_SIZE = 4096;

	private static final boolean DUMP = Boolean.getBoolean("gemrender.dumpatlas");

	private static final boolean COMPRESS =
			!"false".equalsIgnoreCase(System.getProperty("gemrender.compress", "true"));

	private static final int MIN_COMPRESSED_AREA = 256 * 256;

	private final ResourceLocation texture;
	private final Map<MaterialMaps, SpriteUv> sprites;
	private final int width;
	private final int height;
	private final int bands;

	private ModelAtlas(ResourceLocation texture, Map<MaterialMaps, SpriteUv> sprites,
			int width, int height, int bands) {
		this.texture = texture;
		this.sprites = sprites;
		this.width = width;
		this.height = height;
		this.bands = bands;
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

	public SpriteUv uv(MaterialMaps material) {
		return sprites.getOrDefault(material, SpriteUv.IDENTITY);
	}

	public boolean contains(MaterialMaps material) {
		return sprites.containsKey(material);
	}

	public static ModelAtlas stitch(ResourceLocation atlasId, List<MaterialMaps> materials) {
		int bands = 1;
		for (MaterialMaps material : materials) {
			if (material.pbr()) {
				bands = SurfaceBake.BANDS;
				break;
			}
		}

		if (materials.size() < 2 && bands == 1) {
			return null;
		}

		List<SurfaceBake> bakes = new ArrayList<>(materials.size());
		try {
			for (MaterialMaps material : materials) {
				SurfaceBake bake = SurfaceBake.of(material, bands > 1);
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
			if (layout == null || layout.height() * bands > MAX_SIZE) {
				GemRender.LOGGER.info("Not atlasing {}: its {} materials need {}x{} in {} band(s), "
						+ "past the {}px limit", atlasId, materials.size(),
						layout == null ? "?" : layout.width(), layout == null ? "?" : layout.height() * bands,
						bands, MAX_SIZE);
				return null;
			}

			NativeImage sheet = new NativeImage(layout.width(), layout.height() * bands, false);
			Map<MaterialMaps, SpriteUv> uvs = new LinkedHashMap<>();
			for (int i = 0; i < bakes.size(); i++) {
				SurfaceBake bake = bakes.get(i);
				for (int band = 0; band < bands; band++) {
					blit(bake.band(band), sheet, layout.sprite(i), layout.cell(i), band * layout.height());
				}

				SpriteUv uv = layout.uv(i);
				uvs.put(materials.get(i),
						new SpriteUv(uv.uOffset(), uv.vOffset() / bands, uv.uScale(), uv.vScale() / bands));
			}

			dump(atlasId, sheet);

			int height = layout.height() * bands;
			boolean compressed = alignable
					&& (long) layout.width() * height >= MIN_COMPRESSED_AREA
					&& compress(atlasId, sheet, layout.width(), height);

			if (!compressed) {
				register(atlasId, new AtlasTexture(sheet, bands));

				GemRender.LOGGER.info(
						"Atlased {} materials into {} ({}x{} in {} band(s), {}% occupied, {} KB)",
						materials.size(), atlasId, layout.width(), height, bands,
						Math.round(layout.occupancy() * 100),
						(long) layout.width() * height * 4 / 1024);
			}

			return new ModelAtlas(atlasId, uvs, layout.width(), height, bands);
		} finally {
			for (SurfaceBake bake : bakes) {
				bake.close();
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
			BlockCompressor.Blocks blocks = BlockCompressor.toBc7(width, height, rgba(sheet, width, height));
			long millis = (System.nanoTime() - start) / 1_000_000L;

			CompressedTexture texture = new CompressedTexture(blocks);
			onRenderThread(() -> {
				texture.upload();
				Minecraft.getInstance()
						.getTextureManager()
						.register(atlasId, texture);
			});

			sheet.close();

			GemRender.LOGGER.info("Atlased into {} ({}x{}, BC7, {} KB, down from {} KB, encoded in {} ms)",
					atlasId, width, height, blocks.data().length / 1024,
					blocks.uncompressedBytes() / 1024, millis);
			return true;
		} catch (IOException | RuntimeException e) {
			GemRender.LOGGER.warn("Could not compress {}, keeping it as RGBA8 ({})", atlasId, e.toString());
			return false;
		}
	}

	private static byte[] rgba(NativeImage sheet, int width, int height) {
		byte[] out = new byte[width * height * KtxImage.BYTES_PER_PIXEL];

		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = sheet.getPixelRGBA(x, y);
				out[i] = (byte) pixel;
				out[i + 1] = (byte) (pixel >> 8);
				out[i + 2] = (byte) (pixel >> 16);
				out[i + 3] = (byte) (pixel >>> 24);
				i += KtxImage.BYTES_PER_PIXEL;
			}
		}
		return out;
	}

	private static void register(ResourceLocation atlasId, AtlasTexture texture) {
		onRenderThread(() -> Minecraft.getInstance()
				.getTextureManager()
				.register(atlasId, texture));
	}

	private static void onRenderThread(Runnable work) {
		if (RenderSystem.isOnRenderThread()) {
			work.run();
		} else {
			RenderSystem.recordRenderCall(work::run);
		}
	}

	private static void blit(NativeImage from, NativeImage sheet, AtlasLayout.Sprite sprite,
			AtlasLayout.Sprite cell, int yOffset) {
		for (int y = cell.y(); y < cell.y() + cell.height(); y++) {
			for (int x = cell.x(); x < cell.x() + cell.width(); x++) {
				int sx = Math.clamp(x - sprite.x(), 0, sprite.width() - 1);
				int sy = Math.clamp(y - sprite.y(), 0, sprite.height() - 1);
				sheet.setPixelRGBA(x, yOffset + y, from.getPixelRGBA(sx, sy));
			}
		}
	}
}
