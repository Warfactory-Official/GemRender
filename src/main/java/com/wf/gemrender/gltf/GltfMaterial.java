package com.wf.gemrender.gltf;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.texture.SurfaceBake;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.CutoutShader;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import net.minecraft.resources.ResourceLocation;

public record GltfMaterial(@Nullable ResourceLocation texture, AlphaMode alphaMode, float alphaCutoff,
		boolean doubleSided, boolean pbr) {
	public GltfMaterial(@Nullable ResourceLocation texture, AlphaMode alphaMode, float alphaCutoff,
			boolean doubleSided) {
		this(texture, alphaMode, alphaCutoff, doubleSided, false);
	}

	public GltfMaterial {
		alphaCutoff = alphaMode == AlphaMode.MASK ? quantiseCutoff(alphaCutoff) : 0.0f;
	}

	private static final boolean OIT = !"false".equalsIgnoreCase(System.getProperty("gemrender.oit"));

	public enum AlphaMode {
		OPAQUE,

		MASK,

		BLEND
	}

	public static final ResourceLocation UNTEXTURED =
			ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "untextured");

	public GltfMaterial onTexture(ResourceLocation replacement, boolean banded) {
		return new GltfMaterial(replacement, alphaMode, alphaCutoff, doubleSided, pbr && banded);
	}

	public boolean orderIndependent() {
		return OIT && alphaMode == AlphaMode.BLEND;
	}

	public Material toFlywheel() {
		SimpleMaterial.Builder builder = SimpleMaterial.builder();
		if (!UNTEXTURED.equals(texture) && texture != null) {
			builder.texture(texture);
		}

		if (pbr) {
			builder.shaders(PBR_SHADERS);

			builder.cardinalLightingMode(CardinalLightingMode.OFF);
		}

		builder.mipmap(false);

		builder.backfaceCulling(!doubleSided);

		switch (alphaMode) {
			case OPAQUE -> {
				builder.transparency(Transparency.OPAQUE);
				builder.cutout(CutoutShaders.OFF);
			}
			case MASK -> {
				builder.transparency(Transparency.OPAQUE);
				builder.cutout(cutout());
			}
			case BLEND -> {
				if (orderIndependent()) {
					builder.transparency(Transparency.ORDER_INDEPENDENT);
				} else {
					builder.transparency(Transparency.TRANSLUCENT);
					builder.writeMask(WriteMask.COLOR);
				}

				builder.cutout(CutoutShaders.OFF);
			}
		}

		return builder.build();
	}

	@Nullable
	public static Material depthPassFor(Material colour) {
		if (colour.transparency() != Transparency.TRANSLUCENT || colour.writeMask()
				.depth()) {
			return null;
		}

		return SimpleMaterial.builderOf(colour)
				.writeMask(WriteMask.DEPTH)
				.cutout(CutoutShaders.EPSILON)
				.build();
	}

	private static final MaterialShaders PBR_SHADERS = new SimpleMaterialShaders(
			ResourceLocation.fromNamespaceAndPath("flywheel", "material/default.vert"),
			ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "material/pbr.frag"));

	public static final float[] CUTOUTS = { 0.0f, 0.1f, 0.5f };

	public static float quantiseCutoff(float cutoff) {
		float chosen = CUTOUTS[0];
		for (float candidate : CUTOUTS) {
			if (Math.abs(cutoff - candidate) < Math.abs(cutoff - chosen)) {
				chosen = candidate;
			}
		}

		if (Math.abs(cutoff - chosen) > 0.05f) {
			GemRender.LOGGER.warn("glTF alphaCutoff {} has no Flywheel cutout shader; using {} instead. "
					+ "Fragments between the two thresholds will render as though masked at {}.",
					cutoff, chosen, chosen);
		}
		return chosen;
	}

	private CutoutShader cutout() {
		if (alphaCutoff >= 0.5f) {
			return CutoutShaders.HALF;
		}
		return alphaCutoff >= 0.1f ? CutoutShaders.ONE_TENTH : CutoutShaders.EPSILON;
	}

	public String describe() {
		return switch (alphaMode) {
			case OPAQUE -> "opaque";
			case MASK -> "mask@" + alphaCutoff;

			case BLEND -> orderIndependent() ? "blend/oit" : "blend/sorted+depth";
		} + (doubleSided ? " two-sided" : "") + (pbr ? " pbr" : "");
	}
}
