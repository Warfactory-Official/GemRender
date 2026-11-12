package com.wf.gemrender.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;

public final class Vanilla {
    private Vanilla() {
    }

    public static RenderTarget mainTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    public static org.joml.Matrix4fc modelViewMatrix() {
        return com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
    }

    public static boolean shaderTransparency() {
        return Minecraft.useShaderTransparency();
    }

    public static int fboOf(RenderTarget target) {
        //? if >=26.1 {
        /*return fboOf(target.getColorTexture(), target.getDepthTexture());
        *///?} else {
        return target.frameBufferId;
        //?}
    }


    //? if >=26.1 {
    /*private static int scratchFbo = -1;

    private static int fboOf(com.mojang.blaze3d.textures.GpuTexture color,
                             com.mojang.blaze3d.textures.GpuTexture depth) {
        if (scratchFbo == -1) {
            scratchFbo = org.lwjgl.opengl.GL32C.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, scratchFbo);
        org.lwjgl.opengl.GL32C.glFramebufferTexture(GL_FRAMEBUFFER, org.lwjgl.opengl.GL32C.GL_COLOR_ATTACHMENT0,
                textureId(color), 0);
        org.lwjgl.opengl.GL32C.glFramebufferTexture(GL_FRAMEBUFFER, org.lwjgl.opengl.GL32C.GL_DEPTH_ATTACHMENT,
                depth == null ? 0 : textureId(depth), 0);
        return scratchFbo;
    }

    public static int textureId(com.mojang.blaze3d.textures.GpuTexture texture) {
        return ((com.mojang.blaze3d.opengl.GlTexture) texture).glId();
    }
    *///?}

    public static void bindWrite(RenderTarget target) {
        //? if >=26.1 {
        /*GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER,
				fboOf(target.getColorTexture(), target.getDepthTexture()));
        *///?} else {
        target.bindWrite(false);
        //?}
    }

    public static void intoMainTarget(Runnable draws) {
        //? if >=26.1 {
        /*RenderTarget target = mainTarget();
        int previousFbo = GlStateManager.getFrameBuffer(GL_FRAMEBUFFER);
        int[] previousViewport = new int[4];
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, previousViewport);

        bindWrite(target);
        GlStateManager._viewport(0, 0, target.width, target.height);
        try {
            draws.run();
        } finally {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFbo);
            GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2],
                    previousViewport[3]);
        }
        *///?} else {
        draws.run();
        //?}
    }

    public static void intoOutputOverride(Runnable draws) {
        //? if >=26.1 {
        /*com.mojang.blaze3d.textures.GpuTextureView color = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride;
        if (color == null) {
            draws.run();
            return;
        }
        com.mojang.blaze3d.textures.GpuTextureView depth = com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride;

        int previousFbo = GlStateManager.getFrameBuffer(GL_FRAMEBUFFER);
        int[] previousViewport = new int[4];
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, previousViewport);
        boolean previousScissor = org.lwjgl.opengl.GL11C.glIsEnabled(org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST);
        int[] previousScissorBox = new int[4];
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_SCISSOR_BOX, previousScissorBox);

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER,
				fboOf(color.texture(),
						depth == null ? null : depth.texture()));
        GlStateManager._viewport(0, 0, color.getWidth(0), color.getHeight(0));

        com.mojang.blaze3d.systems.ScissorState scissor = com.mojang.blaze3d.systems.RenderSystem
                .getScissorStateForRenderTypeDraws();
        if (scissor.enabled()) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissor.x(), scissor.y(), scissor.width(), scissor.height());
        } else {
            GlStateManager._disableScissorTest();
        }

        try {
            draws.run();
        } finally {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFbo);
            GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2],
                    previousViewport[3]);
            GlStateManager._scissorBox(previousScissorBox[0], previousScissorBox[1], previousScissorBox[2],
                    previousScissorBox[3]);
            if (previousScissor) {
                GlStateManager._enableScissorTest();
            } else {
                GlStateManager._disableScissorTest();
            }
        }
        *///?} else {
        draws.run();
        //?}
    }

    public static int depthTextureId(RenderTarget target) {
        //? if >=26.1 {
        /*return textureId(target.getDepthTexture());
        *///?} else {
        return target.getDepthTextureId();
        //?}
    }

    public static int colorTextureId(RenderTarget target) {
        //? if >=26.1 {
        /*return textureId(target.getColorTexture());
        *///?} else {
        return target.getColorTextureId();
        //?}
    }

    /** Vanilla lightmap; light layer left off. */
    public static int lightmapTextureId() {
        //? if >=26.1 {
        /*return 0; // TODO 26.1: lightmap is a GpuTexture view; CameraMedium never draws there yet.
        *///?} else {
        var light = Minecraft.getInstance().gameRenderer.lightTexture();
        light.turnOnLightLayer();
        int id = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2);
        light.turnOffLightLayer();
        return id;
        //?}
    }

    public static float zNear() {
        //? if >=26.1 {
        /*return net.minecraft.client.Camera.PROJECTION_Z_NEAR;
        *///?} else {
        return GameRenderer.PROJECTION_Z_NEAR;
        //?}
    }

    public static float depthFar() {
        //? if >=26.1 {
        /*var options = Minecraft.getInstance().options;
        return Math.max(options.getEffectiveRenderDistance() * 16 * 4.0F, options.cloudRange().get() * 16);
        *///?} else {
        return Minecraft.getInstance().gameRenderer.getDepthFar();
        //?}
    }

    public static float partialTick() {
        //? if >=26.1 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        *///?} elif >=1.21 {
        return Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(false);
        //?} elif fabric {
        /*return Minecraft.getInstance().getFrameTime();
        *///?} else {
        /*return Minecraft.getInstance().getPartialTick();
        *///?}
    }
}
