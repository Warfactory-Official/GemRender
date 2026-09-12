package com.wf.gemrender.rig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.resources.ResourceLocation;

@Tag("bootstrap")
class RigSkinsTest {
	private static final ResourceLocation ATLAS =
			ResourceLocation.fromNamespaceAndPath("gemrender", "atlas/test/crab");
	private static final ResourceLocation RED =
			ResourceLocation.fromNamespaceAndPath("gemrender", "textures/red.png");
	private static final ResourceLocation BLUE =
			ResourceLocation.fromNamespaceAndPath("gemrender", "textures/blue.png");

	private static final Material MATERIAL = SimpleMaterial.builder()
			.texture(RED)
			.build();

	private static RigGeometry triangle() {
		return new RigGeometry(
				new float[] { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f },
				null,
				new float[] { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f },
				new int[] { 0, 1, 2 });
	}

	private static RigBuilder rig() {
		RigBuilder rig = new RigBuilder("crab");
		rig.bone("body", RigBuilder.ROOT, 0.0f, 0.0f, 0.0f);
		return rig;
	}

	@Test
	void oneSkinIsNotASheet() {
		RigBuilder rig = rig();
		rig.skins(ATLAS, List.of(RED))
				.attach(0, triangle());

		GemRenderGltfModel model = rig.build(MATERIAL, Map.of());

		assertThat(model.variantCount()).isEqualTo(1);
		assertThat(model.variant(0)).isEqualTo(VariantUv.NONE);
		assertThat(model.atlas()).isNull();
	}

	@Test
	void noSkinsIsTheSameModelAsBefore() {
		RigBuilder rig = rig();
		rig.attach(0, triangle());

		GemRenderGltfModel model = rig.build(MATERIAL, Map.of());

		assertThat(model.variantCount()).isEqualTo(1);
		assertThat(model.textures()).isEmpty();
	}

	@Test
	void skinsRefuseAMeshWithItsOwnMaterial() {
		RigBuilder rig = rig();
		rig.skins(ATLAS, List.of(RED, BLUE))
				.attach(0, triangle(), SimpleMaterial.builder()
						.texture(BLUE)
						.build());

		assertThatThrownBy(() -> rig.build(MATERIAL, Map.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("crab")
				.hasMessageContaining("2 skins");
	}
}
