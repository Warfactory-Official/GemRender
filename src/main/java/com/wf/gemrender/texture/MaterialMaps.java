package com.wf.gemrender.texture;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

public record MaterialMaps(@Nullable ResourceLocation baseColor, @Nullable ResourceLocation normal,
		@Nullable ResourceLocation metallicRoughness, @Nullable ResourceLocation occlusion,
		@Nullable ResourceLocation emissive, float baseColorR, float baseColorG, float baseColorB,
		float baseColorA, float metallicFactor, float roughnessFactor, float normalScale,
		float occlusionStrength, float emissiveR, float emissiveG, float emissiveB) {
	public static MaterialMaps plain(@Nullable ResourceLocation baseColor) {
		return new MaterialMaps(baseColor, null, null, null, null, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
				1.0f, 1.0f, 0.0f, 0.0f, 0.0f);
	}

	public boolean pbr() {
		return normal != null || metallicRoughness != null || emissive != null || emissiveR > 0.0f
				|| emissiveG > 0.0f || emissiveB > 0.0f;
	}

	public MaterialMaps withoutMaps() {
		return new MaterialMaps(baseColor, null, null, null, null, baseColorR, baseColorG, baseColorB,
				baseColorA, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);
	}

	public boolean baseColorUnmodified() {
		return occlusion == null && baseColorR == 1.0f && baseColorG == 1.0f && baseColorB == 1.0f
				&& baseColorA == 1.0f;
	}

	public String describe() {
		StringBuilder out = new StringBuilder();
		if (normal != null) {
			out.append(" +normal");
		}
		if (metallicRoughness != null) {
			out.append(" +metallicRoughness");
		}
		if (occlusion != null) {
			out.append(" +occlusion");
		}
		if (emissive != null) {
			out.append(" +emissive");
		} else if (emissiveR > 0.0f || emissiveG > 0.0f || emissiveB > 0.0f) {
			out.append(" +emissiveFactor");
		}
		if (!baseColorUnmodified()) {
			out.append(" +baked");
		}
		return out.toString();
	}
}
