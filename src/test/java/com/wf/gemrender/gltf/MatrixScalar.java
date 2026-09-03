package com.wf.gemrender.gltf;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class MatrixScalar {
	private MatrixScalar() {
	}

	public static float maxDifference(Matrix4fc expected, Matrix4fc actual) {
		float[] a = new float[16];
		float[] b = new float[16];
		expected.get(a);
		actual.get(b);

		float worst = 0.0f;
		for (int i = 0; i < a.length; i++) {
			worst = Math.max(worst, Math.abs(a[i] - b[i]));
		}
		return worst;
	}

	public static Matrix4f times(Matrix4fc m, float scalar) {
		float[] components = new float[16];
		m.get(components);
		for (int i = 0; i < components.length; i++) {
			components[i] *= scalar;
		}
		return new Matrix4f().set(components);
	}
}
