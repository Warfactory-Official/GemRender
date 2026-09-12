package com.wf.gemrender.gltf;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.texture.VariantUv;
import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

/**
 * An imported asset: one Flywheel {@link Model}, plus its palette layout, bounds, morphs and clips.
 */
public record GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
                                 GltfMorphLayout morphs, Map<String, GltfAnimation> animations,
                                 @Nullable ResourceLocation atlas,
                                 List<ResourceLocation> textures, List<VariantUv> variants) {
    public GemRenderGltfModel {
        textures = List.copyOf(textures);
        variants = variants.isEmpty() ? List.of(VariantUv.NONE) : List.copyOf(variants);
    }

    /**
     * A model with the one variant its own textures give it.
     */
    public GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
                              GltfMorphLayout morphs, Map<String, GltfAnimation> animations,
                              @Nullable ResourceLocation atlas, List<ResourceLocation> textures) {
        this(model, layout, bounds, morphs, animations, atlas, textures, List.of(VariantUv.NONE));
    }

    /**
     * The texture-coordinate offset for one of this model's variants, to put on an instance.
     *
     * <p>Index 0 is the model as its own file describes it and is what an asset with no declared
     * variants has. An index past the end is clamped rather than thrown: a variant is content, the
     * count can change when a pack is swapped, and a mob briefly wearing skin 0 is a better failure
     * than a crash in a visual.
     */
    public VariantUv variant(int index) {
        return variants.get(Mth.clamp(index, 0, variants.size() - 1));
    }

    public int variantCount() {
        return variants.size();
    }

    public int jointCount() {
        return layout.size();
    }

    public Matrix4f[] newPalette() {
        Matrix4f[] palette = new Matrix4f[jointCount()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Matrix4f();
        }
        return palette;
    }

    public GltfAnimation animation(String name) {
        return animations.get(name);
    }

    public GltfAnimation animationOrAny(String name) {
        GltfAnimation named = animations.get(name);
        if (named != null) {
            return named;
        }
        return animations.isEmpty() ? null : animations.values()
                .iterator()
                .next();
    }
}
