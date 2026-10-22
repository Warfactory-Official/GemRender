package com.wf.gemrender.light;

import com.wf.gemrender.render.GlState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.water.GpuStampTimer;
import com.wf.gemrender.render.PassState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform1iv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;

/**
 * Traced shadow tiles: per shadowed light one {@code RES^2} R32F tile of first-hit distances through
 * {@link LightOccupancy} (spot: cone projection, point: octahedral). Atlas {@code TILES x TILES}.
 */
final class LightShadows {
    static final int RES = 128;

    static final int TILES = 4;

    static final int MAX = TILES * TILES;

    static final int UNIT = TextureUnits.LIGHT_SHADOWS;

    private final int[] tileLight = new int[MAX];
    private final PassState state = new PassState();
    private final GpuStampTimer gpu = new GpuStampTimer();
    private final int[] viewport = new int[4];

    private int program;
    private int tileLightLocation;
    private int texture;
    private int framebuffer;
    private int vertexArray;
    private int used;

    LightShadows() {
        Arrays.fill(tileLight, -1);
    }

    static String source(String name) {
        try (InputStream in = LightShadows.class.getResourceAsStream(
                "/assets/" + GemRender.MOD_ID + "/shaders/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void begin() {
        Arrays.fill(tileLight, 0, used, -1);
        used = 0;
    }

    /** @return tile, or -1 when the atlas is full */
    int claim(int lightIndex) {
        if (used == MAX) {
            return -1;
        }
        tileLight[used] = lightIndex;
        return used++;
    }

    int used() {
        return used;
    }

    private void ensure() {
        if (program != 0) {
            return;
        }
        program = GlPrograms.link("gemrender light trace", "#version 150\n" + source("light_trace.vert"),
                "#version 150\n" + LightShaders.include() + source("light_trace.frag"));
        glUniformBlockBinding(program, glGetUniformBlockIndex(program, "GemRenderLights"), LightFrame.UBO_BINDING);
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_grs_occupancy"), LightOccupancy.UNIT);
        tileLightLocation = glGetUniformLocation(program, "_grs_tileLight");
        glUseProgram(previous);

        int previousUnit = TextureUnits.activate(UNIT);
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, RES * TILES, RES * TILES, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        TextureUnits.restore(previousUnit);

        int previousFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousFramebuffer);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("GemRender light shadows: framebuffer status 0x" + Integer.toHexString(status));
        }
        vertexArray = glGenVertexArrays();
    }

    /**
     * After the light UBO and occupancy upload. GlStateManager only: Sodium skips a {@code _viewport} equal to
     * its last call => a raw {@code glViewport} leaves the restore skipped, terrain drawn into the atlas corner.
     */
    void trace() {
        ensure();
        bind();
        if (used == 0) {
            return;
        }
        gpu.begin();
        state.saveDeep();
        glGetIntegerv(GL_VIEWPORT, viewport);
        GlState.disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        try {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            GlStateManager._viewport(0, 0, RES * TILES, rows());
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindVertexArray(vertexArray);
            dispatch();
        } finally {
            state.restore();
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            gpu.end();
        }
    }

    /** Test: raw binds, no GlStateManager (render-thread assert). */
    void drawUnmanaged() {
        ensure();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, RES * TILES, rows());
        glUseProgram(program);
        glBindVertexArray(vertexArray);
        dispatch();
    }

    private int rows() {
        return RES * ((used + TILES - 1) / TILES);
    }

    private void dispatch() {
        glUniform1iv(tileLightLocation, tileLight);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    void bind() {
        int previousUnit = TextureUnits.activate(UNIT);
        glBindTexture(GL_TEXTURE_2D, texture);
        TextureUnits.restore(previousUnit);
    }

    String report() {
        return "shadowGpuUs=" + gpu.meanMicros();
    }

    void resetRun() {
        gpu.reset();
    }
}
