package dev.engine_room.flywheel.backend;

import java.io.IOException;

import org.jetbrains.annotations.UnknownNullability;

import com.mojang.blaze3d.platform.NativeImage;

import dev.engine_room.flywheel.lib.util.ResourceUtil;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class NoiseTextures {
	public static final ResourceLocation NOISE_TEXTURE = ResourceUtil.rl("textures/flywheel/noise/blue.png");

	@UnknownNullability
	public static DynamicTexture BLUE_NOISE;

	public static void reload(ResourceManager manager) {
		if (BLUE_NOISE != null) {
			BLUE_NOISE.close();
			BLUE_NOISE = null;
		}
		var optional = manager.getResource(NOISE_TEXTURE);

		if (optional.isEmpty()) {
			return;
		}

		try (var is = optional.get()
				.open()) {
			var image = NativeImage.read(NativeImage.Format.LUMINANCE, is);

			// 26.1 took filtering and wrapping off the texture object and put them in standalone
			// sampler objects, so there is nothing to configure here any more -- the linear, repeating
			// sampler this wants is chosen where it is bound, in OitFramebuffer#prepare.
			BLUE_NOISE = new DynamicTexture(NOISE_TEXTURE::toString, image);
		} catch (IOException e) {

		}
	}
}
