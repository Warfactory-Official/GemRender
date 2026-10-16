package com.wf.gemrender.texture;

import com.wf.gemrender.Ids;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.gemrender.GemRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public final class ModelTextures {
    public static final String KTX2_SUFFIX = ".ktx2";

    private ModelTextures() {
    }

    public static boolean isKtx2(ResourceLocation source) {
        return source.getPath()
                .toLowerCase(Locale.ROOT)
                .endsWith(KTX2_SUFFIX);
    }

    @Nullable
    public static NativeImage read(ResourceLocation source) {
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .getResourceOrThrow(source)
                .open()) {
            return isKtx2(source) ? toNativeImage(KtxImage.read(in)) : NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            GemRender.LOGGER.warn("Could not read model texture {} ({})", source, e.toString());
            return null;
        }
    }

    /**
     * {@code a/b.png} + {@code _x} -> {@code a/b_x.png} when that resource exists, else null.
     */
    @Nullable
    public static ResourceLocation sibling(@Nullable ResourceLocation source, String suffix) {
        if (source == null) {
            return null;
        }
        String path = source.getPath();
        int dot = path.lastIndexOf('.');
        ResourceLocation candidate = source.withPath(dot < 0 ? path + suffix
                : path.substring(0, dot) + suffix + path.substring(dot));
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(candidate)
                .isPresent() ? candidate : null;
    }

    public static ResourceLocation materialTexture(ResourceLocation source,
                                                   List<ResourceLocation> owned) {
        if (!isKtx2(source)) {
            return source;
        }

        NativeImage image = read(source);
        if (image == null) {
            return source;
        }

        ResourceLocation id = decodedId(source);

        register(id, image);
        owned.add(id);

        GemRender.LOGGER.info("Decoded {} to a {}x{} texture registered as {}", source, image.getWidth(),
                image.getHeight(), id);
        return id;
    }

    public static void release(ResourceLocation id) {
        Minecraft.getInstance()
                .getTextureManager()
                .release(id);
    }

    static ResourceLocation decodedId(ResourceLocation source) {
        return Ids.of(GemRender.MOD_ID,
                "decoded/" + source.getNamespace() + "/" + source.getPath());
    }

    static NativeImage toNativeImage(KtxImage decoded) {
        NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);
        byte[] rgba = decoded.rgba();

        int i = 0;
        for (int y = 0; y < decoded.height(); y++) {
            for (int x = 0; x < decoded.width(); x++) {
                int r = rgba[i] & 0xFF;
                int g = rgba[i + 1] & 0xFF;
                int b = rgba[i + 2] & 0xFF;
                int a = rgba[i + 3] & 0xFF;
                i += KtxImage.BYTES_PER_PIXEL;
                Pixels.set(image, x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        return image;
    }

    private static void register(ResourceLocation id, NativeImage image) {
        Runnable work = () -> {
            //? if >=26.1 {
            /*DynamicTexture texture = new DynamicTexture(id::toString, image);
             *///?} else {
            DynamicTexture texture = new DynamicTexture(image);
            //?}
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, texture);
        };
        com.wf.gemrender.render.RenderThread.later(work);
    }
}
