package com.wf.gemrender.iris;

import java.io.File;
import java.io.IOException;

import com.mojang.blaze3d.platform.NativeImage;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.texture.AtlasTexture;
import com.wf.gemrender.texture.LabPbr;
import com.wf.gemrender.texture.SurfaceBake;

import net.irisshaders.iris.pbr.loader.PBRTextureLoader;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.ResourceManager;

final class IrisPbrLoader implements PBRTextureLoader<AtlasTexture> {
	private static final boolean DUMP = Boolean.getBoolean("gemrender.dumpatlas");

	static void register() {
		PBRTextureLoaderRegistry.INSTANCE.register(AtlasTexture.class, new IrisPbrLoader());

		GemRender.LOGGER.info("Registered a LabPBR loader for GemRender atlases; a shaderpack that "
				+ "reads normals and specular will get GemRender's material data rather than Iris's "
				+ "flat default");
	}

	@Override
	public void load(AtlasTexture atlas, ResourceManager resources, PBRTextureConsumer consumer) {
		NativeImage sheet = atlas.getPixels();
		if (sheet == null || atlas.bands() < SurfaceBake.BANDS) {
			GemRender.LOGGER.info("A shaderpack asked for the material data of a {} band atlas; "
					+ "there is none to give", atlas.bands());
			return;
		}

		GemRender.LOGGER.info("Building LabPBR normal and specular maps for a {}x{} atlas",
				sheet.getWidth(), sheet.getHeight());

		int band = sheet.getHeight() / atlas.bands();
		NativeImage normal = normalMap(sheet, band);
		NativeImage specular = specularMap(sheet, band);
		dump(normal, "labpbr_n");
		dump(specular, "labpbr_s");

		consumer.acceptNormalTexture(new DynamicTexture(normal));
		consumer.acceptSpecularTexture(new DynamicTexture(specular));
	}

	private static void dump(NativeImage map, String name) {
		if (!DUMP) {
			return;
		}
		try {
			File out = new File(Minecraft.getInstance().gameDirectory, "gemrender_atlases/" + name + ".png");
			out.getParentFile()
					.mkdirs();
			map.writeToFile(out);
			GemRender.LOGGER.info("Dumped {} to {}", name, out);
		} catch (IOException e) {
			GemRender.LOGGER.warn("Could not dump {}", name, e);
		}
	}

	private static NativeImage normalMap(NativeImage sheet, int band) {
		NativeImage out = new NativeImage(sheet.getWidth(), sheet.getHeight(), false);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				out.setPixelRGBA(x, y, y < band ? LabPbr.normal(sheet.getPixelRGBA(x, band + y))
						: LabPbr.FLAT_NORMAL);
			}
		}
		return out;
	}

	private static NativeImage specularMap(NativeImage sheet, int band) {
		NativeImage out = new NativeImage(sheet.getWidth(), sheet.getHeight(), false);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				out.setPixelRGBA(x, y, y < band
						? LabPbr.specular(sheet.getPixelRGBA(x, band + y),
								sheet.getPixelRGBA(x, 2 * band + y))
						: LabPbr.INERT_SPECULAR);
			}
		}
		return out;
	}
}
