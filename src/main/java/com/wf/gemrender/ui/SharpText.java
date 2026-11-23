package com.wf.gemrender.ui;

//? if >=26.1 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wf.gemrender.Ids;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fc;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/^**
 * Opt-in sharp-bilinear vanilla {@code Font} output. Active inside {@link #begin}/{@link #end} or while a
 * {@link SharpTextScreen} renders; vanilla text elsewhere is untouched.
 *
 * <p>26.1: text is judged at extraction (when the GUI element is created), drawn later by a pipeline
 * substituted for the vanilla text pipelines.
 *^/
public final class SharpText {
    private static final Set<Matrix3x2fc> MARKED = Collections.newSetFromMap(new IdentityHashMap<>());

    private static RenderPipeline color;
    private static RenderPipeline intensity;
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
        return depth > 0 || screen;
    }

    public static void screen(boolean on) {
        screen = on;
    }

    public static void mark(Matrix3x2fc pose) {
        MARKED.add(pose);
    }

    public static boolean marked(Matrix3x2fc pose) {
        return !MARKED.isEmpty() && MARKED.contains(pose);
    }

    public static void clearMarks() {
        MARKED.clear();
    }

    public static RenderPipeline pipeline(RenderPipeline original) {
        if (original == RenderPipelines.GUI_TEXT) {
            if (color == null) {
                color = build("gui_sharp_text");
            }
            return color;
        }
        if (original == RenderPipelines.GUI_TEXT_INTENSITY) {
            if (intensity == null) {
                intensity = build("gui_sharp_text_intensity");
            }
            return intensity;
        }
        return original;
    }

    private static RenderPipeline build(String name) {
        return RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
                .withLocation(Ids.of("pipeline/" + name))
                .withVertexShader("core/rendertype_text")
                .withFragmentShader(Ids.of("core/" + name))
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .build();
    }
}
*///?} else {
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.wf.gemrender.Ids;
//? if iris {
import com.wf.gemrender.iris.ShaderPacks;
//?}
import com.wf.gemrender.mixin.ui.RenderShards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Opt-in sharp-bilinear vanilla {@code Font} output. Active inside {@link #begin}/{@link #end} or while a
 * {@link SharpTextScreen} renders; vanilla text elsewhere is untouched. Inert under an Iris pack.
 */
public final class SharpText {
    private static final String PROGRAM_PREFIX = "gemrender_";
    private static final String COLOR = PROGRAM_PREFIX + "sharp_text";
    private static final String INTENSITY = PROGRAM_PREFIX + "sharp_text_intensity";

    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
    private static final Object COLOR_SHADER = SharpTypes.shader(() -> shader(false));
    private static final Object INTENSITY_SHADER = SharpTypes.shader(() -> shader(true));

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
        //? if iris {
        return (depth > 0 || screen) && !ShaderPacks.inUse();
        //?} else {
        /*return depth > 0 || screen;
        *///?}
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

    private static ResourceProvider programs() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        String root = "shaders/core/" + PROGRAM_PREFIX;
        return location -> manager.getResource(location.getNamespace().equals("minecraft") && location.getPath().startsWith(root)
                ? Ids.of("shaders/core/" + location.getPath().substring(root.length()))
                : location);
    }

    private static ShaderInstance shader(boolean intensity) {
        try {
            if (intensity) {
                if (intensityInstance == null) {
                    intensityInstance = new ShaderInstance(programs(), INTENSITY,
                            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
                }
                return intensityInstance;
            }
            if (colorInstance == null) {
                colorInstance = new ShaderInstance(programs(), COLOR,
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
        if (!(original instanceof RenderShards.CompositeRenderTypeAccessor composite)) {
            return original;
        }
        var state = (RenderShards.CompositeStateAccessor) (Object) composite.gemrender$state();
        var page = ((RenderShards.TextureShardAccessor) state.gemrender$textureState()).gemrender$cutoutTexture();
        String family = CanvasFont.family(original);
        boolean intensity = family.equals("text_intensity") || family.equals("neoforge_text_intensity")
                || family.equals("forge_text_intensity");
        if (page.isEmpty() || !intensity && !family.equals("text") && !family.equals("neoforge_text")
                && !family.equals("forge_text")) {
            return original;
        }
        return SharpTypes.build(intensity, intensity ? INTENSITY_SHADER : COLOR_SHADER, page.get());
    }
}
//?}
