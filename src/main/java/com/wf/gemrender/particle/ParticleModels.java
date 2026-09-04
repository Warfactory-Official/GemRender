package com.wf.gemrender.particle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.CutoutShader;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

public final class ParticleModels {
	private static final Map<Key, Model> BILLBOARDS = new ConcurrentHashMap<>();

	private ParticleModels() {
	}

	public static Model additive(ResourceLocation texture) {
		return billboard(texture, Transparency.ADDITIVE);
	}

	public static Model translucent(ResourceLocation texture) {
		return billboard(texture, Transparency.ORDER_INDEPENDENT);
	}

	public static Model cutout(ResourceLocation texture) {
		return billboard(texture, Transparency.OPAQUE, CutoutShaders.ONE_TENTH);
	}

	public static Model billboard(ResourceLocation texture, Transparency transparency) {
		return billboard(texture, transparency, CutoutShaders.EPSILON);
	}

	public static Model billboard(ResourceLocation texture, Transparency transparency, CutoutShader cutout) {
		return BILLBOARDS.computeIfAbsent(new Key(texture, transparency, cutout),
				key -> new SingleMeshModel(ParticleQuad.INSTANCE,
						material(key.texture(), key.transparency(), key.cutout())));
	}

	public static Material material(ResourceLocation texture, Transparency transparency) {
		return material(texture, transparency, CutoutShaders.EPSILON);
	}

	public static Material material(ResourceLocation texture, Transparency transparency, CutoutShader cutout) {
		return SimpleMaterial.builder()
				.texture(texture)
				.transparency(transparency)
				.cutout(cutout)
				.backfaceCulling(false)
				.cardinalLightingMode(CardinalLightingMode.OFF)
				.mipmap(false)
				.build();
	}

	private record Key(ResourceLocation texture, Transparency transparency, CutoutShader cutout) {
	}
}
