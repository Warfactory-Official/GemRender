package com.wf.gemrender.volume;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleQuad;
import com.wf.gemrender.water.Absorbance;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

public final class VolumeModels {
	public static final MaterialShaders VOLUME_SHADERS = new SimpleMaterialShaders(
			ResourceLocation.fromNamespaceAndPath("flywheel", "material/default.vert"),
			ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, "material/volume.frag"));

	private static final ResourceLocation WHITE =
			ResourceLocation.withDefaultNamespace("textures/misc/white.png");

	private static final Object LOCK = new Object();

	private static Model cloud;

	static {
		Absorbance.getInstance()
				.register(VOLUME_SHADERS);
	}

	private VolumeModels() {
	}

	public static Model cloud() {
		synchronized (LOCK) {
			if (cloud == null) {
				cloud = new SingleMeshModel(ParticleQuad.INSTANCE, SimpleMaterial.builder()
						.texture(WHITE)
						.transparency(Transparency.ORDER_INDEPENDENT)
						.cutout(CutoutShaders.EPSILON)
						.shaders(VOLUME_SHADERS)
						.fog(FogShaders.NONE)
						.blur(false)
						.mipmap(false)
						.backfaceCulling(false)
						.useOverlay(false)
						.useLight(true)
						.cardinalLightingMode(CardinalLightingMode.OFF)
						.build());
			}
			return cloud;
		}
	}
}
