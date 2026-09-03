package com.wf.gemrender.render;

public final class SkinnedCubeGeometry {
	public static final float MIN = -0.5f;
	public static final float MAX = 0.5f;

	public static final float SEAM = 0.5f;

	public static final float[][] FACES = {
			{ 0, 0, -1, MIN, 1, MIN, MAX, 1, MIN, MAX, 0, MIN, MIN, 0, MIN },

			{ 0, 0, 1, MAX, 1, MAX, MIN, 1, MAX, MIN, 0, MAX, MAX, 0, MAX },

			{ -1, 0, 0, MIN, 1, MAX, MIN, 1, MIN, MIN, 0, MIN, MIN, 0, MAX },

			{ 1, 0, 0, MAX, 1, MIN, MAX, 1, MAX, MAX, 0, MAX, MAX, 0, MIN },

			{ 0, -1, 0, MIN, 0, MIN, MAX, 0, MIN, MAX, 0, MAX, MIN, 0, MAX },

			{ 0, 1, 0, MIN, 1, MAX, MAX, 1, MAX, MAX, 1, MIN, MIN, 1, MIN },
	};

	public static final int CORNERS = 4;

	private SkinnedCubeGeometry() {
	}

	public static void corner(float[] face, int index, float[] out) {
		int p = 3 + index * 3;
		out[0] = face[p];
		out[1] = face[p + 1];
		out[2] = face[p + 2];
	}
}
