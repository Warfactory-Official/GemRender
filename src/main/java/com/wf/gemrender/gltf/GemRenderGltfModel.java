package com.wf.gemrender.gltf;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.skin.SkinnedBounds;

import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.resources.ResourceLocation;

/** An imported asset: one Flywheel {@link Model}, plus its palette layout, bounds, morphs and clips. */
public record GemRenderGltfModel(Model model, GltfPaletteLayout layout, SkinnedBounds bounds,
		GltfMorphLayout morphs, Map<String, GltfAnimation> animations, @Nullable ResourceLocation atlas,
		List<ResourceLocation> textures) {
	public GemRenderGltfModel {
		textures = List.copyOf(textures);
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
