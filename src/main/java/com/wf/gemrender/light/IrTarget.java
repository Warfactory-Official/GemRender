package com.wf.gemrender.light;

import com.wf.gemrender.render.Vanilla;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL30C.*;

/**
 * IR irradiance x albedo of the main view ({@link Lights.IrView#SPLIT}): RGBA16F, main target size, from
 * {@code AFTER_SKY} to the end of the hand pass. Attached to every framebuffer drawing into the main colour texture
 * (main target; Veil {@code dynamic_main}, colour attachments 1..n = its dynamic buffers) at its first free colour
 * attachment, which equals {@link LightShaders#irLocation} of the programs drawn there; detached otherwise.
 * <p>The IR draw buffer is enabled only while a program with {@code _grl_irOut} at that index is bound. Programs
 * without that output leave the IR image intact, including when the renderer switches away from and back to the
 * main target. {@code _grl_irOut} at an index owned by another attachment => that draw buffer {@code GL_NONE}.
 * <p>Gap: Fabulous translucent/particle/weather targets carry no IR attachment => no IR there.
 */
public final class IrTarget {
    private static final float[] ZERO = new float[4];
    /** Framebuffer -> IR attachment index; 0 = draws elsewhere. Per level pass: framebuffer names are reused. */
    private static final Int2IntOpenHashMap INDEX = new Int2IntOpenHashMap();

    private static int texture;
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
        RenderTarget main = Vanilla.mainTarget();
        if (texture == 0 || width != main.width || height != main.height) {
            allocate(main.width, main.height);
        }
        int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, Vanilla.fboOf(main));
        attached = true;
        int index = index(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
        drawBuffers(index, index);
        glClearBufferfv(GL_COLOR, index, ZERO);
        drawBuffers(index, irLocation(glGetInteger(GL_CURRENT_PROGRAM)));
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
        for (var entry : INDEX.int2IntEntrySet()) {
            int index = entry.getIntValue();
            if (index == 0 || !glIsFramebuffer(entry.getIntKey())) {
                continue;
            }
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, entry.getIntKey());
            drawBuffers(index, -1);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, GL_TEXTURE_2D, 0, 0);
        }
        INDEX.clear();
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
    }

    public static void programChanged(int program) {
        if (attached) {
            int index = index(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            if (index != 0) {
                drawBuffers(index, irLocation(program));
            }
        }
    }

    public static void framebufferBound(int target, int framebuffer) {
        if (attached && (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER)) {
            int index = index(framebuffer);
            if (index != 0) {
                drawBuffers(index, irLocation(glGetInteger(GL_CURRENT_PROGRAM)));
            }
        }
    }

    /** Render passes can reset their draw buffers even when the program and framebuffer stay bound. */
    public static void refreshDrawBuffers() {
        programChanged(glGetInteger(GL_CURRENT_PROGRAM));
    }

    /** {@code framebuffer} bound to DRAW. First sight in this pass: main colour => IR attached at first free slot. */
    private static int index(int framebuffer) {
        if (framebuffer == 0) {
            return 0;
        }
        int known = INDEX.getOrDefault(framebuffer, -1);
        if (known >= 0) {
            return known;
        }
        int index = 0;
        if (glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_TEXTURE
                && glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == Vanilla.colorTextureId(Vanilla.mainTarget())) {
            index = 1;
            while (glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL_NONE) {
                index++;
            }
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, GL_TEXTURE_2D, texture, 0);
        }
        INDEX.put(framebuffer, index);
        return index;
    }

    /** Bound framebuffer with IR attached at {@code index}, {@code program} current. */
    static void drawBuffersFor(int index, int program) {
        drawBuffers(index, irLocation(program));
    }

    private static int irLocation(int program) {
        return program == 0 ? -1 : glGetFragDataLocation(program, "_grl_irOut");
    }

    /** Bound framebuffer, IR attached at {@code index}: colour 0..index-1, plus IR iff {@code location == index}. */
    private static void drawBuffers(int index, int location) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffers = stack.mallocInt(index + 1);
            for (int i = 0; i < index; i++) {
                buffers.put(i, i == location && i != 0 ? GL_NONE : GL_COLOR_ATTACHMENT0 + i);
            }
            buffers.put(index, GL_COLOR_ATTACHMENT0 + index);
            buffers.limit(location == index ? index + 1 : index);
            glDrawBuffers(buffers);
        }
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
