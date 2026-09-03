package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfMaterial.AlphaMode;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.MeshModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import com.wf.gemrender.vendor.jgltf.model.v2.MaterialModelV2;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.resources.ResourceLocation;

class GltfMaterialTest {
	private static final String ASSET = "assets/gemrender/models/glass/glass.glb";

	private static final int MATERIAL_GLASS = 0;
	private static final int MATERIAL_STEEL = 1;
	private static final int MATERIAL_GRILLE = 2;

	private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("gemrender", "a");
	private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("gemrender", "b");

	@Test
	void opaqueIsFlywheelsDefaultDraw() {
		Material material = new GltfMaterial(A, AlphaMode.OPAQUE, 0.5f, false).toFlywheel();

		assertThat(material.transparency()).isEqualTo(Transparency.OPAQUE);
		assertThat(material.cutout()).isEqualTo(CutoutShaders.OFF);
		assertThat(material.backfaceCulling()).isTrue();
	}

	@Test
	void blendGoesThroughOit() {
		Material material = new GltfMaterial(A, AlphaMode.BLEND, 0.5f, false).toFlywheel();

		assertThat(material.transparency()).isEqualTo(Transparency.ORDER_INDEPENDENT);
		assertThat(material.cutout()).isEqualTo(CutoutShaders.OFF);
		assertThat(material.writeMask()).isEqualTo(WriteMask.COLOR_DEPTH);
	}

	private static Material sortedBlend() {
		return SimpleMaterial.builder()
				.texture(A)
				.transparency(Transparency.TRANSLUCENT)
				.writeMask(WriteMask.COLOR)
				.cutout(CutoutShaders.OFF)
				.backfaceCulling(false)
				.build();
	}

	@Test
	void sortedBlendGetsADepthCompanion() {
		Material depth = GltfMaterial.depthPassFor(sortedBlend());

		assertThat(depth).isNotNull();
		assertThat(depth.writeMask()).isEqualTo(WriteMask.DEPTH);
		assertThat(depth.cutout()).isEqualTo(CutoutShaders.EPSILON);
	}

	@Test
	void theCompanionAgreesWithItsColourPassOnEverythingElse() {
		Material colour = sortedBlend();
		Material depth = GltfMaterial.depthPassFor(colour);

		assertThat(depth.backfaceCulling()).isEqualTo(colour.backfaceCulling());
		assertThat(depth.texture()).isEqualTo(colour.texture());
		assertThat(depth.shaders()).isEqualTo(colour.shaders());
		assertThat(depth.depthTest()).isEqualTo(colour.depthTest());

		assertThat(depth.transparency()).isEqualTo(Transparency.TRANSLUCENT);
	}

	@Test
	void nothingElseGetsOne() {
		assertThat(GltfMaterial.depthPassFor(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false).toFlywheel()))
				.isNull();
		assertThat(GltfMaterial.depthPassFor(new GltfMaterial(A, AlphaMode.MASK, 0.5f, false).toFlywheel()))
				.isNull();

		assertThat(GltfMaterial.depthPassFor(new GltfMaterial(A, AlphaMode.BLEND, 0.0f, false).toFlywheel()))
				.isNull();

		assertThat(GltfMaterial.depthPassFor(GltfMaterial.depthPassFor(sortedBlend()))).isNull();
	}

	@Test
	void maskCutsOutAndStaysOpaque() {
		Material material = new GltfMaterial(A, AlphaMode.MASK, 0.5f, false).toFlywheel();

		assertThat(material.transparency()).isEqualTo(Transparency.OPAQUE);
		assertThat(material.cutout()).isEqualTo(CutoutShaders.HALF);
	}

	@Test
	void doubleSidedIsBackfaceCullingInverted() {
		assertThat(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, true).toFlywheel()
				.backfaceCulling()).isFalse();
		assertThat(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false).toFlywheel()
				.backfaceCulling()).isTrue();
	}

	@Test
	void anUntexturedMaterialNamesNoTexture() {
		Material material = new GltfMaterial(GltfMaterial.UNTEXTURED, AlphaMode.OPAQUE, 0.0f, false)
				.toFlywheel();

		assertThat(material.texture()).isNotEqualTo(GltfMaterial.UNTEXTURED);
	}

	@Test
	void aCutoffLandsOnTheNearestShaderFlywheelHas() {
		assertThat(GltfMaterial.quantiseCutoff(0.5f)).isEqualTo(0.5f);
		assertThat(GltfMaterial.quantiseCutoff(0.1f)).isEqualTo(0.1f);
		assertThat(GltfMaterial.quantiseCutoff(0.0f)).isEqualTo(0.0f);

		assertThat(GltfMaterial.quantiseCutoff(0.44f)).isEqualTo(0.5f);
		assertThat(GltfMaterial.quantiseCutoff(0.9f)).isEqualTo(0.5f);
		assertThat(GltfMaterial.quantiseCutoff(0.04f)).isEqualTo(0.0f);
	}

	@Test
	void aTieKeepsTheFragment() {
		assertThat(0.1f - 0.05f).isEqualTo(0.05f - 0.0f);
		assertThat(GltfMaterial.quantiseCutoff(0.05f)).isEqualTo(0.0f);

		assertThat(0.5f - 0.3f).isLessThan(0.3f - 0.1f);
		assertThat(GltfMaterial.quantiseCutoff(0.3f)).isEqualTo(0.5f);
	}

	@Test
	void materialsDrawnDifferentlyDoNotMerge() {
		GltfMaterial opaque = new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false);

		assertThat(opaque).isNotEqualTo(new GltfMaterial(B, AlphaMode.OPAQUE, 0.0f, false));
		assertThat(opaque).isNotEqualTo(new GltfMaterial(A, AlphaMode.BLEND, 0.0f, false));
		assertThat(opaque).isNotEqualTo(new GltfMaterial(A, AlphaMode.MASK, 0.5f, false));
		assertThat(opaque).isNotEqualTo(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, true));
	}

	@Test
	void materialsDrawnIdenticallyMerge() {
		assertThat(new GltfMaterial(A, AlphaMode.MASK, 0.44f, false))
				.isEqualTo(new GltfMaterial(A, AlphaMode.MASK, 0.55f, false));

		assertThat(new GltfMaterial(A, AlphaMode.OPAQUE, 0.5f, false))
				.isEqualTo(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false));
		assertThat(new GltfMaterial(A, AlphaMode.BLEND, 0.5f, false))
				.isEqualTo(new GltfMaterial(A, AlphaMode.BLEND, 0.0f, false));
	}

	@Test
	void swappingInAnAtlasPageKeepsEverythingElse() {
		GltfMaterial masked = new GltfMaterial(A, AlphaMode.MASK, 0.5f, true);
		GltfMaterial atlased = masked.onTexture(B, false);

		assertThat(atlased.texture()).isEqualTo(B);
		assertThat(atlased.alphaMode()).isEqualTo(masked.alphaMode());
		assertThat(atlased.alphaCutoff()).isEqualTo(masked.alphaCutoff());
		assertThat(atlased.doubleSided()).isEqualTo(masked.doubleSided());
	}

	@Test
	void pbrNeedsABandedSheet() {
		GltfMaterial wantsPbr = new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false, true);
		assertThat(wantsPbr.onTexture(B, true)
				.pbr()).isTrue();
		assertThat(wantsPbr.onTexture(B, false)
				.pbr()).isFalse();

		assertThat(new GltfMaterial(A, AlphaMode.OPAQUE, 0.0f, false).onTexture(B, true)
				.pbr()).isFalse();
	}

	@Test
	void theGlassAssetDeclaresAllThreeAlphaModes() {
		GltfModel gltf = read();

		assertThat(mode(gltf, MATERIAL_GLASS)).isEqualTo(MaterialModelV2.AlphaMode.BLEND);
		assertThat(mode(gltf, MATERIAL_STEEL)).isEqualTo(MaterialModelV2.AlphaMode.OPAQUE);
		assertThat(mode(gltf, MATERIAL_GRILLE)).isEqualTo(MaterialModelV2.AlphaMode.MASK);

		MaterialModelV2 grille = (MaterialModelV2) gltf.getMaterialModels()
				.get(MATERIAL_GRILLE);
		assertThat(grille.getAlphaCutoff()).isEqualTo(0.5f);
		assertThat(grille.isDoubleSided()).isTrue();
	}

	@Test
	void theGlassAssetMergesToThreeMeshesNotOneAndNotFour() {
		GltfModel gltf = read();
		Set<GltfMaterial> distinct = new LinkedHashSet<>();
		int primitives = 0;
		for (NodeModel node : gltf.getNodeModels()) {
			for (MeshModel mesh : node.getMeshModels()) {
				for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
					primitives++;
					MaterialModelV2 v2 = (MaterialModelV2) primitive.getMaterialModel();
					distinct.add(new GltfMaterial(A, switch (v2.getAlphaMode()) {
						case MASK -> AlphaMode.MASK;
						case BLEND -> AlphaMode.BLEND;
						default -> AlphaMode.OPAQUE;
					}, v2.getAlphaCutoff(), v2.isDoubleSided()));
				}
			}
		}

		assertThat(primitives).isEqualTo(4);
		assertThat(distinct).hasSize(3);
	}

	private static MaterialModelV2.AlphaMode mode(GltfModel gltf, int index) {
		return ((MaterialModelV2) gltf.getMaterialModels()
				.get(index)).getAlphaMode();
	}

	private static GltfModel read() {
		try (InputStream in = GltfMaterialTest.class.getClassLoader()
				.getResourceAsStream(ASSET)) {
			if (in == null) {
				throw new AssertionError(ASSET + " is not on the test classpath; "
						+ "run scripts/make-glass-asset.py");
			}
			return new GltfModelReader().readWithoutReferences(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
