package dev.engine_room.flywheel.backend.engine;

import org.lwjgl.opengl.GL33C;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.gl.GlTextureUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Binds vanilla's textures for Flywheel's own GL programs.
 * <p>
 * 26.1 split sampling state out of the texture object and into standalone GL sampler objects, so
 * binding a texture is now two calls, not one: {@code glBindTexture} for the image and
 * {@code glBindSampler} for the filtering. A texture bound without a sampler falls back to the texture
 * object's own parameters, which 26.1 never sets -- the result is an incomplete texture that samples
 * black rather than a GL error. Nothing here may bind a texture without also binding a sampler.
 */
public class TextureBinder {
	public static void bind(GlTextureUnit unit, ResourceLocation resourceLocation) {
		AbstractTexture texture = Minecraft.getInstance()
				.getTextureManager()
				.getTexture(resourceLocation);
		bind(unit, texture.getTextureView(), texture.getSampler());
	}

	/**
	 * Bind a vanilla texture, overriding its filtering with the material's.
	 */
	public static void bind(GlTextureUnit unit, AbstractTexture texture, boolean blur, boolean mipmap) {
		bind(unit, texture.getTextureView(), samplerFor(texture.getSampler(), blur, mipmap));
	}

	public static void bind(GlTextureUnit unit, GpuTextureView view, GpuSampler sampler) {
		unit.makeActive();
		GlStateManager._bindTexture(glId(view.texture()));
		GL33C.glBindSampler(unit.number, ((GlSampler) sampler).getId());
	}

	public static void unbind(GlTextureUnit unit) {
		unit.makeActive();
		GlStateManager._bindTexture(0);
		GL33C.glBindSampler(unit.number, 0);
	}

	/**
	 * Makes a raw {@code glBindTexture} on this unit sample the way the texture object says it should.
	 * <p>
	 * For the backend's own textures, which are created with {@code glTexImage2D} and their own
	 * {@code GL_NEAREST} / {@code GL_CLAMP_TO_EDGE} parameters rather than through {@code GpuDevice}. A
	 * sampler object bound on a unit overrides those parameters for as long as it is there, and vanilla
	 * binds one per texture per render pass and never unbinds it -- so a unit any vanilla pipeline has
	 * used is carrying a stale sampler by the time raw GL gets to it. Binding the sampler that says
	 * exactly what the texture already says is what makes the texture's parameters true again.
	 */
	public static void ownSampler(GlTextureUnit unit) {
		GL33C.glBindSampler(unit.number, ((GlSampler) RenderSystem.getSamplerCache()
				.getClampToEdge(FilterMode.NEAREST)).getId());
	}

	/** Undoes {@link #ownSampler}, leaving the unit as vanilla's own draws expect to find it. */
	public static void clearSampler(GlTextureUnit unit) {
		GL33C.glBindSampler(unit.number, 0);
	}

	public static void bindLightAndOverlay() {
		var gameRenderer = Minecraft.getInstance().gameRenderer;

		// Both are clamped and linear in 26.1 -- see RenderSetup, which binds Sampler1/Sampler2 the
		// same way for every vanilla render type.
		GpuSampler sampler = RenderSystem.getSamplerCache()
				.getClampToEdge(FilterMode.LINEAR);

		bind(Samplers.OVERLAY, gameRenderer.overlayTexture()
				.getTextureView(), sampler);
		bind(Samplers.LIGHT, gameRenderer.lightmap(), sampler);
	}

	public static void resetLightAndOverlay() {
		// Nothing to do on 26.1. The overlay and lightmap used to need turning on and off around a
		// draw because they shared vanilla's texture units; they are now plain texture views that
		// vanilla rebinds for every one of its own draws.
		unbind(Samplers.OVERLAY);
		unbind(Samplers.LIGHT);
	}

	/**
	 * Get the raw GL name behind a texture. Flywheel issues its own draws, so it needs the handle
	 * rather than the {@link GpuTextureView} vanilla's render passes take.
	 */
	public static int glId(GpuTexture texture) {
		return ((GlTexture) texture).glId();
	}

	public static int byName(ResourceLocation texture) {
		return glId(Minecraft.getInstance()
				.getTextureManager()
				.getTexture(texture)
				.getTexture());
	}

	private static GpuSampler samplerFor(GpuSampler base, boolean blur, boolean mipmap) {
		FilterMode filter = blur ? FilterMode.LINEAR : FilterMode.NEAREST;
		// Keep whatever address mode the texture came with; only the material's filtering is ours.
		return RenderSystem.getSamplerCache()
				.getSampler(base.getAddressModeU(), base.getAddressModeV(), filter, filter, mipmap);
	}
}
