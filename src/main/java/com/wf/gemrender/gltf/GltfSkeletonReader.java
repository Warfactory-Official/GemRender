package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.jgltf.GltfResourceHook;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.ChannelBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link GltfSkeleton} from glTF / glb bytes; both sides. */
public final class GltfSkeletonReader {
    private GltfSkeletonReader() {
    }

    /** {@code buffers}: {@code extras.resourceLocation} buffers ({@link GltfResourceHook}). */
    public static GltfSkeleton read(InputStream in, GltfResourceHook.Resolver buffers) throws IOException {
        GltfModel gltf = GltfResourceHook.resolving(buffers, () -> new GltfModelReader().readWithoutReferences(in));
        GltfPaletteLayout layout = GltfPaletteLayout.of(gltf);
        return new GltfSkeleton(layout, animations(gltf, layout.nodeTable()));
    }

    /** Animations by name ({@code animation<i>} when unnamed); channel-less ones = empty clips (named in the file). */
    static Map<String, GltfAnimation> animations(GltfModel gltf, NodeTable nodes) {
        Map<String, GltfAnimation> animations = new LinkedHashMap<>();
        for (AnimationModel animationModel : gltf.getAnimationModels()) {
            List<ChannelBinding> bindings = GltfAnimationCreator.createGltfAnimation(animationModel);
            String name = animationModel.getName();
            if (name == null || name.isBlank()) {
                name = "animation" + animations.size();
            }
            animations.put(name, GltfAnimation.of(name, bindings, nodes));
        }
        return animations;
    }
}
