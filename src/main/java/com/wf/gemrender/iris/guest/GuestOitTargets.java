package com.wf.gemrender.iris.guest;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.GL_COLOR;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.glTexImage3D;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL30C.*;

/** Per-pass OIT targets sized to Iris's scene depth, which every producer FBO depth-tests against. */
final class GuestOitTargets {
    private static final float[] DEPTH_RANGE_CLEAR = {-1e30f, -1e30f, 0f, 0f};
    private static final float[] ZERO = {0f, 0f, 0f, 0f};

    private final int[] ranks;

    private int width = -1;
    private int height = -1;
    private int attachedDepth;
    private int depthRange;
    private int[] coefficients = new int[0];
    private int[] accumulate = new int[0];
    private int depthRangeFbo;
    private int coefficientsFbo;
    private int accumulateFbo;
    private int coefficientAttachments;

    GuestOitTargets(int[] ranks) {
        this.ranks = ranks;
        for (int rank : ranks) {
            coefficientAttachments += ForwardOit.layers(rank);
        }
    }

    /** Resizes, attaches {@code depth} and clears every target. Leaves the main framebuffer unbound. */
    void prepare(int depth, int width, int height) {
        if (width != this.width || height != this.height) {
            delete();
            allocate(width, height);
        }
        if (depth != attachedDepth) {
            attachedDepth = depth;
            for (int fbo : new int[] {depthRangeFbo, coefficientsFbo, accumulateFbo}) {
                GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
                int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Guest OIT framebuffer incomplete: 0x" + Integer.toHexString(status));
                }
            }
        }

        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._disableScissorTest();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, depthRangeFbo);
        glClearBufferfv(GL_COLOR, 0, DEPTH_RANGE_CLEAR);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, coefficientsFbo);
        for (int i = 0; i < coefficientAttachments; i++) {
            glClearBufferfv(GL_COLOR, i, ZERO);
        }
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, accumulateFbo);
        for (int i = 0; i < accumulate.length; i++) {
            glClearBufferfv(GL_COLOR, i, ZERO);
        }
    }

    private void allocate(int width, int height) {
        this.width = width;
        this.height = height;
        attachedDepth = 0;

        depthRange = texture2d(GL_RGBA32F);
        depthRangeFbo = framebuffer(new int[] {depthRange}, null);

        coefficients = new int[ranks.length];
        int[] layerTextures = new int[coefficientAttachments];
        int[] layerIndices = new int[coefficientAttachments];
        int attachment = 0;
        for (int set = 0; set < ranks.length; set++) {
            int layers = ForwardOit.layers(ranks[set]);
            coefficients[set] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D_ARRAY, coefficients[set]);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA16F, width, height, layers, 0, GL_RGBA, GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            for (int layer = 0; layer < layers; layer++) {
                layerTextures[attachment] = coefficients[set];
                layerIndices[attachment++] = layer;
            }
        }
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        coefficientsFbo = framebuffer(layerTextures, layerIndices);

        accumulate = new int[ranks.length];
        for (int slot = 0; slot < accumulate.length; slot++) {
            accumulate[slot] = texture2d(GL_RGBA32F);
        }
        accumulateFbo = framebuffer(accumulate, null);
    }

    private int texture2d(int format) {
        int texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        GlStateManager._bindTexture(0);
        return texture;
    }

    /** {@code layers} non-null: attachment i = layer {@code layers[i]} of array texture {@code textures[i]}. */
    private static int framebuffer(int[] textures, int[] layers) {
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer drawBuffers = stack.mallocInt(textures.length);
            for (int i = 0; i < textures.length; i++) {
                if (layers == null) {
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, textures[i], 0);
                } else {
                    glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, textures[i], 0, layers[i]);
                }
                drawBuffers.put(i, GL_COLOR_ATTACHMENT0 + i);
            }
            glDrawBuffers(drawBuffers);
        }
        return fbo;
    }

    int[] ranks() {
        return ranks;
    }

    int depthRangeFbo() {
        return depthRangeFbo;
    }

    int coefficientsFbo() {
        return coefficientsFbo;
    }

    int accumulateFbo() {
        return accumulateFbo;
    }

    int depthRange() {
        return depthRange;
    }

    int coefficients(int set) {
        return coefficients[set];
    }

    int accumulate(int slot) {
        return accumulate[slot];
    }

    // GlStateManager variants: its texture and FBO caches must not keep a deleted (reusable) name.
    void delete() {
        if (width < 0) {
            return;
        }
        for (int texture : accumulate) {
            GlStateManager._deleteTexture(texture);
        }
        GlStateManager._deleteTexture(depthRange);
        glDeleteTextures(coefficients);
        GlStateManager._glDeleteFramebuffers(depthRangeFbo);
        GlStateManager._glDeleteFramebuffers(coefficientsFbo);
        GlStateManager._glDeleteFramebuffers(accumulateFbo);
        coefficients = new int[0];
        accumulate = new int[0];
        width = height = -1;
    }
}
