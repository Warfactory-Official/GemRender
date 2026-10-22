package com.wf.gemrender.light;

import com.wf.gemrender.render.Vanilla;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL30C.*;

/**
 * IR irradiance x albedo of the main view ({@link Lights.IrView#SPLIT}): RGBA16F, main target size. COLOR1 of
 * the main framebuffer from {@code AFTER_SKY} to the end of the hand pass; detached otherwise.
 * <p>COLOR1 is writable only while a program with {@code _grl_irOut} at location 1 is bound. Programs without
 * that output leave the IR image intact, including when the renderer switches away from and back to the main target.
 * <p>Gap: Fabulous translucent/particle/weather targets carry no COLOR1 => no IR there.
 */
public final class IrTarget {
    private static final float[] ZERO = new float[4];
    private static final int[] COLOR_ONLY = {GL_COLOR_ATTACHMENT0};
    private static final int[] COLOR_AND_IR = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1};

    private static int texture;
    private static int fbo;
    private static int width;
    private static int height;
    private static boolean pending;
    private static boolean attached;
    private static boolean filled;

    private IrTarget() {
    }

    /** Texture name; valid content when {@link #filled()}. 0 before the first SPLIT pass. */
    public static int texture() {
        return texture;
    }

    /** The last main view drew IR into {@link #texture()}. Read after the level render, before the next. */
    public static boolean filled() {
        return filled;
    }

    /** Nested level pass (scope PiP inside the main frame) never re-attaches or clears. */
    static void pass(boolean split) {
        pending = split && !attached;
    }

    public static void afterSky() {
        if (!pending || attached) {
            return;
        }
        pending = false;
        RenderSystem.assertOnRenderThread();
        RenderTarget main = com.wf.gemrender.render.Vanilla.mainTarget();
        if (texture == 0 || width != main.width || height != main.height) {
            allocate(main.width, main.height);
        }
        int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = Vanilla.fboOf(main);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, texture, 0);
        glDrawBuffers(COLOR_AND_IR);
        glClearBufferfv(GL_COLOR, 1, ZERO);
        attached = true;
        drawBuffersForProgram(glGetInteger(GL_CURRENT_PROGRAM));
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
    }

    static void end() {
        pending = false;
        filled = attached;
        if (!attached) {
            return;
        }
        attached = false;
        int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDrawBuffers(COLOR_ONLY);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
    }

    public static void programChanged(int program) {
        if (attached && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == fbo) {
            drawBuffersForProgram(program);
        }
    }

    public static void framebufferBound(int target, int framebuffer) {
        if (attached && framebuffer == fbo && (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER)) {
            drawBuffersForProgram(glGetInteger(GL_CURRENT_PROGRAM));
        }
    }

    /** Render passes can reset their draw buffers even when the program and framebuffer stay bound. */
    public static void refreshDrawBuffers() {
        if (attached && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == fbo) {
            drawBuffersForProgram(glGetInteger(GL_CURRENT_PROGRAM));
        }
    }

    /** The currently bound draw framebuffer owns COLOR1 as its IR image. */
    static void drawBuffersForProgram(int program) {
        var writesIr = program != 0 && glGetFragDataLocation(program, "_grl_irOut") == 1;
        glDrawBuffers(writesIr ? COLOR_AND_IR : COLOR_ONLY);
    }

    private static void allocate(int w, int h) {
        if (texture == 0) {
            texture = glGenTextures();
        }
        width = w;
        height = h;
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_HALF_FLOAT, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        //? if >=26.1 {
        /*glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        *///?}
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, previous);
    }
}
