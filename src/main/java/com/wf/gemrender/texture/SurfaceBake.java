package com.wf.gemrender.texture;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import com.wf.gemrender.GemRender;

import net.minecraft.resources.ResourceLocation;

public final class SurfaceBake implements AutoCloseable {
	public static final int BANDS = 3;

	private static final int FLAT_NORMAL = 128;

	private final NativeImage[] bands;
	private final int width;
	private final int height;

	private SurfaceBake(NativeImage[] bands, int width, int height) {
		this.bands = bands;
		this.width = width;
		this.height = height;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int bands() {
		return bands.length;
	}

	public NativeImage band(int index) {
		return bands[index];
	}

	@Override
	public void close() {
		for (NativeImage band : bands) {
			band.close();
		}
	}

	@Nullable
	public static SurfaceBake of(MaterialMaps maps, boolean pbr) {
		NativeImage base = read(maps.baseColor());
		if (maps.baseColor() != null && base == null) {
			return null;
		}

		NativeImage normal = read(maps.normal());
		NativeImage orm = read(maps.metallicRoughness());
		NativeImage occlusion = read(maps.occlusion());
		NativeImage emissive = read(maps.emissive());

		try {
			int width = size(base, normal, orm, occlusion, emissive, true);
			int height = size(base, normal, orm, occlusion, emissive, false);

			NativeImage[] out = new NativeImage[pbr ? BANDS : 1];
			out[0] = bakeBase(maps, base, occlusion, width, height);
			if (pbr) {
				out[1] = bakeSurface(maps, normal, orm, width, height);
				out[2] = bakeEmissive(maps, emissive, width, height);
			}
			return new SurfaceBake(out, width, height);
		} finally {
			close(base);
			close(normal);
			close(orm);
			close(occlusion);
			close(emissive);
		}
	}

	private static NativeImage bakeBase(MaterialMaps maps, @Nullable NativeImage base,
			@Nullable NativeImage occlusion, int width, int height) {
		NativeImage out = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = base == null ? 0xFFFFFFFF : sample(base, x, y, width, height);

				float shade = 1.0f;
				if (occlusion != null) {
					float sampled = red(sample(occlusion, x, y, width, height)) / 255.0f;
					shade = 1.0f + maps.occlusionStrength() * (sampled - 1.0f);
				}

				out.setPixelRGBA(x, y, pack(
						scale(red(pixel), maps.baseColorR() * shade),
						scale(green(pixel), maps.baseColorG() * shade),
						scale(blue(pixel), maps.baseColorB() * shade),
						scale(alpha(pixel), maps.baseColorA())));
			}
		}
		return out;
	}

	private static NativeImage bakeSurface(MaterialMaps maps, @Nullable NativeImage normal,
			@Nullable NativeImage orm, int width, int height) {
		NativeImage out = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int nx = FLAT_NORMAL;
				int ny = FLAT_NORMAL;
				if (normal != null) {
					int pixel = sample(normal, x, y, width, height);
					nx = scaleAbout(red(pixel), maps.normalScale());
					ny = scaleAbout(green(pixel), maps.normalScale());
				}

				int roughness = 255;
				int metallic = 255;
				if (orm != null) {
					int pixel = sample(orm, x, y, width, height);
					roughness = green(pixel);
					metallic = blue(pixel);
				}

				out.setPixelRGBA(x, y, pack(nx, ny, scale(roughness, maps.roughnessFactor()),
						scale(metallic, maps.metallicFactor())));
			}
		}
		return out;
	}

	private static NativeImage bakeEmissive(MaterialMaps maps, @Nullable NativeImage emissive,
			int width, int height) {
		NativeImage out = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = emissive == null ? 0xFFFFFFFF : sample(emissive, x, y, width, height);
				out.setPixelRGBA(x, y, pack(
						scale(red(pixel), maps.emissiveR()),
						scale(green(pixel), maps.emissiveG()),
						scale(blue(pixel), maps.emissiveB()),
						255));
			}
		}
		return out;
	}

	private static int sample(NativeImage from, int x, int y, int width, int height) {
		if (from.getWidth() == width && from.getHeight() == height) {
			return from.getPixelRGBA(x, y);
		}
		int sx = Math.min(from.getWidth() - 1, x * from.getWidth() / width);
		int sy = Math.min(from.getHeight() - 1, y * from.getHeight() / height);
		return from.getPixelRGBA(sx, sy);
	}

	private static int size(@Nullable NativeImage base, @Nullable NativeImage normal,
			@Nullable NativeImage orm, @Nullable NativeImage occlusion, @Nullable NativeImage emissive,
			boolean wide) {
		if (base != null) {
			return wide ? base.getWidth() : base.getHeight();
		}

		int largest = 1;
		for (NativeImage image : new NativeImage[] { normal, orm, occlusion, emissive }) {
			if (image != null) {
				largest = Math.max(largest, wide ? image.getWidth() : image.getHeight());
			}
		}
		return largest;
	}

	@Nullable
	private static NativeImage read(@Nullable ResourceLocation source) {
		if (source == null) {
			return null;
		}
		NativeImage image = ModelTextures.read(source);
		if (image == null) {
			GemRender.LOGGER.warn("Could not read {}; the material will bake without it", source);
		}
		return image;
	}

	private static void close(@Nullable NativeImage image) {
		if (image != null) {
			image.close();
		}
	}

	private static int red(int pixel) {
		return pixel & 0xFF;
	}

	private static int green(int pixel) {
		return (pixel >> 8) & 0xFF;
	}

	private static int blue(int pixel) {
		return (pixel >> 16) & 0xFF;
	}

	private static int alpha(int pixel) {
		return (pixel >>> 24) & 0xFF;
	}

	private static int pack(int r, int g, int b, int a) {
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int scale(int channel, float factor) {
		return Math.clamp(Math.round(channel * factor), 0, 255);
	}

	private static int scaleAbout(int channel, float factor) {
		return Math.clamp(Math.round(FLAT_NORMAL + (channel - FLAT_NORMAL) * factor), 0, 255);
	}
}
