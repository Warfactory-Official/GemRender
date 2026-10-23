package com.wf.gemrender.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import static org.lwjgl.opengl.GL33C.*;

/** Owned RGBA8 colour-only framebuffer for {@link Canvas#begin(CanvasTarget, float, float, int)}. */
public final class CanvasTarget implements AutoCloseable {
    private int width;
    private int height;
    private int framebuffer;
    private int texture;
    private boolean linear = true;

    public CanvasTarget(int width, int height) {
        resize(width, height);
    }

    /** Takes effect at the next draw; contents are lost. */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(width + "x" + height);
        }
        if (width == this.width && height == this.height) {
            return;
        }
        this.width = width;
        this.height = height;
        if (texture != 0) {
            RenderSystem.assertOnRenderThread();
            allocate();
        }
    }

    public CanvasTarget linear(boolean linear) {
        this.linear = linear;
        if (texture != 0) {
            filter();
        }
        return this;
    }

    void ensure() {
        if (framebuffer != 0) {
            return;
        }
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        texture = glGenTextures();
        allocate();
        GlStateManager._bindTexture(texture);
        filter();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int previousFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        framebuffer = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
        GlStateManager._bindTexture(previousTexture);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            close();
            throw new IllegalStateException("Canvas target incomplete: 0x" + Integer.toHexString(status));
        }
    }

    private void allocate() {
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        GlStateManager._bindTexture(previous);
    }

    private void filter() {
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        int mode = linear ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mode);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mode);
        GlStateManager._bindTexture(previous);
    }

    int framebuffer() {
        return framebuffer;
    }

    /** GL texture name; 0 before the first draw. Premultiplied alpha. */
    public int texture() {
        return texture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
        }
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
        }
        framebuffer = 0;
        texture = 0;
    }
}
