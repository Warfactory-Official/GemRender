package com.wf.gemrender.lens;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.PassState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;

/**
 * Shimmering air around points: an anomaly, heat over a fire, a shockwave front. Each frame's {@link #submit}ted
 * lenses distort the finished level (after weather, before the hand) in one full-screen pass over a copy of it, as
 * X-Ray's distortion does: within a lens' radius the scene is read at a screen-space offset from an animated noise
 * field anchored to the lens (soft toward the rim) and hazed slightly. Scene in front of a lens stays put.
 * Submissions last one frame.
 */
public final class Lenses {
    private static final String VERSION = "#version 420 core\n";

    /** {@code MAX_LENSES} in lens.frag; submissions past it are dropped. */
    public static final int MAX = 16;

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.lens"));

    private static final Lenses INSTANCE = new Lenses();

    private final PassState state = new PassState();
    private final float[] lens = new float[MAX * 4];
    private final float[] shape = new float[MAX * 2];
    private final double[] world = new double[MAX * 3];
    private final float[] matrix = new float[16];
    private int count;
    // Debug counters: Lenses#report.
    private boolean measure;
    private float measured = -1.0f;
    private int dropped;
    private int lastDrawn;
    private long framesDrawn;
    private long lastDrawNanos;

    private boolean created;
    private boolean failed;
    private int program;
    private int vao;
    private int copyFbo;
    private int copyTexture;
    private int copyWidth;
    private int copyHeight;
    private int drawFbo;
    private int clipLoc;
    private int toClipLoc;
    private int countLoc;
    private int timeLoc;
    private int lensLoc;
    private int shapeLoc;

    private Lenses() {
    }

    public static Lenses getInstance() {
        return INSTANCE;
    }

    /**
     * A lens for this frame only; render thread.
     *
     * @param radius blocks; nothing past it is touched
     * @param strength peak offset, in screen UV (X-Ray's {@code def_distort} 0.05 is a full-strength texel's
     *                 range), once the lens is large on screen; less while it is small
     * @param turn     radians the field is turned about the vertical: animate it to stir the shimmer
     */
    public void submit(double x, double y, double z, float radius, float strength, float turn) {
        if (!ENABLED || count >= MAX || radius <= 0.0f) {
            dropped++;
            return;
        }
        world[count * 3] = x;
        world[count * 3 + 1] = y;
        world[count * 3 + 2] = z;
        lens[count * 4 + 3] = radius;
        shape[count * 2] = strength;
        shape[count * 2 + 1] = turn;
        count++;
    }

    /** After the level, main target bound. {@code projection x modelView} = camera-relative world to clip. */
    public void draw(Matrix4fc projection, Matrix4fc modelView, Vec3 camera) {
        int n = count;
        count = 0;
        if (n == 0 || failed) {
            return;
        }
        for (int i = 0; i < n; i++) {
            lens[i * 4] = (float) (world[i * 3] - camera.x);
            lens[i * 4 + 1] = (float) (world[i * 3 + 1] - camera.y);
            lens[i * 4 + 2] = (float) (world[i * 3 + 2] - camera.z);
        }
        RenderTarget main = Vanilla.mainTarget();
        int color = Vanilla.colorTextureId(main);

        GlAudit.Scope audit = GlAudit.open("lens")
                .changes(GlAudit.DRAW_FRAMEBUFFER, GlAudit.READ_FRAMEBUFFER);
        state.saveDeep();
        try {
            if (!ensureCreated()) {
                return;
            }
            ensureCopy(main.width, main.height);
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, Vanilla.fboOf(main));
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
            glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height, GL_COLOR_BUFFER_BIT,
                    GL_NEAREST);

            // Main colour alone: its depth is read below, so it must not be the bound target's. Attached every
            // pass: a recreated main target can come back under the same texture name, and a cached attachment
            // would then keep drawing into the deleted one (nothing on screen).
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, drawFbo);
            glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, color, 0);
            GlStateManager._viewport(0, 0, main.width, main.height);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlState.colorMask(true, true, true, true);
            GlState.disableBlend();

            GlStateManager._glUseProgram(program);
            Matrix4f toClip = new Matrix4f(projection).mul(modelView);
            toClip.get(matrix);
            glUniformMatrix4fv(toClipLoc, false, matrix);
            toClip.invert().get(matrix);
            glUniformMatrix4fv(clipLoc, false, matrix);
            glUniform1i(countLoc, n);
            // Wrapped: float precision, and the noise drift has no period to keep.
            glUniform1f(timeLoc, (float) ((System.nanoTime() / 1.0E9D) % 3600.0D));
            glUniform4fv(lensLoc, lens);
            glUniform2fv(shapeLoc, shape);

            lastDrawn = n;
            framesDrawn++;
            lastDrawNanos = System.nanoTime();
            GlStateManager._activeTexture(GL_TEXTURE0);
            GlStateManager._bindTexture(copyTexture);
            GlStateManager._activeTexture(GL_TEXTURE1);
            GlStateManager._bindTexture(Vanilla.depthTextureId(main));
            int pointSampler1 = GlState.pointSampler(1);
            GlStateManager._glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            GlState.restoreSampler(1, pointSampler1);
            if (measure) {
                measure = false;
                measured = changed(main.width, main.height);
            }
        } finally {
            state.restore();
            Vanilla.bindWrite(main);
            audit.close();
        }
    }

    /** Nanos ({@link System#nanoTime}) of the last pass that drew any lens; 0 = never. */
    public long lastDrawNanos() {
        return lastDrawNanos;
    }

    /**
     * Debug, one-shot: the next pass reads back what it read and what it wrote, and {@link #measured} becomes the
     * share of pixels it changed. Stalls that frame.
     */
    public void measure() {
        measure = true;
        measured = -1.0f;
    }

    /** Share of the screen the last measured pass changed, 0..1; -1 = none measured yet. */
    public float measured() {
        return measured;
    }

    private float changed(int width, int height) {
        java.nio.ByteBuffer before = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        java.nio.ByteBuffer after = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        int pack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, copyFbo);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, before);
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFbo);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, after);
            int changed = 0;
            for (int i = 0; i < width * height * 4; i += 4) {
                for (int k = 0; k < 3; k++) {
                    if (Math.abs((before.get(i + k) & 0xFF) - (after.get(i + k) & 0xFF)) > 8) {
                        changed++;
                        break;
                    }
                }
            }
            return (float) changed / (width * height);
        } finally {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pack);
            org.lwjgl.system.MemoryUtil.memFree(before);
            org.lwjgl.system.MemoryUtil.memFree(after);
        }
    }

    /** One line for debug commands: pass state, last pass, totals. */
    public String report() {
        long ago = lastDrawNanos == 0 ? -1 : (System.nanoTime() - lastDrawNanos) / 1_000_000L;
        return "lenses: " + (!ENABLED ? "off (-Dgemrender.lens=false)" : failed ? "FAILED (shader)" : created
                ? "ready" : "not built yet") + ", pending=" + count + ", lastPass=" + lastDrawn + " lens(es) "
                + (ago < 0 ? "never" : ago + "ms ago") + ", passes=" + framesDrawn + ", dropped=" + dropped
                + ", copy=" + copyWidth + "x" + copyHeight + ", measured=" + (measured < 0.0f ? "-"
                : String.format(java.util.Locale.ROOT, "%.2f%% of pixels changed", measured * 100.0f));
    }

    private void ensureCopy(int width, int height) {
        if (copyWidth == width && copyHeight == height) {
            return;
        }
        GlStateManager._activeTexture(GL_TEXTURE0);
        GlStateManager._bindTexture(copyTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, copyTexture, 0);
        copyWidth = width;
        copyHeight = height;
    }

    private boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            program = GlPrograms.link("lens",
                    VERSION + GlPrograms.resource(GemRender.MOD_ID, "shaders/water_split.vert"),
                    VERSION + GlPrograms.resource(GemRender.MOD_ID, "shaders/lens.frag"));
            GlStateManager._glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "_gr_scene"), 0);
            glUniform1i(glGetUniformLocation(program, "_gr_depth"), 1);
            clipLoc = glGetUniformLocation(program, "_gr_clipToRelative");
            toClipLoc = glGetUniformLocation(program, "_gr_relativeToClip");
            countLoc = glGetUniformLocation(program, "_gr_count");
            timeLoc = glGetUniformLocation(program, "_gr_time");
            lensLoc = glGetUniformLocation(program, "_gr_lens");
            shapeLoc = glGetUniformLocation(program, "_gr_shape");
            GlStateManager._glUseProgram(0);
            vao = glGenVertexArrays();
            copyFbo = glGenFramebuffers();
            drawFbo = glGenFramebuffers();
            copyTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(copyTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            created = true;
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Lenses disabled: their shader failed to build.", e);
            return false;
        }
    }
}
