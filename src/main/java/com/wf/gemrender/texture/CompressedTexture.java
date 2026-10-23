package com.wf.gemrender.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

//? if >=26.1 {
/*import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
*///?}

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

        //? if >=26.1 {
        /*// Declared RGBA8, stored BC7: GlDevice allocates with glTexImage2D (mutable) => level 0 respecified.
        // Sampling is format-agnostic; nothing copies into or out of this texture.
        texture = RenderSystem.getDevice().createTexture(() -> "gemrender:bc7_atlas", GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
        GlStateManager._bindTexture(((GlTexture) texture).glId());
        compressedImage();
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        textureView = RenderSystem.getDevice().createTextureView(texture);
        blocks = null;
        *///?} else {
        GlStateManager._bindTexture(getId());

        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        compressedImage();

        blur = false;
        mipmap = false;
        blocks = null;
        //?}
    }

    private void compressedImage() {
        ByteBuffer data = MemoryUtil.memAlloc(blocks.length);
        try {
            data.put(blocks)
                    .flip();
            GL13.glCompressedTexImage2D(GL11.GL_TEXTURE_2D, 0, glFormat, width, height, 0, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    //? if <26.1 {
    @Override
    public void load(ResourceManager resourceManager) {
    }
    //?}
}
