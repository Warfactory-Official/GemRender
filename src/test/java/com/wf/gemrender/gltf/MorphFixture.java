package com.wf.gemrender.gltf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.morph.MorphTargets;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.MeshModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator;

public final class MorphFixture {
	public static final String ASSET = "assets/gemrender/models/morph/morph.glb";

	public static final int NODE_PUMP = 0;
	public static final int NODE_PISTON = 1;

	public static final int BELLOWS_TARGETS = 2;

	public static final int PISTON_TARGETS = 1;

	public static final int BELLOWS_VERTICES = 5 * (8 + 1) + 2 * (1 + 8);
	public static final int PISTON_VERTICES = 24;

	public static final float CLIP_SECONDS = 2.0f;

	public static final float MID_CLIP = 1.0f;

	public static final float[] BELLOWS_WEIGHTS_AT_MID = { 1.0f, 0.5f };
	public static final float[] PISTON_WEIGHTS_AT_MID = { 0.75f };

	public static final float SQUASH = 0.5f;
	public static final float BULGE = 0.6f;
	public static final float EXTEND = 1.2f;
	public static final float BELLOWS_HEIGHT = 3.0f;

	private static final GltfModel GLTF = read();
	private static final GltfPaletteLayout LAYOUT = GltfPaletteLayout.of(GLTF);

	private MorphFixture() {
	}

	public static GltfModel gltf() {
		return GLTF;
	}

	public static GltfPaletteLayout layout() {
		return LAYOUT;
	}

	public static GltfAnimation animation() {
		return GltfAnimation.of("pump", GltfAnimationCreator.createGltfAnimation(GLTF.getAnimationModels()
				.get(0)), LAYOUT.nodeTable());
	}

	public static NodeModel node(int index) {
		return GLTF.getNodeModels()
				.get(index);
	}

	public static MeshModel mesh(int nodeIndex) {
		return node(nodeIndex).getMeshModels()
				.get(0);
	}

	public static MeshPrimitiveModel primitive(int nodeIndex) {
		return mesh(nodeIndex).getMeshPrimitiveModels()
				.get(0);
	}

	public static int vertexCount(int nodeIndex) {
		return primitive(nodeIndex).getAttributes()
				.get("POSITION")
				.getCount();
	}

	public static MorphTargets targets(int nodeIndex) {
		return MorphTargets.of(primitive(nodeIndex), vertexCount(nodeIndex));
	}

	public static float[] positions(int nodeIndex) {
		return MeshGeometry.of(primitive(nodeIndex),
				com.wf.gemrender.gltf.skin.VertexSkinning.rigid(vertexCount(nodeIndex),
						LAYOUT.nodeSlot(node(nodeIndex))),
				com.wf.gemrender.texture.SpriteUv.IDENTITY, 0)
				.positions();
	}

	public static GltfMorphLayout morphLayout() {
		MorphTargets bellows = targets(NODE_PUMP);
		MorphTargets piston = targets(NODE_PISTON);

		GltfMorphLayout.Builder builder = GltfMorphLayout.builder();
		builder.add(LAYOUT.nodeTable(), node(NODE_PUMP), mesh(NODE_PUMP), bellows, 0);
		builder.add(LAYOUT.nodeTable(), node(NODE_PISTON), mesh(NODE_PISTON), piston, bellows.floatCount());
		return builder.build();
	}

	private static GltfModel read() {
		try (InputStream in = MorphFixture.class.getClassLoader()
				.getResourceAsStream(ASSET)) {
			if (in == null) {
				throw new AssertionError(ASSET + " is not on the test classpath; "
						+ "run scripts/make-morph-asset.py");
			}
			return new GltfModelReader().readWithoutReferences(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
