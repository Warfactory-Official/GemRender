package com.wf.gemrender.water;

import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.PassState;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.render.Vanilla;
import com.wf.gemrender.volume.SceneDepth;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;

/**
 * One absorbance layer at 1/factor resolution: depth = farthest of each block, upsampled by depth match.
 * Exclusive frames only: Flywheel fetches wavelet coefficients at full-res {@code gl_FragCoord}.
 */
// TODO: mixed (wavelet + absorbance) frames stay full-res.
final class ReducedAbsorbance {

    private static final String VERSION = "#version 420 core\n";
    private static final int[] DRAW_LINEAR = {GL_COLOR_ATTACHMENT0};
    private static final int[] DRAW_ACCUMULATORS = {GL_COLOR_ATTACHMENT0 + Absorbance.ABSORBANCE_SLOT,
            GL_COLOR_ATTACHMENT0 + Absorbance.EMISSION_SLOT};
    private static final float[] ZERO = {0f, 0f, 0f, 0f};

    private final PassState state = new PassState();
    private final int[] viewport = new int[4];

    private int program;
    private int vao;
    private int depthLoc;
    private int factorLoc;
    private int znearLoc;
    private int zfarLoc;
    private boolean created;
    private boolean failed;

    private int fbo;
    private int depth;
    private int linear;
    private int accumulate;
    private int emission;
    private int width = -1;
    private int height = -1;

    private int outerFramebuffer;
    private int borrowedSceneDepth;

    boolean enabled() {
        return ensureCreated();
    }

    int accumulateTexture() {
        return accumulate;
    }

    int emissionTexture() {
        return emission;
    }

    int linearTexture() {
        return linear;
    }

    /** Before the draws: farthest of {@code depthTexture} + linear copy, accumulators cleared. */
    void prepare(int depthTexture, int factor) {
        glGetIntegerv(GL_VIEWPORT, viewport);
        ensureSize((viewport[2] + factor - 1) / factor, (viewport[3] + factor - 1) / factor);

        state.save();
        int previousUnit = GlState.activeTexture();
        try {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, width, height);
            GlState.disableBlend();
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL_ALWAYS);
            GlStateManager._depthMask(true);
            GlState.colorMask(true, true, true, true);
            glDrawBuffers(DRAW_LINEAR);

            GlStateManager._glUseProgram(program);
            glUniform1i(depthLoc, 0);
            glUniform1i(factorLoc, factor);
            glUniform1f(znearLoc, Vanilla.zNear());
            glUniform1f(zfarLoc, Vanilla.depthFar());
            GlStateManager._activeTexture(GL_TEXTURE0);
            int borrowed = glGetInteger(GL_TEXTURE_BINDING_2D);
            int borrowedSampler = GlState.pointSampler(0);
            try {
                GlStateManager._bindTexture(depthTexture);
                int previousArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
                GlStateManager._glBindVertexArray(vao);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                GlStateManager._glBindVertexArray(previousArray);
            } finally {
                GlStateManager._bindTexture(borrowed);
                GlState.restoreSampler(0, borrowedSampler);
            }

            glDrawBuffers(DRAW_ACCUMULATORS);
            glClearBufferfv(GL_COLOR, 0, ZERO);
            glClearBufferfv(GL_COLOR, 1, ZERO);
        } finally {
            GlStateManager._activeTexture(previousUnit);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            state.restore();
        }
    }

    /** Absorbance route begins: draws land here; volumes march against the reduced linear depth. */
    void enter() {
        outerFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, width, height);

        int previousUnit = TextureUnits.activate(SceneDepth.TEXTURE_UNIT);
        borrowedSceneDepth = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, linear);
        TextureUnits.restore(previousUnit);
    }

    void leave() {
        int previousUnit = TextureUnits.activate(SceneDepth.TEXTURE_UNIT);
        glBindTexture(GL_TEXTURE_2D, borrowedSceneDepth);
        TextureUnits.restore(previousUnit);

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, outerFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    private boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            program = GlPrograms.link("absorbance_downsample",
                    VERSION + GlPrograms.resource(GemRender.MOD_ID, "shaders/water_split.vert"),
                    VERSION + GlPrograms.resource("flywheel", "flywheel/internal/depth.glsl")
                            + GlPrograms.resource(GemRender.MOD_ID, "shaders/absorbance_downsample.frag"));
            depthLoc = glGetUniformLocation(program, "_gr_depth");
            factorLoc = glGetUniformLocation(program, "_gr_factor");
            znearLoc = glGetUniformLocation(program, "_gr_znear");
            zfarLoc = glGetUniformLocation(program, "_gr_zfar");
            vao = glGenVertexArrays();
            created = true;
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Reduced absorbance disabled: its downsample shader failed to build. "
                    + "Smoke and gas draw at full resolution.", e);
            return false;
        }
    }

    private void ensureSize(int newWidth, int newHeight) {
        if (width == newWidth && height == newHeight) {
            return;
        }
        width = newWidth;
        height = newHeight;

        if (fbo != 0) {
            glDeleteTextures(depth);
            glDeleteTextures(linear);
            glDeleteTextures(accumulate);
            glDeleteTextures(emission);
            glDeleteFramebuffers(fbo);
        }

        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            depth = texture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT);
            linear = texture(GL_R32F, GL_RED);
            accumulate = texture(GL_RGBA16F, GL_RGBA);
            emission = texture(GL_RGBA16F, GL_RGBA);
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }

        int previousFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depth, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, linear, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + Absorbance.ABSORBANCE_SLOT, accumulate, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + Absorbance.EMISSION_SLOT, emission, 0);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
    }

    private int texture(int internal, int format) {
        int id = glGenTextures();
        GlStateManager._bindTexture(id);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, format, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }
}
