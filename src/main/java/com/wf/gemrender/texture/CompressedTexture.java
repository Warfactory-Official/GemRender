package com.wf.gemrender.texture;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

public final class CompressedTexture extends AbstractTexture {
	private final int width;
	private final int height;
	private final int glFormat;
	private byte[] blocks;

	public CompressedTexture(BlockCompressor.Blocks compressed) {
		this.width = compressed.width();
		this.height = compressed.height();
		this.glFormat = compressed.glFormat();
		this.blocks = compressed.data();
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int byteSize() {
		return BlockCompressor.blockBytes(width, height);
	}

	public void upload() {
		RenderSystem.assertOnRenderThread();
		if (blocks == null) {
			return;
		}

		GlStateManager._bindTexture(getId());

		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		ByteBuffer data = MemoryUtil.memAlloc(blocks.length);
		try {
			data.put(blocks)
					.flip();
			GL13.glCompressedTexImage2D(GL11.GL_TEXTURE_2D, 0, glFormat, width, height, 0, data);
		} finally {
			MemoryUtil.memFree(data);
		}

		blur = false;
		mipmap = false;
		blocks = null;
	}

	@Override
	public void load(ResourceManager resourceManager) {
	}
}
