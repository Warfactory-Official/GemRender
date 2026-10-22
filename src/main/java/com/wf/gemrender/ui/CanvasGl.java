package com.wf.gemrender.ui;

import com.wf.gemrender.render.GlState;
import com.mojang.blaze3d.platform.GlStateManager;
//? if <26.2 {
import com.mojang.blaze3d.vertex.BufferUploader;
//?}
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** Program, VAO, buffers and the GL state saved around one canvas pass. */
final class CanvasGl {
    static final int STRIDE = 40;
    static final int SLOTS = 8;

    private int program;
    private int matrixLoc;
    private int vao;
    private int vbo;
    private int ibo;
    private int indexQuads;
    private boolean failed;

    private static final long INITIAL_RING = STRIDE * 16384L;
    private long ringBytes;
    private long cursor;

    private int savedProgram;
    private int savedFramebuffer;
    private boolean savedDeep;
    private boolean viewportStale = true;
    private int savedActiveTexture;
    private boolean savedBlend;
    private int savedSrcRgb;
    private int savedDstRgb;
    private int savedSrcAlpha;
    private int savedDstAlpha;
    private boolean savedDepthTest;
    private boolean savedDepthMask;
    private boolean savedCull;
    final int[] savedScissor = new int[4];
    boolean savedScissorTest;
    final int[] savedViewport = new int[4];

    boolean ensure() {
        if (program != 0) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            program = GlPrograms.link("gemrender:ui/canvas",
                    GlPrograms.resource(GemRender.MOD_ID, "shaders/ui/canvas.vert"),
                    GlPrograms.resource(GemRender.MOD_ID, "shaders/ui/canvas.frag"));
        } catch (IOException | RuntimeException e) {
            failed = true;
            GemRender.LOGGER.error("UI canvas disabled: its program failed to build", e);
            return false;
        }
        matrixLoc = glGetUniformLocation(program, "uMatrix");
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        GlStateManager._glUseProgram(program);
        for (int i = 0; i < SLOTS; i++) {
            glUniform1i(glGetUniformLocation(program, "uTex" + i), i);
        }
        GlStateManager._glUseProgram(previous);

        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        vao = glGenVertexArrays();
        ibo = glGenBuffers();
        glBindVertexArray(vao);
        allocateVertexBuffer(INITIAL_RING);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        indexQuads = 0;
        growIndices(1024);
        glBindVertexArray(previousVao);
        glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
        //? if <26.2 {
        BufferUploader.invalidate();
        //?}
        return true;
    }

    /** Requires {@link #vao} bound. */
    private void allocateVertexBuffer(long ringBytes) {
        if (vbo != 0) {
            glDeleteBuffers(vbo);
        }
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        nglBufferData(GL_ARRAY_BUFFER, ringBytes, 0L, GL_STREAM_DRAW);
        this.ringBytes = ringBytes;
        cursor = 0;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, STRIDE, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, 8);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, STRIDE, 16);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, STRIDE, 20);
        glEnableVertexAttribArray(4);
        glVertexAttribIPointer(4, 2, GL_UNSIGNED_BYTE, STRIDE, 36);
    }

    /**
     * Copies this flush's vertices into the next free range of a fixed buffer; returns the base vertex
     * to add to batch starts. Re-specifying the buffer per flush (glBufferData) measured ~30 us under
     * Mesa glthread; fences around a persistent map cost more (each is a glthread sync).
     */
    private int upload(long vertices, int vertexCount) {
        long bytes = (long) vertexCount * STRIDE;
        if (bytes * 4 > ringBytes) {
            long size = ringBytes;
            while (bytes * 4 > size) {
                size *= 2;
            }
            allocateVertexBuffer(size);
        }
        if (cursor + bytes > ringBytes) {
            cursor = 0;
        }
        nglBufferSubData(GL_ARRAY_BUFFER, cursor, bytes, vertices);
        int base = (int) (cursor / STRIDE);
        cursor += bytes;
        return base;
    }

    /** Requires {@link #vao} bound. */
    private void growIndices(int quads) {
        if (quads <= indexQuads) {
            return;
        }
        int capacity = Math.max(quads, indexQuads * 2);
        IntBuffer indices = MemoryUtil.memAllocInt(capacity * 6);
        try {
            for (int q = 0; q < capacity; q++) {
                int v = q * 4;
                indices.put(v).put(v + 1).put(v + 2).put(v).put(v + 2).put(v + 3);
            }
            indices.flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(indices);
        }
        indexQuads = capacity;
    }

    /**
     * GUI pass: shadow state only; scissor box and viewport queried only when an enclosing scissor is
     * on. {@code deep} (offscreen target): framebuffer and viewport too.
     */
    void save(boolean deep) {
        savedProgram = Math.max(GlShadow.program(), 0);
        savedActiveTexture = GlState.activeTexture();
        savedBlend = GlShadow.blend();
        savedSrcRgb = GlShadow.srcRgb();
        savedDstRgb = GlShadow.dstRgb();
        savedSrcAlpha = GlShadow.srcAlpha();
        savedDstAlpha = GlShadow.dstAlpha();
        savedDepthTest = GlShadow.depthTest();
        savedDepthMask = GlShadow.depthMask();
        savedCull = GlShadow.cull();
        savedScissorTest = GlShadow.scissor();
        savedDeep = deep;
        if (deep) {
            savedFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        }
        if (deep || savedScissorTest || viewportStale) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer box = stack.mallocInt(4);
                if (savedScissorTest) {
                    glGetIntegerv(GL_SCISSOR_BOX, box);
                    box.get(savedScissor);
                }
                IntBuffer viewport = stack.mallocInt(4);
                glGetIntegerv(GL_VIEWPORT, viewport);
                viewport.get(savedViewport);
            }
            viewportStale = deep;
        }
    }

    /** The GUI-pass viewport only changes with the window; offscreen begins re-query it. */
    void windowResized() {
        viewportStale = true;
    }

    void restore() {
        GlStateManager._glUseProgram(savedProgram);
        GlStateManager._glBindVertexArray(0);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, 0);
        //? if <26.2 {
        BufferUploader.invalidate();
        //?}
        if (savedDeep) {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, savedFramebuffer);
            GlStateManager._viewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
        }
        GlStateManager._blendFuncSeparate(savedSrcRgb, savedDstRgb, savedSrcAlpha, savedDstAlpha);
        if (savedBlend) {
            GlState.enableBlend();
        } else {
            GlState.disableBlend();
        }
        GlStateManager._depthMask(savedDepthMask);
        if (savedDepthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        if (savedCull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
        if (savedScissorTest) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(savedScissor[0], savedScissor[1], savedScissor[2], savedScissor[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
        GlStateManager._activeTexture(savedActiveTexture);
    }

    /**
     * Draw state for every batch; called per flush since vanilla calls between flushes reset it.
     * Returns the base vertex for {@link #draw}.
     */
    int bind(Matrix4f matrix, long vertices, int vertexCount, int maxBatchQuads) {
        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(matrixLoc, false, matrix.get(stack.mallocFloat(16)));
        }
        GlStateManager._glBindVertexArray(vao);
        growIndices(maxBatchQuads);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int base = upload(vertices, vertexCount);
        GlState.enableBlend();
        GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        return base;
    }

    void textures(int[] ids, int offset, int count) {
        for (int i = 0; i < count; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + i);
            GlStateManager._bindTexture(ids[offset + i]);
            glBindSampler(i, 0);
        }
    }

    void scissor(boolean enabled, int x, int y, int width, int height) {
        if (enabled) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(x, y, width, height);
        } else {
            GlStateManager._disableScissorTest();
        }
    }

    void draw(int firstVertex, int quads) {
        glDrawElementsBaseVertex(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0L, firstVertex);
    }

    void delete() {
        if (program != 0) {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            glDeleteBuffers(ibo);
        }
        program = 0;
        vao = vbo = ibo = 0;
        ringBytes = 0;
        indexQuads = 0;
        failed = false;
    }
}
