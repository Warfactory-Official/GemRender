package com.wf.gemrender.gltf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.vendor.jgltf.model.AccessorDatas;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator;

public final class RigFixture {
	public static final String ASSET = "assets/gemrender/models/rig/rig.glb";

	public static final int NODE_ARMATURE = 0;
	public static final int NODE_BONE0 = 1;
	public static final int NODE_FLAG = 5;

	public static final int NODE_COUNT = 6;
	public static final int JOINT_COUNT = 4;

	public static final int SKIN_BASE = NODE_COUNT;

	public static final float CURL_RADIANS = 0.55f;
	public static final float MID_CLIP = 1.0f;
	public static final float CLIP_SECONDS = 2.0f;

	public static final int RING_WIDTH = 9;

	private static final GltfModel GLTF = read();
	private static final GltfPaletteLayout LAYOUT = GltfPaletteLayout.of(GLTF);

	private RigFixture() {
	}

	public static GltfModel gltf() {
		return GLTF;
	}

	public static GltfPaletteLayout layout() {
		return LAYOUT;
	}

	public static GltfAnimation animation() {
		return GltfAnimation.of("curl", GltfAnimationCreator.createGltfAnimation(GLTF.getAnimationModels()
				.get(0)), LAYOUT.nodeTable());
	}

	public static NodeModel node(int index) {
		return GLTF.getNodeModels()
				.get(index);
	}

	public static MeshPrimitiveModel primitive(int nodeIndex) {
		return node(nodeIndex).getMeshModels()
				.get(0)
				.getMeshPrimitiveModels()
				.get(0);
	}

	public static int vertexCount(int nodeIndex) {
		return primitive(nodeIndex).getAttributes()
				.get("POSITION")
				.getCount();
	}

	public static float[] positions(int nodeIndex) {
		int count = vertexCount(nodeIndex);
		AccessorFloatData data = AccessorDatas.createFloat(primitive(nodeIndex).getAttributes()
				.get("POSITION"));

		float[] out = new float[count * 3];
		for (int v = 0; v < count; v++) {
			for (int c = 0; c < 3; c++) {
				out[v * 3 + c] = data.get(v, c);
			}
		}
		return out;
	}

	public static VertexSkinning columnSkinning() {
		return VertexSkinning.of(primitive(NODE_ARMATURE), vertexCount(NODE_ARMATURE),
				LAYOUT.jointSlots(LAYOUT.skins()
						.get(0)
						.skin()));
	}

	public static VertexSkinning flagSkinning() {
		return VertexSkinning.rigid(vertexCount(NODE_FLAG), LAYOUT.nodeSlot(node(NODE_FLAG)));
	}

	public static SkinnedBounds bounds() {
		return new SkinnedBounds.Builder()
				.add(positions(NODE_ARMATURE), vertexCount(NODE_ARMATURE), columnSkinning())
				.add(positions(NODE_FLAG), vertexCount(NODE_FLAG), flagSkinning())
				.build();
	}

	public static Matrix4f[] newPalette() {
		Matrix4f[] palette = new Matrix4f[LAYOUT.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		return palette;
	}

	public static Matrix4f[] pose(GltfAnimation animation, float timeSeconds) {
		Matrix4f[] palette = newPalette();
		GltfPose.evaluate(LAYOUT, animation, timeSeconds, palette);
		return palette;
	}

	public static Vector3f skin(VertexSkinning skinning, Matrix4f[] palette, int vertex, Vector3f bind) {
		Matrix4f blended = new Matrix4f().zero();
		for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
			float weight = skinning.blendWeight(vertex, influence);
			if (weight <= 0.0f) {
				continue;
			}
			int slot = BoneAttributeCodec.unpackJoint(skinning.packedJoints(vertex), influence);
			blended.add(MatrixScalar.times(palette[slot], weight));
		}
		return blended.transformPosition(new Vector3f(bind));
	}

	public static float[] posedPositions(int nodeIndex, VertexSkinning skinning, Matrix4f[] palette) {
		float[] bind = positions(nodeIndex);
		int count = vertexCount(nodeIndex);

		float[] out = new float[count * 3];
		Vector3f scratch = new Vector3f();
		for (int v = 0; v < count; v++) {
			scratch.set(bind[v * 3], bind[v * 3 + 1], bind[v * 3 + 2]);
			Vector3f posed = skin(skinning, palette, v, scratch);
			out[v * 3] = posed.x;
			out[v * 3 + 1] = posed.y;
			out[v * 3 + 2] = posed.z;
		}
		return out;
	}

	private static GltfModel read() {
		try (InputStream in = RigFixture.class.getClassLoader()
				.getResourceAsStream(ASSET)) {
			if (in == null) {
				throw new AssertionError(ASSET + " is not on the test classpath; "
						+ "run scripts/make-rig-asset.py");
			}
			return new GltfModelReader().readWithoutReferences(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
