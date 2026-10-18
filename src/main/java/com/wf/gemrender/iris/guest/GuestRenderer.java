package com.wf.gemrender.iris.guest;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wf.gemrender.direct.DirectGuest;
import com.wf.gemrender.direct.DirectMaterial;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.mixin.iris.IrisRenderingPipelineAccessor;
import com.wf.gemrender.render.PassState;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL14C.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14C.GL_MAX;
import static org.lwjgl.opengl.GL14C.glBlendEquation;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;

/**
 * Draws a direct pass through the pack: the program Iris would pick for the vanilla entity render type, bound and
 * fed exactly as a vanilla draw at this point would be. Blended copies take wavelet OIT when {@link ForwardOit}
 * accepts the translucent program, else source-over in queue order with depth written (entity translucent).
 */
final class GuestRenderer {
    /** Iris's SHADOW_ENTITIES_CUTOUT alpha test, which it gives translucent entities too. */
    private static final float SHADOW_TRANSLUCENT_CUTOFF = 0.1f;

    private static final PassState STATE = new PassState();

    private GuestRenderer() {
    }

    static void flush(IrisRenderingPipeline pipeline, DirectPass pass, List<? extends DirectGuest.Batch> batches) {
        GuestPrograms.track(pipeline);

        Frame frame = new Frame(new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()), Minecraft.getInstance()
                .getWindow());
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        STATE.saveDeep();
        renderer.lightTexture()
                .turnOnLightLayer();
        renderer.overlayTexture()
                .setupOverlayColor();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL_LEQUAL);
        com.wf.gemrender.render.GlState.colorMask(true, true, true, true);
        try {
            if (pass == DirectPass.SHADOW) {
                for (DirectGuest.Batch batch : batches) {
                    DirectMaterial material = batch.material();
                    float cutoff = material.blended() ? SHADOW_TRANSLUCENT_CUTOFF : material.alphaCutoff();
                    draw(GuestPrograms.get(pipeline, Role.SHADOW, cutoff, null), batch, frame, false, false);
                }
                return;
            }

            Role opaque = pass == DirectPass.HAND ? Role.HAND : Role.ENTITIES;
            Role translucent = pass == DirectPass.HAND ? Role.HAND : Role.ENTITIES_TRANSLUCENT;
            List<DirectGuest.Batch> blended = new ArrayList<>();
            for (DirectGuest.Batch batch : batches) {
                DirectMaterial material = batch.material();
                if (material.blended()) {
                    blended.add(batch);
                } else {
                    draw(GuestPrograms.get(pipeline, opaque, material.alphaCutoff(), null), batch, frame, false,
                            !material.doubleSided());
                }
            }
            if (blended.isEmpty()) {
                return;
            }
            ForwardOit oit = GuestPrograms.oit(pipeline, translucent);
            if (oit == null || !oit(pipeline, translucent, oit, blended, frame)) {
                for (DirectGuest.Batch batch : blended) {
                    DirectMaterial material = batch.material();
                    draw(GuestPrograms.get(pipeline, translucent, material.alphaCutoff(), null), batch, frame, true,
                            !material.doubleSided());
                }
            }
        } finally {
            renderer.lightTexture()
                    .turnOffLightLayer();
            renderer.overlayTexture()
                    .teardownOverlayColor();
            GlStateManager._blendEquation(GL_FUNC_ADD);
            STATE.restore();
        }
    }

    private record Frame(Matrix4f modelView, Matrix4f projection, Window window) {
    }

    /** {@code program} null => pack lacks it; logged at build. */
    private static void draw(GuestProgram program, DirectGuest.Batch batch, Frame frame, boolean blend,
                             boolean cull) {
        if (program == null) {
            return;
        }
        if (blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GlStateManager._disableBlend();
        }
        GlStateManager._depthMask(true);
        cull(cull);
        ExtendedShader shader = apply(program, batch, frame);
        batch.draw();
        shader.clear();
    }

    private static ExtendedShader apply(GuestProgram program, DirectGuest.Batch batch, Frame frame) {
        RenderSystem.setShaderTexture(0, batch.texture());
        ExtendedShader shader = program.shader();
        //? if >=1.21 {
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES, frame.modelView(), frame.projection(), frame.window());
        //?} else {
        /*// 1.20.1 has no setDefaultUniforms: VertexBuffer._drawWithShader's sequence.
        for (int i = 0; i < 12; i++) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        set(shader.MODEL_VIEW_MATRIX, u -> u.set(frame.modelView()));
        set(shader.PROJECTION_MATRIX, u -> u.set(frame.projection()));
        set(shader.INVERSE_VIEW_ROTATION_MATRIX, u -> u.set(RenderSystem.getInverseViewRotationMatrix()));
        set(shader.COLOR_MODULATOR, u -> u.set(RenderSystem.getShaderColor()));
        set(shader.GLINT_ALPHA, u -> u.set(RenderSystem.getShaderGlintAlpha()));
        set(shader.FOG_START, u -> u.set(RenderSystem.getShaderFogStart()));
        set(shader.FOG_END, u -> u.set(RenderSystem.getShaderFogEnd()));
        set(shader.FOG_COLOR, u -> u.set(RenderSystem.getShaderFogColor()));
        set(shader.FOG_SHAPE, u -> u.set(RenderSystem.getShaderFogShape().getIndex()));
        set(shader.TEXTURE_MATRIX, u -> u.set(RenderSystem.getTextureMatrix()));
        set(shader.GAME_TIME, u -> u.set(RenderSystem.getShaderGameTime()));
        set(shader.SCREEN_SIZE, u -> u.set((float) frame.window().getWidth(), (float) frame.window().getHeight()));
        set(shader.LINE_WIDTH, u -> u.set(RenderSystem.getShaderLineWidth()));
        RenderSystem.setupShaderLights(shader);
        *///?}
        shader.apply();
        return shader;
    }

    //? if <1.21 {
    /*private static void set(@org.jetbrains.annotations.Nullable com.mojang.blaze3d.shaders.Uniform uniform,
                            java.util.function.Consumer<com.mojang.blaze3d.shaders.Uniform> value) {
        if (uniform != null) {
            value.accept(uniform);
        }
    }
    *///?}

    private static void cull(boolean cull) {
        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
    }

    /** False: a producer failed to build (logged); caller draws native. */
    private static boolean oit(IrisRenderingPipeline pipeline, Role role, ForwardOit oit,
                               List<DirectGuest.Batch> batches, Frame frame) {
        GuestProgram target = GuestPrograms.get(pipeline, role, 0.0f, null);
        if (target == null) {
            return false;
        }
        GuestProgram[][] producers = new GuestProgram[OitPass.values().length][batches.size()];
        for (OitPass pass : OitPass.values()) {
            for (int i = 0; i < batches.size(); i++) {
                producers[pass.ordinal()][i] = GuestPrograms.get(pipeline, role, batches.get(i)
                        .material()
                        .alphaCutoff(), pass);
                if (producers[pass.ordinal()][i] == null) {
                    return false;
                }
            }
        }

        RenderTargets renderTargets = ((IrisRenderingPipelineAccessor) pipeline).gemrender$renderTargets();
        GuestOitTargets targets = GuestOit.targets(oit);
        targets.prepare(renderTargets.getDepthTexture(), renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight());

        for (OitPass pass : OitPass.values()) {
            int framebuffer = switch (pass) {
                case DEPTH_RANGE -> targets.depthRangeFbo();
                case COEFFICIENTS -> targets.coefficientsFbo();
                case EVALUATE -> targets.accumulateFbo();
            };
            for (int i = 0; i < batches.size(); i++) {
                DirectGuest.Batch batch = batches.get(i);
                ExtendedShader shader = apply(producers[pass.ordinal()][i], batch, frame);
                // apply() bound the pack's framebuffer and blend, per buffer behind GlStateManager's cache:
                // producers own both, set uncached.
                GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                GlStateManager._enableBlend();
                glEnable(GL_BLEND);
                int equation = pass == OitPass.DEPTH_RANGE ? GL_MAX : GL_FUNC_ADD;
                GlStateManager._blendEquation(equation);
                glBlendEquation(equation);
                GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
                glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
                GlStateManager._depthMask(false);
                cull(!batch.material()
                        .doubleSided());
                GuestOit.bindProducerTextures(oit, pass);
                batch.draw();
                shader.clear();
            }
        }
        GuestOit.unbindProducerTextures(oit);
        GlStateManager._blendEquation(GL_FUNC_ADD);

        target.target(pipeline)
                .bind();
        GuestOit.composite(oit);
        return true;
    }
}
