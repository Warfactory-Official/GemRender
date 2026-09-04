package com.wf.gemrender.particle;

import org.joml.Vector4f;
import org.joml.Vector4fc;

import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.model.QuadMesh;

public final class ParticleQuad implements QuadMesh {
	public static final ParticleQuad INSTANCE = new ParticleQuad();

	private static final float[] X = { -0.5f, 0.5f, 0.5f, -0.5f };

	private static final float[] Y = { -0.5f, -0.5f, 0.5f, 0.5f };

	private static final float[] U = { 0.0f, 1.0f, 1.0f, 0.0f };

	private static final float[] V = { 1.0f, 1.0f, 0.0f, 0.0f };

	private static final Vector4fc BOUNDING_SPHERE = new Vector4f(0.0f, 0.0f, 0.0f, 0.70710678f);

	private ParticleQuad() {
	}

	@Override
	public int vertexCount() {
		return 4;
	}

	@Override
	public void write(MutableVertexList dst) {
		for (int i = 0; i < 4; i++) {
			dst.x(i, X[i]);
			dst.y(i, Y[i]);
			dst.z(i, 0.0f);
			dst.r(i, 1.0f);
			dst.g(i, 1.0f);
			dst.b(i, 1.0f);
			dst.a(i, 1.0f);
			dst.u(i, U[i]);
			dst.v(i, V[i]);
			dst.overlay(i, 0);
			dst.light(i, 0);
			dst.normalX(i, 0.0f);
			dst.normalY(i, 0.0f);
			dst.normalZ(i, 1.0f);
		}
	}

	@Override
	public Vector4fc boundingSphere() {
		return BOUNDING_SPHERE;
	}
}
