package com.wf.gemrender.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.TextureUnits;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.glTexImage3D;
import static org.lwjgl.opengl.GL12C.glTexSubImage3D;
import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

/**
 * Every registered paint as one layer of a {@code sampler2DArray} ({@code _gemrender_paint}), shared by all
 * models: memory = paints + models, never paints x models, and painted copies of a model stay one batch.
 *
 * <p>Register from any thread; the array is rebuilt on the render thread at the next {@link #bind}.
 */
public final class PaintArray {
    public static final int TEXTURE_UNIT = TextureUnits.PAINT;

    /**
     * Layer edge, texels. Sources of another size are resampled nearest.
     */
    public static final int SIZE = Integer.getInteger("gemrender.paintsize", 512);

    private static final float ANISOTROPY = 8.0f;

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final Map<Source, Integer> LAYERS = new HashMap<>();

    private static boolean dirty;
    private static int textureId;
    private static Boolean supported;

    private PaintArray() {
    }

    /**
     * A tiling texture, one repeat per {@code blocksPerTile} blocks.
     */
    public static Paint texture(ResourceLocation texture, float blocksPerTile) {
        return new Paint(layer(new Source(texture, 0)), 1.0f / blocksPerTile);
    }

    /**
     * One colour, {@code 0xRRGGBB}.
     */
    public static Paint solid(int rgb) {
        return new Paint(layer(new Source(null, rgb & 0xFFFFFF)), 1.0f);
    }

    /**
     * Forgets every paint; layers handed out before are invalid. For a resource reload.
     */
    public static synchronized void clear() {
        SOURCES.clear();
        LAYERS.clear();
        dirty = true;
    }

    private static synchronized int layer(Source source) {
        return LAYERS.computeIfAbsent(source, key -> {
            SOURCES.add(key);
            dirty = true;
            return SOURCES.size() - 1;
        });
    }

    public static void bind() {
        RenderSystem.assertOnRenderThread();
        if (supported == null) {
            int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
            supported = units > TEXTURE_UNIT;
            if (!supported) {
                GemRender.LOGGER.warn("Paint disabled: it needs texture unit {} and this driver has {}",
                        TEXTURE_UNIT, units);
            }
        }
        if (!supported) {
            return;
        }

        List<Source> build = null;
        synchronized (PaintArray.class) {
            if (dirty) {
                dirty = false;
                build = List.copyOf(SOURCES);
            }
        }
        if (build != null) {
            rebuild(build);
        }
        if (textureId == 0) {
            return;
        }

        int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
        try {
            glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        } finally {
            TextureUnits.restore(previousUnit);
        }
    }

    private static void rebuild(List<Source> sources) {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        if (sources.isEmpty()) {
            return;
        }

        long start = System.nanoTime();
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
        try {
            textureId = glGenTextures();
            GlState.resetUnpack();
            glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
            glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, SIZE, SIZE, sources.size(), 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, (ByteBuffer) null);
            for (int layer = 0; layer < sources.size(); layer++) {
                fill(sources.get(layer), pixels);
                glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, SIZE, SIZE, 1, GL_RGBA, GL_UNSIGNED_BYTE,
                        pixels);
            }
            glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                glTexParameterf(GL_TEXTURE_2D_ARRAY, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        ANISOTROPY);
            }
        } finally {
            TextureUnits.restore(previousUnit);
            MemoryUtil.memFree(pixels);
        }
        GemRender.LOGGER.info("Paint array: {} layers of {}^2 on texture unit {} in {}ms", sources.size(), SIZE,
                TEXTURE_UNIT, (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * RGBA bytes; missing texture => magenta.
     */
    private static void fill(Source source, ByteBuffer out) {
        out.clear();
        NativeImage image = source.texture() == null ? null : ModelTextures.read(source.texture());
        try {
            int solid = source.texture() == null ? source.rgb() : 0xFF00FF;
            int abgr = 0xFF000000 | (solid & 0xFF) << 16 | (solid & 0xFF00) | (solid >> 16 & 0xFF);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    out.putInt(image == null ? abgr
                            : Pixels.get(image, x * image.getWidth() / SIZE, y * image.getHeight() / SIZE));
                }
            }
        } finally {
            if (image != null) {
                image.close();
            }
        }
        out.flip();
    }

    private record Source(@Nullable ResourceLocation texture, int rgb) {
    }
}
