package com.wf.gemrender.render;

import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.SkinnedBounds;

import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.model.QuadMesh;

public final class SkinnedCubeMesh implements QuadMesh {
	public static final SkinnedCubeMesh INSTANCE = new SkinnedCubeMesh();

	public static final int JOINT_LOWER = 0;

	public static final int JOINT_UPPER = 1;

	public static final int JOINT_COUNT = 2;

	private static final int VERTEX_COUNT = 24;

	private static final float MIN = SkinnedCubeGeometry.MIN;
	private static final float MAX = SkinnedCubeGeometry.MAX;

	private static final float SEAM = SkinnedCubeGeometry.SEAM;

	private static final Vector4fc BOUNDING_SPHERE =
			new Vector4f(0.0f, 0.5f, 0.0f, (float) (Math.sqrt(3) * 0.5));

	public static final SkinnedBounds BOUNDS = new SkinnedBounds.Builder()

			.addBox(JOINT_LOWER, MIN, 0.0f, MIN, MAX, 0.0f, MAX)
			.addBox(JOINT_UPPER, MIN, 1.0f, MIN, MAX, 1.0f, MAX)
			.build();

	private SkinnedCubeMesh() {
	}

	@Override
	public int vertexCount() {
		return VERTEX_COUNT;
	}

	@Override
	public Vector4fc boundingSphere() {
		return BOUNDING_SPHERE;
	}

	@Override
	public void write(MutableVertexList vertexList) {
		int v = 0;
		float[] corner = new float[3];

		for (float[] face : SkinnedCubeGeometry.FACES) {
			for (int c = 0; c < SkinnedCubeGeometry.CORNERS; c++) {
				SkinnedCubeGeometry.corner(face, c, corner);
				writeVertex(vertexList, v++, corner[0], corner[1], corner[2],
						face[0], face[1], face[2]);
			}
		}
	}

	private static void writeVertex(MutableVertexList list, int i,
			float x, float y, float z, float nx, float ny, float nz) {
		list.x(i, x);
		list.y(i, y);
		list.z(i, z);

		list.normalX(i, nx);
		list.normalY(i, ny);
		list.normalZ(i, nz);

		list.u(i, 0);
		list.v(i, 0);

		list.light(i, 0);

		int joint = y > SEAM ? JOINT_UPPER : JOINT_LOWER;

		float[] weights = { 1.0f, 0.0f, 0.0f, 0.0f };
		int[] quantised = new int[BoneAttributeCodec.INFLUENCES];
		BoneAttributeCodec.quantizeWeights(weights, 0, quantised);

		list.r(i, BoneAttributeCodec.weightChannel(quantised[0]));
		list.g(i, BoneAttributeCodec.weightChannel(quantised[1]));
		list.b(i, BoneAttributeCodec.weightChannel(quantised[2]));
		list.a(i, BoneAttributeCodec.weightChannel(quantised[3]));

		list.overlay(i, BoneAttributeCodec.packJoints(joint, 0, 0, 0));
	}
}
