package com.wf.gemrender.water;



import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;


import java.io.IOException;



import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glGetInteger;

final class WaterSplitPrograms {
    private static final String MOD = GemRender.MOD_ID;
    private static final String FLYWHEEL = "flywheel";
    private static final String VERSION = "#version 420 core\n";
    private static final int UNIT_ACCUMULATE = 0;
    private static final int UNIT_FRONT = 1;
    private static final int UNIT_DEPTH_RANGE = 2;
    private static final int UNIT_COEFFICIENTS = 3;
    private static final int UNIT_WATER_DEPTH = 4;
    private static final int UNIT_CLOUD_DEPTH = 5;
    private static final int UNIT_EMISSION = 2;
    private static final int UNIT_FRONT_EMISSION = 3;
    private static final int UNIT_DEPTH_RANGE_ABSORBANCE = 4;
    private static final int UNIT_LOW_DEPTH = 6;
    private static final int UNIT_FULL_DEPTH = 7;
    private static final int UNIT_FULL_ACCUMULATE = 8;
    private static final int UNIT_FULL_FRONT = 9;
    private static final int UNIT_FULL_EMISSION = 10;
    private static final int UNIT_FULL_FRONT_EMISSION = 11;
    private static final int MODE_COMPOSITE = 0;
    private static final int MODE_BEHIND = 1;
    private static final int MODE_FRONT = 2;
    private final int[] borrowed = new int[UNIT_FULL_FRONT_EMISSION + 1];
    private final int[] borrowedSampler = new int[UNIT_FULL_FRONT_EMISSION + 1];
    private int depthCopyProgram;
    private int behindProgram;
    private int frontProgram;
    private int absorbanceCompositeProgram;
    private int absorbanceBehindProgram;
    private int absorbanceFrontProgram;
    private int upsampleProgram;
    private int upsampleFactorLoc;
    private int upsampleModeLoc;
    private int upsampleCloudPhaseLoc;
    private int upsampleZNearLoc;
    private int upsampleZFarLoc;
    private int vao;
    private int depthCopyDepthLoc;
    private int depthCopySecondLoc;
    private int depthCopyTwoSourcesLoc;
    private int depthFillProgram;
    private int splitDepthProgram;
    private int splitDepthZNearLoc;
    private int splitDepthZFarLoc;
    private int absorbanceZNearLoc;
    private int absorbanceZFarLoc;
    private int absorbanceCloudPhaseLoc;
    private int behindZNearLoc;
    private int behindZFarLoc;
    private int frontZNearLoc;
    private int frontZFarLoc;
    private int frontCloudPhaseLoc;
    private boolean created;
    private boolean failed;

    private static void bindSamplers(int program) {
        GlStateManager._glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_gr_accumulate"), UNIT_ACCUMULATE);
        glUniform1i(glGetUniformLocation(program, "_gr_frontAccumulate"), UNIT_FRONT);
        glUniform1i(glGetUniformLocation(program, "_gr_depthRange"), UNIT_DEPTH_RANGE);
        glUniform1i(glGetUniformLocation(program, "_gr_coefficients"), UNIT_COEFFICIENTS);
        glUniform1i(glGetUniformLocation(program, "_gr_waterDepth"), UNIT_WATER_DEPTH);

        glUniform1i(glGetUniformLocation(program, "_gr_cloudDepth"), UNIT_CLOUD_DEPTH);
    }

    private static void bindAbsorbanceSamplers(int program) {
        GlStateManager._glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_gr_depthRange"), UNIT_DEPTH_RANGE_ABSORBANCE);
        glUniform1i(glGetUniformLocation(program, "_gr_accumulate"), UNIT_ACCUMULATE);
        glUniform1i(glGetUniformLocation(program, "_gr_frontAccumulate"), UNIT_FRONT);
        glUniform1i(glGetUniformLocation(program, "_gr_emission"), UNIT_EMISSION);
        glUniform1i(glGetUniformLocation(program, "_gr_frontEmission"), UNIT_FRONT_EMISSION);
    }

    private static void setZRange(int znearLoc, int zfarLoc) {
        glUniform1f(znearLoc, Vanilla.zNear());
        glUniform1f(zfarLoc, Vanilla.depthFar());
    }




    boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }

        try {
            String wavelet = GlPrograms.resource(FLYWHEEL, "flywheel/internal/wavelet.glsl");
            String depth = GlPrograms.resource(FLYWHEEL, "flywheel/internal/depth.glsl");
            String vert = VERSION + GlPrograms.resource(MOD, "shaders/water_split.vert");

            depthCopyProgram = GlPrograms.link("depth_copy",
                    vert, VERSION + GlPrograms.resource(MOD, "shaders/depth_copy.frag"));
            behindProgram = GlPrograms.link("water_behind",
                    vert, VERSION + wavelet + depth + GlPrograms.resource(MOD, "shaders/water_behind.frag"));
            frontProgram = GlPrograms.link("water_front",
                    vert, VERSION + wavelet + depth + GlPrograms.resource(MOD, "shaders/water_front.frag"));

            depthCopyDepthLoc = glGetUniformLocation(depthCopyProgram, "_gr_depth");
            depthCopySecondLoc = glGetUniformLocation(depthCopyProgram, "_gr_depth2");
            depthCopyTwoSourcesLoc = glGetUniformLocation(depthCopyProgram, "_gr_twoSources");
            depthFillProgram = GlPrograms.link("depth_fill",
                    VERSION + GlPrograms.resource(MOD, "shaders/depth_fill.vert"),
                    VERSION + GlPrograms.resource(MOD, "shaders/depth_fill.frag"));

            bindSamplers(behindProgram);
            behindZNearLoc = glGetUniformLocation(behindProgram, "_gr_znear");
            behindZFarLoc = glGetUniformLocation(behindProgram, "_gr_zfar");

            splitDepthProgram = GlPrograms.link("split_depth",
                    vert, VERSION + depth + GlPrograms.resource(MOD, "shaders/split_depth.frag"));
            bindSamplers(splitDepthProgram);
            splitDepthZNearLoc = glGetUniformLocation(splitDepthProgram, "_gr_znear");
            splitDepthZFarLoc = glGetUniformLocation(splitDepthProgram, "_gr_zfar");

            bindSamplers(frontProgram);
            frontZNearLoc = glGetUniformLocation(frontProgram, "_gr_znear");
            frontZFarLoc = glGetUniformLocation(frontProgram, "_gr_zfar");
            frontCloudPhaseLoc = glGetUniformLocation(frontProgram, "_gr_cloudPhase");

            String absorbance = VERSION + GlPrograms.resource(MOD, "shaders/absorbance.glsl");
            absorbanceCompositeProgram = GlPrograms.link("absorbance_composite",
                    vert, absorbance + depth + GlPrograms.resource(MOD, "shaders/absorbance_composite.frag"));
            absorbanceBehindProgram = GlPrograms.link("absorbance_behind",
                    vert, absorbance + GlPrograms.resource(MOD, "shaders/absorbance_behind.frag"));
            absorbanceFrontProgram = GlPrograms.link("absorbance_front",
                    vert, absorbance + GlPrograms.resource(MOD, "shaders/absorbance_front.frag"));

            bindAbsorbanceSamplers(absorbanceCompositeProgram);
            absorbanceZNearLoc = glGetUniformLocation(absorbanceCompositeProgram, "_gr_znear");
            absorbanceZFarLoc = glGetUniformLocation(absorbanceCompositeProgram, "_gr_zfar");
            bindAbsorbanceSamplers(absorbanceBehindProgram);
            bindAbsorbanceSamplers(absorbanceFrontProgram);
            glUniform1i(glGetUniformLocation(absorbanceFrontProgram, "_gr_cloudDepth"), UNIT_CLOUD_DEPTH);
            absorbanceCloudPhaseLoc = glGetUniformLocation(absorbanceFrontProgram, "_gr_cloudPhase");

            upsampleProgram = GlPrograms.link("absorbance_upsample",
                    vert, absorbance + depth + GlPrograms.resource(MOD, "shaders/absorbance_upsample.frag"));
            bindAbsorbanceSamplers(upsampleProgram);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_cloudDepth"), UNIT_CLOUD_DEPTH);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_lowDepth"), UNIT_LOW_DEPTH);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_depth"), UNIT_FULL_DEPTH);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_fullAccumulate"), UNIT_FULL_ACCUMULATE);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_fullFrontAccumulate"), UNIT_FULL_FRONT);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_fullEmission"), UNIT_FULL_EMISSION);
            glUniform1i(glGetUniformLocation(upsampleProgram, "_gr_fullFrontEmission"), UNIT_FULL_FRONT_EMISSION);
            upsampleFactorLoc = glGetUniformLocation(upsampleProgram, "_gr_factor");
            upsampleModeLoc = glGetUniformLocation(upsampleProgram, "_gr_mode");
            upsampleCloudPhaseLoc = glGetUniformLocation(upsampleProgram, "_gr_cloudPhase");
            upsampleZNearLoc = glGetUniformLocation(upsampleProgram, "_gr_znear");
            upsampleZFarLoc = glGetUniformLocation(upsampleProgram, "_gr_zfar");

            GlStateManager._glUseProgram(0);
            vao = glGenVertexArrays();
            created = true;
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Water split disabled: its shaders failed to build. Translucent models "
                    + "will occlude the water behind them, as they did before the split.", e);
            return false;
        }
    }

    void drawDepthCopy(int depthTexture, int secondTexture) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(depthCopyProgram);
            glUniform1i(depthCopyDepthLoc, 0);
            glUniform1i(depthCopySecondLoc, 1);
            glUniform1f(depthCopyTwoSourcesLoc, secondTexture == 0 ? 0.0f : 1.0f);
            bind2d(0, depthTexture);
            bind2d(1, secondTexture);
            drawFullscreen();
            unbind(1);
            unbind(0);
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    /** Depth 0 wherever the stencil test passes. */
    void drawDepthFill() {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(depthFillProgram);
            drawFullscreen();
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    void drawBehind(int accumulate, int front, int depthRange, int coefficients, int waterDepth,
                    int cloudDepth) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(behindProgram);
            setZRange(behindZNearLoc, behindZFarLoc);
            bindCompositeTextures(accumulate, front, depthRange, coefficients, waterDepth, cloudDepth);
            drawFullscreen();
            unbindCompositeTextures();
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    void drawSplitDepth(int depthRange, int waterDepth) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(splitDepthProgram);
            setZRange(splitDepthZNearLoc, splitDepthZFarLoc);
            bind2d(UNIT_DEPTH_RANGE, depthRange);
            bind2d(UNIT_WATER_DEPTH, waterDepth);
            drawFullscreen();
            unbind(UNIT_WATER_DEPTH);
            unbind(UNIT_DEPTH_RANGE);
            GlStateManager._activeTexture(GL_TEXTURE0);
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    void drawFront(int accumulate, int front, int depthRange, int coefficients, int waterDepth,
                   int cloudDepth, float cloudPhase) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(frontProgram);
            setZRange(frontZNearLoc, frontZFarLoc);
            glUniform1f(frontCloudPhaseLoc, cloudPhase);
            bindCompositeTextures(accumulate, front, depthRange, coefficients, waterDepth, cloudDepth);
            drawFullscreen();
            unbindCompositeTextures();
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    void drawAbsorbanceComposite(Absorbance a, int depthRange) {
        if (a.reducedFrame()) {
            drawUpsampled(MODE_COMPOSITE, a, a.reduced(), mainDepth(), depthRange, 0, -1f);
            return;
        }
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(absorbanceCompositeProgram);
            setZRange(absorbanceZNearLoc, absorbanceZFarLoc);
            bind2d(UNIT_DEPTH_RANGE_ABSORBANCE, depthRange);
            drawAccumulators(absorbanceCompositeProgram, a.accumulateTexture(), 0, a.emissionTexture(), 0);
            unbind(UNIT_DEPTH_RANGE_ABSORBANCE);
            GlStateManager._activeTexture(GL_TEXTURE0);
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    void drawAbsorbanceBehind(Absorbance a) {
        if (a.reducedFrame()) {
            drawUpsampled(MODE_BEHIND, a, a.reduced(), mainDepth(), 0, 0, -1f);
            return;
        }
        drawAccumulators(absorbanceBehindProgram, a.accumulateTexture(), a.frontTexture(), a.emissionTexture(),
                a.frontEmissionTexture());
    }

    /** @param testedDepth the depth the front resubmit was tested against */
    void drawAbsorbanceFront(Absorbance a, int testedDepth, int cloudDepth, float cloudPhase) {
        if (a.reducedFrame()) {
            drawUpsampled(MODE_FRONT, a, a.reducedFront(), testedDepth, 0, cloudDepth, cloudPhase);
            return;
        }
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(absorbanceFrontProgram);
            glUniform1f(absorbanceCloudPhaseLoc, cloudPhase);
            bind2d(UNIT_CLOUD_DEPTH, cloudDepth);
            drawAccumulators(absorbanceFrontProgram, 0, a.frontTexture(), 0, a.frontEmissionTexture());
            unbind(UNIT_CLOUD_DEPTH);
            GlStateManager._activeTexture(GL_TEXTURE0);
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    private static int mainDepth() {
        return Vanilla.depthTextureId(Minecraft.getInstance()
                .getMainRenderTarget());
    }

    /** {@code layer}: whose depth weights the upsample; behind reads both layers (same grid). */
    private void drawUpsampled(int mode, Absorbance a, ReducedAbsorbance layer, int fullDepth, int depthRange,
                               int cloudDepth, float cloudPhase) {
        ReducedAbsorbance main = a.reduced();
        ReducedAbsorbance front = a.reducedFront();
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(upsampleProgram);
            glUniform1i(upsampleFactorLoc, a.frameFactor());
            glUniform1i(upsampleModeLoc, mode);
            glUniform1f(upsampleCloudPhaseLoc, cloudPhase);
            setZRange(upsampleZNearLoc, upsampleZFarLoc);
            bind2d(UNIT_DEPTH_RANGE_ABSORBANCE, depthRange);
            bind2d(UNIT_CLOUD_DEPTH, cloudDepth);
            bind2d(UNIT_LOW_DEPTH, layer.linearTexture());
            bind2d(UNIT_FULL_DEPTH, fullDepth);
            bind2d(UNIT_FULL_ACCUMULATE, a.accumulateTexture());
            bind2d(UNIT_FULL_FRONT, a.frontTexture());
            bind2d(UNIT_FULL_EMISSION, a.emissionTexture());
            bind2d(UNIT_FULL_FRONT_EMISSION, a.frontEmissionTexture());
            drawAccumulators(upsampleProgram, main.accumulateTexture(), front.accumulateTexture(),
                    main.emissionTexture(), front.emissionTexture());
            unbind(UNIT_FULL_FRONT_EMISSION);
            unbind(UNIT_FULL_EMISSION);
            unbind(UNIT_FULL_FRONT);
            unbind(UNIT_FULL_ACCUMULATE);
            unbind(UNIT_FULL_DEPTH);
            unbind(UNIT_LOW_DEPTH);
            unbind(UNIT_CLOUD_DEPTH);
            unbind(UNIT_DEPTH_RANGE_ABSORBANCE);
            GlStateManager._activeTexture(GL_TEXTURE0);
        } finally {
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    /** Premultiplied (shaders/absorbance.glsl); leaves the callers' (SRC_ALPHA, ONE_MINUS_SRC_ALPHA). */
    private void drawAccumulators(int program, int accumulate, int front, int emission, int frontEmission) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            GlStateManager._glUseProgram(program);
            GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            bind2d(UNIT_ACCUMULATE, accumulate);
            bind2d(UNIT_FRONT, front);
            bind2d(UNIT_EMISSION, emission);
            bind2d(UNIT_FRONT_EMISSION, frontEmission);
            GlStateManager._activeTexture(GL_TEXTURE0);
            drawFullscreen();
            unbind(UNIT_FRONT_EMISSION);
            unbind(UNIT_EMISSION);
            unbind(UNIT_FRONT);
            unbind(UNIT_ACCUMULATE);
            GlStateManager._activeTexture(GL_TEXTURE0);
        } finally {
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._glUseProgram(previousProgram);
        }
    }

    private void bindCompositeTextures(int accumulate, int front, int depthRange, int coefficients,
                                       int waterDepth, int cloudDepth) {
        bind2d(UNIT_ACCUMULATE, accumulate);
        bind2d(UNIT_FRONT, front);
        bind2d(UNIT_DEPTH_RANGE, depthRange);

        GlStateManager._activeTexture(GL_TEXTURE0 + UNIT_COEFFICIENTS);
        borrowed[UNIT_COEFFICIENTS] = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, coefficients);
        borrowedSampler[UNIT_COEFFICIENTS] = GlState.pointSampler(UNIT_COEFFICIENTS);
        bind2d(UNIT_WATER_DEPTH, waterDepth);
        bind2d(UNIT_CLOUD_DEPTH, cloudDepth);
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    private void unbindCompositeTextures() {
        for (int unit = UNIT_CLOUD_DEPTH; unit >= 0; unit--) {
            if (unit == UNIT_COEFFICIENTS) {
                GlStateManager._activeTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
                GlStateManager._bindTexture(borrowed[unit]);
                GlState.restoreSampler(unit, borrowedSampler[unit]);
            } else {
                unbind(unit);
            }
        }
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    private void bind2d(int unit, int texture) {
        GlStateManager._activeTexture(GL_TEXTURE0 + unit);
        borrowed[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);

        borrowedSampler[unit] = GlState.pointSampler(unit);
    }

    private void unbind(int unit) {
        GlStateManager._activeTexture(GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(borrowed[unit]);
        GlState.restoreSampler(unit, borrowedSampler[unit]);
    }

    private void drawFullscreen() {
        int previousArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        try {
            GlStateManager._glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            GlStateManager._glBindVertexArray(previousArray);
        }
    }
}
