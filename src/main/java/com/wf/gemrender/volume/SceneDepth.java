package com.wf.gemrender.volume;



import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.render.Vanilla;
import com.wf.gemrender.render.PassState;
import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11C;

import java.io.IOException;



import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glGetInteger;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

public final class SceneDepth {
    private static final String MOD = GemRender.MOD_ID;
    public static final int TEXTURE_UNIT = TextureUnits.SCENE_DEPTH;

    private static final String VERSION = "#version 420 core\n";

    private static final SceneDepth INSTANCE = new SceneDepth();

    private final PassState state = new PassState();

    private final int[] viewport = new int[4];

    private int program;

    private int vao;

    private int depthLoc;

    private int znearLoc;

    private int zfarLoc;

    private boolean created;

    private boolean failed;

    private int fbo;

    private int texture;

    private int width = -1;

    private int height = -1;

    private SceneDepth() {
    }

    public static SceneDepth getInstance() {
        return INSTANCE;
    }




    public int textureId() {
        return texture;
    }

    public void bind() {
        if (texture == 0) {
            return;
        }

        int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
        try {
            GL11C.glBindTexture(GL_TEXTURE_2D, texture);
        } finally {
            TextureUnits.restore(previousUnit);
        }
    }

    public void capture() {
        if (!ensureCreated()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = com.wf.gemrender.render.Vanilla.mainTarget();
        ensureSize(main.width, main.height);

        GlAudit.Scope audit = GlAudit.open("volume:depth");
        state.save();
        glGetIntegerv(GL_VIEWPORT, viewport);
        int previousUnit = GlState.activeTexture();

        try {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, width, height);

            GlState.disableBlend();
            GlStateManager._disableDepthTest();

            int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
            try {
                GlStateManager._glUseProgram(program);
                //? if >=26.2 {
                /*// Reverse-Z: scene_depth.frag's forward formula with near/far swapped, as Projection swaps them.
                glUniform1f(znearLoc, Vanilla.depthFar());
                glUniform1f(zfarLoc, Vanilla.zNear());
                *///?} else {
                glUniform1f(znearLoc, Vanilla.zNear());
                glUniform1f(zfarLoc, Vanilla.depthFar());
                //?}
                glUniform1i(depthLoc, 0);

                GlStateManager._activeTexture(GL_TEXTURE0);
                int borrowed = glGetInteger(GL_TEXTURE_BINDING_2D);

                int borrowedSampler = GlState.pointSampler(0);
                try {
                    GlStateManager._bindTexture(Vanilla.depthTextureId(main));
                    drawFullscreen();
                } finally {
                    GlStateManager._bindTexture(borrowed);
                    GlState.restoreSampler(0, borrowedSampler);
                }
            } finally {
                GlStateManager._glUseProgram(previousProgram);
            }
        } finally {
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._activeTexture(previousUnit);
            state.restore();
            audit.close();
        }

        bind();
    }

    private boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }

        try {
            program = GlPrograms.link("scene_depth",
                    VERSION + GlPrograms.resource(MOD, "shaders/water_split.vert"),
                    VERSION + GlPrograms.resource(MOD, "shaders/scene_depth.frag"));

            depthLoc = glGetUniformLocation(program, "_gr_depth");
            znearLoc = glGetUniformLocation(program, "_gr_znear");
            zfarLoc = glGetUniformLocation(program, "_gr_zfar");

            vao = glGenVertexArrays();
            created = true;
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Volumetrics disabled: the scene depth copy failed to build. Raymarched "
                    + "volumes would draw through solid geometry without it.", e);
            return false;
        }
    }

    private void ensureSize(int newWidth, int newHeight) {
        if (width == newWidth && height == newHeight) {
            return;
        }
        width = newWidth;
        height = newHeight;

        if (texture != 0) {
            glDeleteTextures(texture);
            glDeleteFramebuffers(fbo);
        }

        texture = glGenTextures();

        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            //? if >=26.2 {
            /*glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
            *///?}
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }

        int previousFramebuffer = glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture, 0);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
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
