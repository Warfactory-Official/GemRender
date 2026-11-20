package com.wf.gemrender.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wf.gemrender.iris.ShaderPacks;
import com.wf.gemrender.mixin.ui.CompositeRenderTypeAccessor;
import com.wf.gemrender.mixin.ui.CompositeStateAccessor;
import com.wf.gemrender.mixin.ui.TextureShardAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Opt-in sharp-bilinear vanilla {@code Font} output. Active inside {@link #begin}/{@link #end} or while a
 * {@link SharpTextScreen} renders; vanilla text elsewhere is untouched. Inert under an Iris pack.
 */
public final class SharpText {
    private static final ResourceLocation COLOR = ResourceLocation.fromNamespaceAndPath("gemrender", "sharp_text");
    private static final ResourceLocation INTENSITY = ResourceLocation.fromNamespaceAndPath("gemrender", "sharp_text_intensity");

    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
    private static final RenderStateShard.ShaderStateShard COLOR_SHADER = new RenderStateShard.ShaderStateShard(() -> shader(false));
    private static final RenderStateShard.ShaderStateShard INTENSITY_SHADER = new RenderStateShard.ShaderStateShard(() -> shader(true));

    private static ShaderInstance colorInstance;
    private static ShaderInstance intensityInstance;
    private static int depth;
    private static boolean screen;

    private SharpText() {
    }

    public static void begin() {
        depth++;
    }

    public static void end() {
        if (depth == 0) {
            throw new IllegalStateException("SharpText.end without begin");
        }
        depth--;
    }

    public static boolean active() {
        return (depth > 0 || screen) && !ShaderPacks.inUse();
    }

    public static void screen(boolean on) {
        screen = on;
    }

    /** Resource reload: instances and the types holding them are rebuilt on next use. */
    public static void invalidate() {
        TYPES.clear();
        colorInstance = close(colorInstance);
        intensityInstance = close(intensityInstance);
    }

    private static ShaderInstance close(ShaderInstance instance) {
        if (instance != null) {
            instance.close();
        }
        return null;
    }

    private static ShaderInstance shader(boolean intensity) {
        try {
            if (intensity) {
                if (intensityInstance == null) {
                    intensityInstance = new ShaderInstance(Minecraft.getInstance().getResourceManager(), INTENSITY,
                            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
                }
                return intensityInstance;
            }
            if (colorInstance == null) {
                colorInstance = new ShaderInstance(Minecraft.getInstance().getResourceManager(), COLOR,
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            }
            return colorInstance;
        } catch (IOException e) {
            throw new IllegalStateException("gemrender sharp text shader", e);
        }
    }

    /** {@code original} unless it is a plain text page in NORMAL mode. */
    public static RenderType select(RenderType original, Font.DisplayMode mode) {
        if (mode != Font.DisplayMode.NORMAL || !active()) {
            return original;
        }
        RenderType swapped = TYPES.get(original);
        if (swapped == null) {
            swapped = build(original);
            TYPES.put(original, swapped);
        }
        return swapped;
    }

    private static RenderType build(RenderType original) {
        if (!(original instanceof CompositeRenderTypeAccessor composite)) {
            return original;
        }
        var state = (CompositeStateAccessor) (Object) composite.gemrender$state();
        var page = ((TextureShardAccessor) state.gemrender$textureState()).gemrender$cutoutTexture();
        String name = original.toString();
        int start = name.startsWith("RenderType[") ? "RenderType[".length() : 0;
        int end = name.indexOf(':', start);
        String family = name.substring(start, end < 0 ? name.length() : end);
        boolean intensity = family.equals("text_intensity") || family.equals("neoforge_text_intensity");
        if (page.isEmpty() || !intensity && !family.equals("text") && !family.equals("neoforge_text")) {
            return original;
        }
        return RenderType.create(
                intensity ? "gemrender_sharp_text_intensity" : "gemrender_sharp_text",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                786432,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(intensity ? INTENSITY_SHADER : COLOR_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(page.get(), false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false));
    }
}
