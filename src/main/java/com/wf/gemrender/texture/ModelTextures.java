package com.wf.gemrender.texture;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class ModelTextures {
	public static final String KTX2_SUFFIX = ".ktx2";

	private ModelTextures() {
	}

	public static boolean isKtx2(ResourceLocation source) {
		return source.getPath()
				.toLowerCase(Locale.ROOT)
				.endsWith(KTX2_SUFFIX);
	}

	@Nullable
	public static NativeImage read(ResourceLocation source) {
		try (InputStream in = Minecraft.getInstance()
				.getResourceManager()
				.getResourceOrThrow(source)
				.open()) {
			return isKtx2(source) ? toNativeImage(KtxImage.read(in)) : NativeImage.read(in);
		} catch (IOException | RuntimeException e) {
			GemRender.LOGGER.warn("Could not read model texture {} ({})", source, e.toString());
			return null;
		}
	}

	public static ResourceLocation materialTexture(ResourceLocation source,
			List<ResourceLocation> owned) {
		if (!isKtx2(source)) {
			return source;
		}

		NativeImage image = read(source);
		if (image == null) {
			return source;
		}

		ResourceLocation id = decodedId(source);

		register(id, new DynamicTexture(image));
		owned.add(id);

		GemRender.LOGGER.info("Decoded {} to a {}x{} texture registered as {}", source, image.getWidth(),
				image.getHeight(), id);
		return id;
	}

	public static void release(ResourceLocation id) {
		Minecraft.getInstance()
				.getTextureManager()
				.release(id);
	}

	static ResourceLocation decodedId(ResourceLocation source) {
		return ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID,
				"decoded/" + source.getNamespace() + "/" + source.getPath());
	}

	static NativeImage toNativeImage(KtxImage decoded) {
		NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);
		byte[] rgba = decoded.rgba();

		int i = 0;
		for (int y = 0; y < decoded.height(); y++) {
			for (int x = 0; x < decoded.width(); x++) {
				int r = rgba[i] & 0xFF;
				int g = rgba[i + 1] & 0xFF;
				int b = rgba[i + 2] & 0xFF;
				int a = rgba[i + 3] & 0xFF;
				i += KtxImage.BYTES_PER_PIXEL;
				image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
			}
		}
		return image;
	}

	private static void register(ResourceLocation id, DynamicTexture texture) {
		if (RenderSystem.isOnRenderThread()) {
			Minecraft.getInstance()
					.getTextureManager()
					.register(id, texture);
		} else {
			RenderSystem.recordRenderCall(() -> Minecraft.getInstance()
					.getTextureManager()
					.register(id, texture));
		}
	}
}
