package com.wf.gemrender.render;

import com.mojang.blaze3d.platform.GlStateManager;

import static org.lwjgl.opengl.GL33C.*;

public final class PassState {

    private static final int[] DEEP_UNITS = {0, 1, 2};
    private final int[] unitTexture = new int[DEEP_UNITS.length];
    private final int[] unitSampler = new int[DEEP_UNITS.length];
    private int drawFramebuffer;
    private int program;
    private boolean blend;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private boolean depthTest;
    private boolean depthMask;
    private int depthFunc;
    private final int[] colorMask = new int[4];
    private boolean deep;
    private int vertexArray;
    private int activeTexture;
    private boolean cullFace;
    private int arrayBuffer;

    public void save() {
        deep = false;
        saveCore();
    }

    public void saveDeep() {
        deep = true;
        saveCore();

        vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        activeTexture = GlState.activeTexture();
        cullFace = glGetInteger(GL_CULL_FACE) != 0;
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        for (int i = 0; i < DEEP_UNITS.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + DEEP_UNITS[i]);
            unitTexture[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            unitSampler[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        GlStateManager._activeTexture(activeTexture);
    }

    private void saveCore() {
        drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        program = glGetInteger(GL_CURRENT_PROGRAM);

        blend = glGetInteger(GL_BLEND) != 0;
        blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);

        depthTest = glGetInteger(GL_DEPTH_TEST) != 0;
        depthMask = glGetInteger(GL_DEPTH_WRITEMASK) != 0;
        depthFunc = glGetInteger(GL_DEPTH_FUNC);
        glGetIntegerv(GL_COLOR_WRITEMASK, colorMask);
    }

    public void restore() {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glUseProgram(program);

        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if (blendEquationRgb == blendEquationAlpha) {
            GlState.blendEquation(blendEquationRgb);
        }
        if (blend) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }

        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(depthMask);
        GlState.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        if (depthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }

        if (!deep) {
            return;
        }

        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        if (cullFace) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
        for (int i = 0; i < DEEP_UNITS.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + DEEP_UNITS[i]);
            GlStateManager._bindTexture(unitTexture[i]);
            GlState.restoreSampler(DEEP_UNITS[i], unitSampler[i]);
        }
        GlStateManager._activeTexture(activeTexture);
    }
}
