package com.wf.gemrender.ui;

//? if >=26.1 {
/*import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;

final class CanvasPass implements PictureInPictureRenderState {
    private static final ArrayDeque<ByteBuffer> POOL = new ArrayDeque<>();

    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;
    private final ScreenRectangle bounds;
    private ByteBuffer data;
    final int vertexCount;
    final int maxQuads;
    final int batchCount;
    final int[] batchFirst;
    final int[] batchTextures;
    final int[] batchTextureCount;
    final int[] batchClip;
    final int pixelWidth;
    final int pixelHeight;
    final float originX;
    final float originY;
    private final Matrix4f base = new Matrix4f();
    private final int framebufferWidth;
    private final int framebufferHeight;

    private CanvasPass(int x0, int y0, int x1, int y1, ByteBuffer data, int vertexCount, int maxQuads, int batchCount,
                       int[] batchFirst, int[] batchTextures, int[] batchTextureCount, int[] batchClip, Matrix4f base,
                       int framebufferWidth, int framebufferHeight, int guiWidth, int guiHeight) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        this.data = data;
        this.vertexCount = vertexCount;
        this.maxQuads = maxQuads;
        this.batchCount = batchCount;
        this.batchFirst = batchFirst;
        this.batchTextures = batchTextures;
        this.batchTextureCount = batchTextureCount;
        this.batchClip = batchClip;
        this.base.set(base);
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        float scaleX = framebufferWidth / (float) guiWidth;
        float scaleY = framebufferHeight / (float) guiHeight;
        this.pixelWidth = Math.max(1, Math.round((x1 - x0) * scaleX));
        this.pixelHeight = Math.max(1, Math.round((y1 - y0) * scaleY));
        this.originX = x0 * scaleX;
        this.originY = framebufferHeight - y1 * scaleY;
    }

    static @Nullable CanvasPass capture(long vertices, int count, Matrix4f base, int framebufferWidth,
                                        int framebufferHeight, int guiWidth, int guiHeight, int[] batchFirst,
                                        int[] batchTextures, int[] batchTextureCount, int[] batchClip, int batchCount) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            long at = vertices + (long) i * CanvasGl.STRIDE;
            float x = MemoryUtil.memGetFloat(at);
            float y = MemoryUtil.memGetFloat(at + 4);
            float w = base.m03() * x + base.m13() * y + base.m33();
            float px = ((base.m00() * x + base.m10() * y + base.m30()) / w * 0.5f + 0.5f) * framebufferWidth;
            float py = ((base.m01() * x + base.m11() * y + base.m31()) / w * 0.5f + 0.5f) * framebufferHeight;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        int gx0 = Math.max(0, (int) Math.floor((minX - 1) * guiWidth / framebufferWidth));
        int gx1 = Math.min(guiWidth, (int) Math.ceil((maxX + 1) * guiWidth / framebufferWidth));
        int gy0 = Math.max(0, (int) Math.floor((framebufferHeight - maxY - 1) * guiHeight / framebufferHeight));
        int gy1 = Math.min(guiHeight, (int) Math.ceil((framebufferHeight - minY + 1) * guiHeight / framebufferHeight));
        if (gx1 <= gx0 || gy1 <= gy0) {
            return null;
        }
        int bytes = count * CanvasGl.STRIDE;
        ByteBuffer data = POOL.pollFirst();
        if (data == null || data.capacity() < bytes) {
            data = ByteBuffer.allocateDirect(Integer.highestOneBit(Math.max(bytes, 1024) - 1) << 1);
        }
        MemoryUtil.memCopy(vertices, MemoryUtil.memAddress(data), bytes);
        int maxQuads = 0;
        for (int i = 0; i < batchCount; i++) {
            int end = i + 1 < batchCount ? batchFirst[i + 1] : count;
            maxQuads = Math.max(maxQuads, (end - batchFirst[i]) / 4);
        }
        return new CanvasPass(gx0, gy0, gx1, gy1, data, count, maxQuads, batchCount,
                Arrays.copyOf(batchFirst, batchCount), Arrays.copyOf(batchTextures, batchCount * CanvasGl.SLOTS),
                Arrays.copyOf(batchTextureCount, batchCount), Arrays.copyOf(batchClip, batchCount * 5), base,
                framebufferWidth, framebufferHeight, guiWidth, guiHeight);
    }

    long address() {
        return MemoryUtil.memAddress(data);
    }

    Matrix4f textureMatrix(int width, int height, Matrix4f out) {
        out.identity();
        out.m00(framebufferWidth / (float) width).m30((framebufferWidth - 2 * originX) / width - 1);
        out.m11(framebufferHeight / (float) height).m31((framebufferHeight - 2 * originY) / height - 1);
        return out.mul(base);
    }

    void recycle() {
        if (data != null && POOL.size() < 16) {
            POOL.addFirst(data);
        }
        data = null;
    }

    @Override
    public int x0() {
        return x0;
    }

    @Override
    public int x1() {
        return x1;
    }

    @Override
    public int y0() {
        return y0;
    }

    @Override
    public int y1() {
        return y1;
    }

    @Override
    public float scale() {
        return 1.0f;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
*///?}
