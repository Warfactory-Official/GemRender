package com.wf.gemrender.light;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.texture.Pixels;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12C.glTexImage3D;
import static org.lwjgl.opengl.GL12C.glTexSubImage3D;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

public final class LightCookies {
    public static final int UNIT = TextureUnits.LIGHT_COOKIES;

    public static final int SIZE = 256;

    private static final List<ResourceLocation> SOURCES = new ArrayList<>();
    private static final Map<ResourceLocation, LightCookie> COOKIES = new HashMap<>();

    private static boolean dirty;
    private static int textureId;

    private LightCookies() {
    }

    static synchronized LightCookie cookie(ResourceLocation texture) {
        return COOKIES.computeIfAbsent(texture, key -> {
            SOURCES.add(key);
            dirty = true;
            return new LightCookie(key, SOURCES.size() - 1);
        });
    }

    public static synchronized void reload() {
        dirty = true;
    }

    static void bind() {
        List<ResourceLocation> build = null;
        synchronized (LightCookies.class) {
            if (dirty) {
                dirty = false;
                build = List.copyOf(SOURCES);
            }
        }
        if (build != null) {
            rebuild(build);
        }

        int previousUnit = TextureUnits.activate(UNIT);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        TextureUnits.restore(previousUnit);
    }

    private static void rebuild(List<ResourceLocation> sources) {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        if (sources.isEmpty()) {
            return;
        }

        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        int previousUnit = TextureUnits.activate(UNIT);
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
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } finally {
            TextureUnits.restore(previousUnit);
            MemoryUtil.memFree(pixels);
        }
        GemRender.LOGGER.info("Light cookies: {} layers of {}^2 on texture unit {}", sources.size(), SIZE, UNIT);
    }

    /**
     * Missing texture => magenta.
     */
    private static void fill(ResourceLocation source, ByteBuffer out) {
        out.clear();
        NativeImage image = ModelTextures.read(source);
        try {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    out.putInt(image == null ? 0xFFFF00FF
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
}
