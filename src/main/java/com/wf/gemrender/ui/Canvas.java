package com.wf.gemrender.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;
*///?} else {
import com.wf.gemrender.mixin.ui.BufferSourceAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Immediate-mode 2D recorder: every primitive becomes one quad in a CPU vertex arena, drawn in as few
 * GL calls as texture slots and clip changes allow. Steady state allocates nothing.
 *
 * <p>Units: GUI-scaled pixels for {@link #begin(GuiGraphics)}, caller-chosen for
 * {@link #begin(CanvasTarget, float, float, int)}. Angles in radians, 0 = +x, positive turns towards
 * +y (clockwise on screen). Colours ARGB, taken literally (alpha 0 = invisible).
 *
 * <p>Edges of lines, arcs, discs and rounded rectangles are antialiased in the fragment shader from
 * screen-space derivatives, so they stay one pixel soft under any {@link #scale}.
 *
 * <p>Render thread only. Not re-entrant: one {@code begin}/{@code end} at a time per instance.
 */
public final class Canvas {
    static final int SOLID = 0;
    static final int TEXTURE = 1;
    static final int INTENSITY = 2;
    static final int LINE = 3;
    static final int ARC = 4;
    static final int ROUND_RECT = 5;

    public static final float TAU = (float) (Math.PI * 2);

    private static Canvas shared;

    private final CanvasGl gl = new CanvasGl();
    private final CanvasFont font = new CanvasFont();
    private final TextSink textSink = new TextSink();
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f scratch = new Matrix4f();

    private long vertices;
    private int vertexCapacity;
    private int vertexCount;

    private int[] batchFirst = new int[16];
    private int[] batchTextures = new int[16 * CanvasGl.SLOTS];
    private int[] batchTextureCount = new int[16];
    private int[] batchClip = new int[16 * 5];
    private int batchCount;
    private boolean clipDirty;

    private float[] matrices = new float[6 * 16];
    private int depth;
    private float a;
    private float b;
    private float c;
    private float d;
    private float e;
    private float f;

    private int[] clips = new int[5 * 16];
    private int clipDepth;

    private float pixelsPerUnit;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private boolean active;
    private boolean suspended;
    private int framebuffer;
    private float unitsW;
    private float unitsH;
    private int lastWindowW;
    private int lastWindowH;
    //? if >=26.2 {
    /*private static int guiFrame;
    private final java.util.ArrayList<GuiLayer> guiLayers = new java.util.ArrayList<>();
    private int guiLayersUsed;
    private int guiLayerFrame = -1;
    private GuiLayer guiLayer;
    private GuiGraphicsExtractor guiGraphics;

    private static final class GuiLayer {
        GpuTexture texture;
        GpuTextureView view;
    }
    *///?}

    public static Canvas shared() {
        RenderSystem.assertOnRenderThread();
        if (shared == null) {
            shared = new Canvas();
        }
        return shared;
    }

    static void reload() {
        if (shared != null) {
            shared.gl.delete();
        }
    }

    public boolean active() {
        return active;
    }

    //? if >=26.2 {
    /*/^*
     * Draws over the GUI in the {@code graphics} pose. Extraction precedes the GUI pass (which clears the
     * main target), so recording goes to a transparent window-sized layer; {@link #end()} and
     * {@link #suspend()} submit it as one premultiplied blit placed above everything extracted so far.
     * Anything vanilla extracts afterwards lands on top. The enclosing vanilla scissor clips the blit.
     ^/
    public Canvas begin(GuiGraphicsExtractor graphics) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        guiGraphics = graphics;
        beginFramebuffer(acquireGuiLayer(guiW * scale, guiH * scale), guiW * scale, guiH * scale, guiW, guiH,
                true, true, 0);
        base.mul(graphics.pose());
        computePixelsPerUnit();
        return this;
    }

    /^* Layers are reused once the frame's GUI pass has drawn them. ^/
    static void endFrame() {
        guiFrame++;
    }

    /^*
     * Vanilla filters through sampler objects and leaves texture magnification at the GL default (LINEAR);
     * Canvas binds sampler 0 => NEAREST on the texture itself, as 1.21 textures carried. Vanilla never reads it.
     ^/
    static int magNearest(int glTexture) {
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, glTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, previous);
        return glTexture;
    }

    private int acquireGuiLayer(int width, int height) {
        if (guiLayerFrame != guiFrame) {
            guiLayerFrame = guiFrame;
            guiLayersUsed = 0;
        }
        if (guiLayersUsed == guiLayers.size()) {
            guiLayers.add(new GuiLayer());
        }
        GuiLayer layer = guiLayers.get(guiLayersUsed++);
        if (layer.texture == null || layer.texture.getWidth(0) != width || layer.texture.getHeight(0) != height) {
            if (layer.texture != null) {
                layer.view.close();
                layer.texture.close();
            }
            GpuDevice device = RenderSystem.getDevice();
            layer.texture = device.createTexture(() -> "gemrender:canvas_gui",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1);
            layer.view = device.createTextureView(layer.texture);
        }
        guiLayer = layer;
        return Vanilla.fboOf(layer.texture);
    }

    private void submitGuiLayer() {
        guiGraphics.guiRenderState.addGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(guiLayer.view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                new Matrix3x2f(), 0, 0, (int) unitsW, (int) unitsH, 0.0f, 1.0f, 1.0f, 0.0f, -1,
                guiGraphics.peekScissorStack()));
    }
    *///?} else {
    /**
     * Draws over the GUI in the {@code graphics} pose. Flushes the vanilla batch first so ordering
     * holds; anything vanilla draws after {@link #end()} lands on top. An enclosing vanilla scissor is
     * inherited as the root clip.
     */
    public Canvas begin(GuiGraphics graphics) {
        if (!((BufferSourceAccessor) graphics.bufferSource()).gemrender$startedBuilders().isEmpty()) {
            graphics.flush();
        }
        int windowW = Minecraft.getInstance().getWindow().getWidth();
        int windowH = Minecraft.getInstance().getWindow().getHeight();
        if (windowW != lastWindowW || windowH != lastWindowH) {
            lastWindowW = windowW;
            lastWindowH = windowH;
            gl.windowResized();
        }
        Matrix4f pose = graphics.pose().last().pose();
        base.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        start(0);
        viewportX = gl.savedViewport[0];
        viewportY = gl.savedViewport[1];
        viewportW = gl.savedViewport[2];
        viewportH = gl.savedViewport[3];
        rootClip(gl.savedScissorTest, gl.savedScissor[0], gl.savedScissor[1], gl.savedScissor[2], gl.savedScissor[3]);
        computePixelsPerUnit();
        unitsW = graphics.guiWidth();
        unitsH = graphics.guiHeight();
        return this;
    }
    //?}

    /**
     * Draws into {@code target} with ({@code 0,0}) top-left and ({@code width,height}) bottom-right in
     * canvas units. Clears to {@code clearArgb}. Rows are stored so that a GUI blit with v0 = 0 at the
     * top shows the image upright. Output colour is premultiplied by alpha.
     */
    public Canvas begin(CanvasTarget target, float width, float height, int clearArgb) {
        target.ensure();
        return beginFramebuffer(target.framebuffer(), target.width(), target.height(), width, height, false, true,
                clearArgb);
    }

    /**
     * Draws into any framebuffer (e.g. a camera feed's {@code RenderTarget}) without clearing.
     * {@code topRowFirst}: unit y = 0 lands on the framebuffer's top row, as for a picture rendered by
     * a camera; false = on row 0, as {@link CanvasTarget} stores it.
     */
    public Canvas begin(int framebuffer, int pixelWidth, int pixelHeight, float width, float height, boolean topRowFirst) {
        return beginFramebuffer(framebuffer, pixelWidth, pixelHeight, width, height, topRowFirst, false, 0);
    }

    private Canvas beginFramebuffer(int framebuffer, int pixelWidth, int pixelHeight, float width, float height,
                                    boolean topRowFirst, boolean clear, int clearArgb) {
        start(framebuffer);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        viewportX = 0;
        viewportY = 0;
        viewportW = pixelWidth;
        viewportH = pixelHeight;
        GlStateManager._viewport(0, 0, viewportW, viewportH);
        GlStateManager._disableScissorTest();
        if (clear) {
            GlState.colorMask(true, true, true, true);
            GlState.clearColor((clearArgb >>> 16 & 0xFF) / 255f, (clearArgb >>> 8 & 0xFF) / 255f,
                    (clearArgb & 0xFF) / 255f, (clearArgb >>> 24) / 255f);
            GlState.clear(GL_COLOR_BUFFER_BIT);
        }
        if (topRowFirst) {
            base.setOrtho(0, width, height, 0, -1, 1);
        } else {
            base.setOrtho(0, width, 0, height, -1, 1);
        }
        rootClip(false, 0, 0, 0, 0);
        computePixelsPerUnit();
        unitsW = width;
        unitsH = height;
        return this;
    }

    /** {@code framebuffer} 0 = the GUI pass (current framebuffer kept). */
    private void start(int framebuffer) {
        RenderSystem.assertOnRenderThread();
        if (active) {
            throw new IllegalStateException("Canvas already active");
        }
        active = true;
        suspended = false;
        this.framebuffer = framebuffer;
        gl.save(framebuffer != 0);
        font.refresh();
        vertexCount = 0;
        batchCount = 0;
        depth = 0;
        a = 1;
        b = 0;
        c = 0;
        d = 1;
        e = 0;
        f = 0;
        clipDepth = 0;
    }

    private void rootClip(boolean enabled, int x, int y, int w, int h) {
        clips[0] = enabled ? 1 : 0;
        clips[1] = x;
        clips[2] = y;
        clips[3] = x + w;
        clips[4] = y + h;
        clipDepth = 0;
        clipDirty = true;
    }

    private void computePixelsPerUnit() {
        float px = base.m00() * viewportW * 0.5f;
        float py = base.m01() * viewportH * 0.5f;
        pixelsPerUnit = (float) Math.sqrt(px * px + py * py);
        if (!(pixelsPerUnit > 0)) {
            pixelsPerUnit = 1;
        }
    }

    public void end() {
        requireActive();
        if (!suspended) {
            flush();
            gl.restore();
            //? if >=26.2 {
            /*if (guiGraphics != null) {
                submitGuiLayer();
            }
            *///?}
        }
        active = false;
        suspended = false;
        framebuffer = 0;
        //? if >=26.2 {
        /*guiGraphics = null;
        *///?}
    }

    /**
     * Draws everything recorded so far and hands GL back to vanilla, e.g. for an item or entity
     * render. Transform and clip stacks survive; {@link #resume()} before recording again.
     */
    public void suspend() {
        requireActive();
        if (suspended) {
            return;
        }
        flush();
        gl.restore();
        //? if >=26.2 {
        /*if (guiGraphics != null) {
            submitGuiLayer();
        }
        *///?}
        suspended = true;
    }

    public void resume() {
        requireActive();
        if (!suspended) {
            return;
        }
        //? if >=26.2 {
        /*if (guiGraphics != null) {
            framebuffer = acquireGuiLayer(viewportW, viewportH);
        }
        *///?}
        gl.save(framebuffer != 0);
        if (framebuffer != 0) {
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            GlStateManager._viewport(0, 0, viewportW, viewportH);
            //? if >=26.2 {
            /*if (guiGraphics != null) {
                GlStateManager._disableScissorTest();
                GlState.colorMask(true, true, true, true);
                GlState.clearColor(0, 0, 0, 0);
                GlState.clear(GL_COLOR_BUFFER_BIT);
            }
            *///?}
        }
        suspended = false;
        clipDirty = true;
    }

    /** Multiplies the current canvas transform onto {@code pose}, for vanilla drawing while suspended. */
    public void applyTo(PoseStack pose) {
        scratch.identity();
        scratch.m00(a).m01(b).m10(c).m11(d).m30(e).m31(f);
        pose.last().pose().mul(scratch);
    }

    /** Drawing area in canvas units: GUI size (unscaled pose) or the target's begin size. */
    public float width() {
        return unitsW;
    }

    public float height() {
        return unitsH;
    }

    public float pixelsPerUnit() {
        return pixelsPerUnit * unitScale();
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Canvas not active");
        }
    }

    // ---------------------------------------------------------------- transform

    public Canvas push() {
        int at = depth * 6;
        if (at + 6 > matrices.length) {
            matrices = Arrays.copyOf(matrices, matrices.length * 2);
        }
        matrices[at] = a;
        matrices[at + 1] = b;
        matrices[at + 2] = c;
        matrices[at + 3] = d;
        matrices[at + 4] = e;
        matrices[at + 5] = f;
        depth++;
        return this;
    }

    public Canvas pop() {
        if (depth == 0) {
            throw new IllegalStateException("Canvas transform stack underflow");
        }
        int at = --depth * 6;
        a = matrices[at];
        b = matrices[at + 1];
        c = matrices[at + 2];
        d = matrices[at + 3];
        e = matrices[at + 4];
        f = matrices[at + 5];
        return this;
    }

    public Canvas translate(float x, float y) {
        e += a * x + c * y;
        f += b * x + d * y;
        return this;
    }

    public Canvas scale(float s) {
        return scale(s, s);
    }

    public Canvas scale(float sx, float sy) {
        a *= sx;
        b *= sx;
        c *= sy;
        d *= sy;
        return this;
    }

    public Canvas rotate(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float na = a * cos + c * sin;
        float nb = b * cos + d * sin;
        float nc = c * cos - a * sin;
        float nd = d * cos - b * sin;
        a = na;
        b = nb;
        c = nc;
        d = nd;
        return this;
    }

    private float unitScale() {
        return (float) Math.sqrt(Math.abs(a * d - b * c));
    }

    private float margin() {
        float ppu = pixelsPerUnit * unitScale();
        return ppu > 0 ? 1f / ppu : 1f;
    }

    // ---------------------------------------------------------------- clip

    /**
     * Intersects the clip with the axis-aligned screen bounds of the given rectangle (under rotation,
     * its bounding box).
     */
    public Canvas pushClip(float x0, float y0, float x1, float y1) {
        int at = (clipDepth + 1) * 5;
        if (at + 5 > clips.length) {
            clips = Arrays.copyOf(clips, clips.length * 2);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float tx = a * x + c * y + e;
            float ty = b * x + d * y + f;
            float cx = base.m00() * tx + base.m10() * ty + base.m30();
            float cy = base.m01() * tx + base.m11() * ty + base.m31();
            float cw = base.m03() * tx + base.m13() * ty + base.m33();
            float px = viewportX + (cx / cw * 0.5f + 0.5f) * viewportW;
            float py = viewportY + (cy / cw * 0.5f + 0.5f) * viewportH;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        int parent = clipDepth * 5;
        int ix0 = Math.round(minX);
        int iy0 = Math.round(minY);
        int ix1 = Math.round(maxX);
        int iy1 = Math.round(maxY);
        if (clips[parent] != 0) {
            ix0 = Math.max(ix0, clips[parent + 1]);
            iy0 = Math.max(iy0, clips[parent + 2]);
            ix1 = Math.min(ix1, clips[parent + 3]);
            iy1 = Math.min(iy1, clips[parent + 4]);
        }
        clips[at] = 1;
        clips[at + 1] = ix0;
        clips[at + 2] = iy0;
        clips[at + 3] = Math.max(ix0, ix1);
        clips[at + 4] = Math.max(iy0, iy1);
        clipDepth++;
        clipDirty = true;
        return this;
    }

    public Canvas popClip() {
        if (clipDepth == 0) {
            throw new IllegalStateException("Canvas clip stack underflow");
        }
        clipDepth--;
        clipDirty = true;
        return this;
    }

    // ---------------------------------------------------------------- shapes

    public Canvas fill(float x0, float y0, float x1, float y1, int argb) {
        return gradient(x0, y0, x1, y1, argb, argb);
    }

    /** Vertical gradient, {@code top} at {@code y0}. */
    public Canvas gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        begin(SOLID, 0);
        vertex(x0, y0, 0, 0, top, 0, 0, 0, 0, SOLID, 0);
        vertex(x0, y1, 0, 0, bottom, 0, 0, 0, 0, SOLID, 0);
        vertex(x1, y1, 0, 0, bottom, 0, 0, 0, 0, SOLID, 0);
        vertex(x1, y0, 0, 0, top, 0, 0, 0, 0, SOLID, 0);
        return this;
    }

    /** Outline of thickness {@code t} drawn inside the rectangle. */
    public Canvas frame(float x0, float y0, float x1, float y1, float t, int argb) {
        fill(x0, y0, x1, y0 + t, argb);
        fill(x0, y1 - t, x1, y1, argb);
        fill(x0, y0 + t, x0 + t, y1 - t, argb);
        return fill(x1 - t, y0 + t, x1, y1 - t, argb);
    }

    public Canvas triangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        return quad(x0, y0, x1, y1, x2, y2, x2, y2, argb);
    }

    public Canvas quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        begin(SOLID, 0);
        vertex(x0, y0, 0, 0, argb, 0, 0, 0, 0, SOLID, 0);
        vertex(x1, y1, 0, 0, argb, 0, 0, 0, 0, SOLID, 0);
        vertex(x2, y2, 0, 0, argb, 0, 0, 0, 0, SOLID, 0);
        vertex(x3, y3, 0, 0, argb, 0, 0, 0, 0, SOLID, 0);
        return this;
    }

    /** Convex polygon as a fan around point 0; {@code xy} interleaved, {@code points >= 3}. No edge AA. */
    public Canvas fan(float[] xy, int points, int argb) {
        for (int i = 1; i + 1 < points; i++) {
            triangle(xy[0], xy[1], xy[i * 2], xy[i * 2 + 1], xy[i * 2 + 2], xy[i * 2 + 3], argb);
        }
        return this;
    }

    /** Antialiased segment with butt ends. */
    public Canvas line(float x0, float y0, float x1, float y1, float width, int argb) {
        return segment(x0, y0, x1, y1, width, 0, 0, argb);
    }

    /**
     * Antialiased polyline; joints are covered by extending inner ends by half the width, so a
     * translucent colour darkens slightly at each joint.
     */
    public Canvas polyline(float[] xy, int points, float width, boolean closed, int argb) {
        int segments = closed ? points : points - 1;
        float half = width * 0.5f;
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % points;
            float startCap = closed || i > 0 ? half : 0;
            float endCap = closed || i < segments - 1 ? half : 0;
            segment(xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1], width, startCap, endCap, argb);
        }
        return this;
    }

    /** Dashed segment: {@code dash} on, {@code gap} off, starting with a dash at ({@code x0,y0}). */
    public Canvas dashed(float x0, float y0, float x1, float y1, float width, float dash, float gap, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6f || dash <= 0) {
            return this;
        }
        float ux = dx / length;
        float uy = dy / length;
        for (float s = 0; s < length; s += dash + gap) {
            float t = Math.min(length, s + dash);
            segment(x0 + ux * s, y0 + uy * s, x0 + ux * t, y0 + uy * t, width, 0, 0, argb);
        }
        return this;
    }

    private Canvas segment(float x0, float y0, float x1, float y1, float width, float capStart, float capEnd, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6f) {
            return this;
        }
        float ux = dx / length;
        float uy = dy / length;
        float m = margin();
        float halfLength = (length + capStart + capEnd) * 0.5f;
        float halfWidth = width * 0.5f;
        float shift = (capEnd - capStart) * 0.5f;
        float cx = (x0 + x1) * 0.5f + ux * shift;
        float cy = (y0 + y1) * 0.5f + uy * shift;
        float along = halfLength + m;
        float across = halfWidth + m;
        float nx = -uy;
        float ny = ux;
        begin(LINE, 0);
        vertex(cx - ux * along - nx * across, cy - uy * along - ny * across, -along, -across, argb, halfLength, halfWidth, 0, 0, LINE, 0);
        vertex(cx - ux * along + nx * across, cy - uy * along + ny * across, -along, across, argb, halfLength, halfWidth, 0, 0, LINE, 0);
        vertex(cx + ux * along + nx * across, cy + uy * along + ny * across, along, across, argb, halfLength, halfWidth, 0, 0, LINE, 0);
        vertex(cx + ux * along - nx * across, cy + uy * along - ny * across, along, -across, argb, halfLength, halfWidth, 0, 0, LINE, 0);
        return this;
    }

    /** Full ring centred on radius {@code r}. */
    public Canvas ring(float cx, float cy, float r, float thickness, int argb) {
        return arc(cx, cy, r, thickness, 0, TAU, argb);
    }

    /** Ring section centred on radius {@code r}, from {@code start} through {@code sweep} (> 0). */
    public Canvas arc(float cx, float cy, float r, float thickness, float start, float sweep, int argb) {
        float half = thickness * 0.5f;
        return arcShape(cx, cy, r + half, r - half, start, sweep, argb);
    }

    public Canvas disc(float cx, float cy, float r, int argb) {
        return arcShape(cx, cy, r, -1, 0, TAU, argb);
    }

    /** Filled pie slice. */
    public Canvas sector(float cx, float cy, float r, float start, float sweep, int argb) {
        return arcShape(cx, cy, r, -1, start, sweep, argb);
    }

    private Canvas arcShape(float cx, float cy, float outer, float inner, float start, float sweep, int argb) {
        if (sweep <= 0 || outer <= 0) {
            return this;
        }
        float s = sweep >= TAU ? TAU : sweep;
        float base = start % TAU;
        if (base < 0) {
            base += TAU;
        }
        float h = outer + margin();
        begin(ARC, 0);
        vertex(cx - h, cy - h, -h, -h, argb, outer, inner, base, s, ARC, 0);
        vertex(cx - h, cy + h, -h, h, argb, outer, inner, base, s, ARC, 0);
        vertex(cx + h, cy + h, h, h, argb, outer, inner, base, s, ARC, 0);
        vertex(cx + h, cy - h, h, -h, argb, outer, inner, base, s, ARC, 0);
        return this;
    }

    public Canvas roundRect(float x0, float y0, float x1, float y1, float radius, int argb) {
        return roundShape(x0, y0, x1, y1, radius, 0, argb);
    }

    /** Rounded outline of {@code stroke} width drawn inside the rectangle. */
    public Canvas roundFrame(float x0, float y0, float x1, float y1, float radius, float stroke, int argb) {
        return roundShape(x0, y0, x1, y1, radius, Math.max(stroke, 1e-3f), argb);
    }

    private Canvas roundShape(float x0, float y0, float x1, float y1, float radius, float stroke, int argb) {
        float hw = Math.abs(x1 - x0) * 0.5f;
        float hh = Math.abs(y1 - y0) * 0.5f;
        float r = Math.min(Math.max(radius, 0), Math.min(hw, hh));
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;
        float m = margin();
        float ex = hw + m;
        float ey = hh + m;
        begin(ROUND_RECT, 0);
        vertex(cx - ex, cy - ey, -ex, -ey, argb, hw, hh, r, stroke, ROUND_RECT, 0);
        vertex(cx - ex, cy + ey, -ex, ey, argb, hw, hh, r, stroke, ROUND_RECT, 0);
        vertex(cx + ex, cy + ey, ex, ey, argb, hw, hh, r, stroke, ROUND_RECT, 0);
        vertex(cx + ex, cy - ey, ex, -ey, argb, hw, hh, r, stroke, ROUND_RECT, 0);
        return this;
    }

    // ---------------------------------------------------------------- images

    public Canvas sprite(ResourceLocation texture, float x0, float y0, float x1, float y1, float u0, float v0,
                         float u1, float v1, int argb) {
        return sprite(textureId(texture), x0, y0, x1, y1, u0, v0, u1, v1, argb);
    }

    public Canvas sprite(ResourceLocation texture, float x0, float y0, float x1, float y1, int argb) {
        return sprite(textureId(texture), x0, y0, x1, y1, 0, 0, 1, 1, argb);
    }

    /** {@code glTexture}: raw GL texture name. */
    public Canvas sprite(int glTexture, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                         float v1, int argb) {
        int slot = begin(TEXTURE, glTexture);
        vertex(x0, y0, u0, v0, argb, 0, 0, 0, 0, TEXTURE, slot);
        vertex(x0, y1, u0, v1, argb, 0, 0, 0, 0, TEXTURE, slot);
        vertex(x1, y1, u1, v1, argb, 0, 0, 0, 0, TEXTURE, slot);
        vertex(x1, y0, u1, v0, argb, 0, 0, 0, 0, TEXTURE, slot);
        return this;
    }

    public Canvas sprite(TextureAtlasSprite sprite, float x0, float y0, float x1, float y1, int argb) {
        return sprite(textureId(sprite.atlasLocation()), x0, y0, x1, y1, sprite.getU0(), sprite.getV0(),
                sprite.getU1(), sprite.getV1(), argb);
    }

    /**
     * Nine-slice of the texture region (u0,v0)-(u1,v1): corners keep {@code inset} canvas units and
     * {@code uInset}/{@code vInset} texture units, edges and centre stretch.
     */
    public Canvas nineSlice(int glTexture, float x0, float y0, float x1, float y1, float inset, float u0, float v0,
                            float u1, float v1, float uInset, float vInset, int argb) {
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                sprite(glTexture, cut(i, x0, x1, inset), cut(j, y0, y1, inset), cut(i + 1, x0, x1, inset),
                        cut(j + 1, y0, y1, inset), cut(i, u0, u1, uInset), cut(j, v0, v1, vInset),
                        cut(i + 1, u0, u1, uInset), cut(j + 1, v0, v1, vInset), argb);
            }
        }
        return this;
    }

    private static float cut(int index, float from, float to, float inset) {
        return switch (index) {
            case 0 -> from;
            case 1 -> from + inset;
            case 2 -> to - inset;
            default -> to;
        };
    }

    public static int textureId(ResourceLocation texture) {
        //? if >=26.2 {
        /*return magNearest(Vanilla.textureId(Minecraft.getInstance()
                .getTextureManager()
                .getTexture(texture)
                .getTexture()));
        *///?} else {
        return Minecraft.getInstance()
                .getTextureManager()
                .getTexture(texture)
                .getId();
        //?}
    }

    // ---------------------------------------------------------------- text

    public int lineHeight() {
        return font.font().lineHeight;
    }

    /** Changes whenever cached glyph metrics drop (font reload, font set or filter change); key for cached widths. */
    public int fontVersion() {
        return font.version;
    }

    public float width(CharSequence text) {
        return width(text, 0, text.length());
    }

    /** Matches {@code Font.width} for unstyled text. */
    public float width(CharSequence text, int start, int end) {
        var set = font.fontSet();
        boolean fishy = font.filterFishy();
        float width = 0;
        for (int i = start; i < end; ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            width += font.glyph(set, fishy, cp).advance;
        }
        return width;
    }

    public float width(FormattedCharSequence text) {
        return font.font().width(text);
    }

    public float text(CharSequence text, float x, float y, int argb, boolean shadow) {
        return text(text, 0, text.length(), x, y, argb, shadow);
    }

    /** Default font, unstyled; returns the x after the last advance. Shadow as vanilla: +1, rgb/4. */
    public float text(CharSequence text, int start, int end, float x, float y, int argb, boolean shadow) {
        var set = font.fontSet();
        boolean fishy = font.filterFishy();
        if (shadow) {
            runChars(set, fishy, text, start, end, x, y, dim(argb), true);
        }
        return runChars(set, fishy, text, start, end, x, y, argb, false);
    }

    /** Honours per-character style colour (rgb only), bold and italic. */
    public float text(FormattedCharSequence text, float x, float y, int argb, boolean shadow) {
        return text(text, x, y, argb, shadow, true);
    }

    /** {@code styleColours = false}: every glyph in {@code argb} (outlines, silhouettes). */
    public float text(FormattedCharSequence text, float x, float y, int argb, boolean shadow, boolean styleColours) {
        if (shadow) {
            textSink.reset(x, y, argb, true, styleColours);
            text.accept(textSink);
        }
        textSink.reset(x, y, argb, false, styleColours);
        text.accept(textSink);
        return textSink.x;
    }

    /** Text centred on {@code cx}. */
    public float textCentred(CharSequence text, float cx, float y, int argb, boolean shadow) {
        return text(text, cx - width(text) * 0.5f, y, argb, shadow);
    }

    /** Text ending at {@code right}. */
    public float textRight(CharSequence text, float right, float y, int argb, boolean shadow) {
        return text(text, right - width(text), y, argb, shadow);
    }

    private float runChars(
            //? if >=26.2 {
            /*net.minecraft.client.gui.GlyphSource set,
            *///?} else {
            FontSet set,
            //?}
            boolean fishy, CharSequence text, int start, int end, float x, float y,
                           int argb, boolean shadowPass) {
        for (int i = start; i < end; ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            CanvasFont.Glyph glyph = font.glyph(set, fishy, cp);
            float offset = shadowPass ? glyph.shadowOffset : 0;
            glyph(glyph, false, false, x + offset, y + offset, argb);
            x += glyph.advance;
        }
        return x;
    }

    private static int dim(int argb) {
        return argb & 0xFF000000 | (argb >>> 16 & 0xFF) / 4 << 16 | (argb >>> 8 & 0xFF) / 4 << 8 | (argb & 0xFF) / 4;
    }

    private void glyph(CanvasFont.Glyph g, boolean bold, boolean italic, float x, float y, int argb) {
        if (g.empty) {
            return;
        }
        int mode = (g.texture & CanvasFont.INTENSITY_FLAG) != 0 ? INTENSITY : TEXTURE;
        int id = g.texture & ~CanvasFont.INTENSITY_FLAG;
        float skewTop = italic ? 1.0f - 0.25f * g.up : 0;
        float skewBottom = italic ? 1.0f - 0.25f * g.down : 0;
        for (int pass = 0; pass < (bold ? 2 : 1); pass++) {
            float px = x + pass * g.boldOffset;
            float x0 = px + g.left;
            float x1 = px + g.right;
            float y0 = y + g.up;
            float y1 = y + g.down;
            int slot = begin(mode, id);
            vertex(x0 + skewTop, y0, g.u0, g.v0, argb, 1, 0, 0, 0, mode, slot);
            vertex(x0 + skewBottom, y1, g.u0, g.v1, argb, 1, 0, 0, 0, mode, slot);
            vertex(x1 + skewBottom, y1, g.u1, g.v1, argb, 1, 0, 0, 0, mode, slot);
            vertex(x1 + skewTop, y0, g.u1, g.v0, argb, 1, 0, 0, 0, mode, slot);
        }
    }

    private final class TextSink implements FormattedCharSink {
        float x;
        float y;
        int argb;
        boolean shadow;

        boolean styleColours;

        void reset(float x, float y, int argb, boolean shadow, boolean styleColours) {
            this.x = x;
            this.y = y;
            this.argb = argb;
            this.shadow = shadow;
            this.styleColours = styleColours;
        }

        @Override
        public boolean accept(int position, Style style, int cp) {
            var set = font.fontSet(style.getFont());
            boolean fishy = font.filterFishy();
            CanvasFont.Glyph glyph = font.glyph(set, fishy, cp);
            boolean bold = style.isBold();
            int colour = argb;
            TextColor styled = styleColours ? style.getColor() : null;
            if (styled != null) {
                colour = argb & 0xFF000000 | styled.getValue() & 0xFFFFFF;
            }
            if (shadow) {
                colour = dim(colour);
            }
            float offset = shadow ? glyph.shadowOffset : 0;
            glyph(glyph, bold, style.isItalic(), x + offset, y + offset, colour);
            x += bold ? glyph.advanceBold : glyph.advance;
            return true;
        }
    }

    // ---------------------------------------------------------------- arena

    /** Opens a quad; returns the texture slot (0 for untextured modes). */
    private int begin(int mode, int glTexture) {
        if (!active || suspended) {
            throw new IllegalStateException(active ? "Canvas suspended" : "Canvas not active");
        }
        if (vertexCount + 4 > vertexCapacity) {
            int capacity = Math.max(1024, vertexCapacity * 2);
            vertices = vertices == 0 ? MemoryUtil.nmemAlloc((long) capacity * CanvasGl.STRIDE)
                    : MemoryUtil.nmemRealloc(vertices, (long) capacity * CanvasGl.STRIDE);
            if (vertices == 0) {
                throw new OutOfMemoryError("Canvas vertex arena");
            }
            vertexCapacity = capacity;
        }
        if (batchCount == 0 || clipDirty && batchFirst[batchCount - 1] != vertexCount) {
            newBatch();
        } else if (clipDirty) {
            writeClip(batchCount - 1);
        }
        clipDirty = false;
        if (mode != TEXTURE && mode != INTENSITY) {
            return 0;
        }
        int batch = batchCount - 1;
        int at = batch * CanvasGl.SLOTS;
        int count = batchTextureCount[batch];
        for (int i = 0; i < count; i++) {
            if (batchTextures[at + i] == glTexture) {
                return i;
            }
        }
        if (count == CanvasGl.SLOTS) {
            newBatch();
            batch = batchCount - 1;
            at = batch * CanvasGl.SLOTS;
            count = 0;
        }
        batchTextures[at + count] = glTexture;
        batchTextureCount[batch] = count + 1;
        return count;
    }

    private void newBatch() {
        if (batchCount == batchFirst.length) {
            int capacity = batchCount * 2;
            batchFirst = Arrays.copyOf(batchFirst, capacity);
            batchTextureCount = Arrays.copyOf(batchTextureCount, capacity);
            batchTextures = Arrays.copyOf(batchTextures, capacity * CanvasGl.SLOTS);
            batchClip = Arrays.copyOf(batchClip, capacity * 5);
        }
        batchFirst[batchCount] = vertexCount;
        batchTextureCount[batchCount] = 0;
        writeClip(batchCount);
        batchCount++;
    }

    private void writeClip(int batch) {
        System.arraycopy(clips, clipDepth * 5, batchClip, batch * 5, 5);
    }

    private void vertex(float x, float y, float u, float v, int argb, float p0, float p1, float p2, float p3,
                        int mode, int slot) {
        long at = vertices + (long) vertexCount * CanvasGl.STRIDE;
        MemoryUtil.memPutFloat(at, a * x + c * y + e);
        MemoryUtil.memPutFloat(at + 4, b * x + d * y + f);
        MemoryUtil.memPutFloat(at + 8, u);
        MemoryUtil.memPutFloat(at + 12, v);
        MemoryUtil.memPutByte(at + 16, (byte) (argb >>> 16));
        MemoryUtil.memPutByte(at + 17, (byte) (argb >>> 8));
        MemoryUtil.memPutByte(at + 18, (byte) argb);
        MemoryUtil.memPutByte(at + 19, (byte) (argb >>> 24));
        MemoryUtil.memPutFloat(at + 20, p0);
        MemoryUtil.memPutFloat(at + 24, p1);
        MemoryUtil.memPutFloat(at + 28, p2);
        MemoryUtil.memPutFloat(at + 32, p3);
        MemoryUtil.memPutByte(at + 36, (byte) mode);
        MemoryUtil.memPutByte(at + 37, (byte) slot);
        vertexCount++;
    }

    /** Draws everything recorded so far. Transform and clip stacks are kept. */
    public void flush() {
        requireActive();
        if (suspended) {
            throw new IllegalStateException("Canvas suspended");
        }
        if (vertexCount == 0) {
            batchCount = 0;
            return;
        }
        if (!gl.ensure()) {
            vertexCount = 0;
            batchCount = 0;
            return;
        }
        int maxQuads = 0;
        for (int i = 0; i < batchCount; i++) {
            int end = i + 1 < batchCount ? batchFirst[i + 1] : vertexCount;
            maxQuads = Math.max(maxQuads, (end - batchFirst[i]) / 4);
        }
        int baseVertex = gl.bind(base, vertices, vertexCount, maxQuads);
        for (int i = 0; i < batchCount; i++) {
            int first = batchFirst[i];
            int end = i + 1 < batchCount ? batchFirst[i + 1] : vertexCount;
            if (end == first) {
                continue;
            }
            int clip = i * 5;
            gl.scissor(batchClip[clip] != 0, batchClip[clip + 1], batchClip[clip + 2],
                    batchClip[clip + 3] - batchClip[clip + 1], batchClip[clip + 4] - batchClip[clip + 2]);
            gl.textures(batchTextures, i * CanvasGl.SLOTS, batchTextureCount[i]);
            gl.draw(baseVertex + first, (end - first) / 4);
        }
        vertexCount = 0;
        batchCount = 0;
        clipDirty = true;
    }


    /** Vertices recorded since the last flush; for tests and cost counters. */
    public int pendingVertices() {
        return vertexCount;
    }

    public int pendingBatches() {
        return batchCount;
    }
}
