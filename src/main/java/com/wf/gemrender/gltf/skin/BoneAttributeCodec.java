package com.wf.gemrender.gltf.skin;

public final class BoneAttributeCodec {
	public static final int INFLUENCES = 4;

	public static final int MAX_JOINTS = 256;

	public static final int WEIGHT_SUM = 255;

	private BoneAttributeCodec() {
	}

	public static int packJoints(int j0, int j1, int j2, int j3) {
		checkJoint(j0);
		checkJoint(j1);
		checkJoint(j2);
		checkJoint(j3);
		return (j0 & 0xFF) | ((j1 & 0xFF) << 8) | ((j2 & 0xFF) << 16) | ((j3 & 0xFF) << 24);
	}

	public static int unpackJoint(int packed, int influence) {
		if (influence < 0 || influence >= INFLUENCES) {
			throw new IllegalArgumentException("influence out of range: " + influence);
		}
		return (packed >>> (influence * 8)) & 0xFF;
	}

	public static void quantizeWeights(float[] weights, int offset, int[] out) {
		if (out.length < INFLUENCES) {
			throw new IllegalArgumentException("out must hold at least " + INFLUENCES + " entries");
		}

		float[] clamped = new float[INFLUENCES];
		float sum = 0.0f;
		for (int i = 0; i < INFLUENCES; i++) {
			float w = weights[offset + i];

			clamped[i] = w > 0.0f ? w : 0.0f;
			sum += clamped[i];
		}

		if (sum <= 0.0f) {
			out[0] = WEIGHT_SUM;
			out[1] = 0;
			out[2] = 0;
			out[3] = 0;
			return;
		}

		float[] fraction = new float[INFLUENCES];
		int total = 0;
		for (int i = 0; i < INFLUENCES; i++) {
			float scaled = clamped[i] / sum * WEIGHT_SUM;
			int floor = (int) scaled;
			out[i] = floor;
			fraction[i] = scaled - floor;
			total += floor;
		}

		for (int remaining = WEIGHT_SUM - total; remaining > 0; remaining--) {
			int best = 0;
			for (int i = 1; i < INFLUENCES; i++) {
				if (fraction[i] > fraction[best]) {
					best = i;
				}
			}
			out[best]++;
			fraction[best] = -1.0f;
		}
	}

	public static float weightChannel(int quantized) {
		if (quantized < 0 || quantized > 255) {
			throw new IllegalArgumentException("quantized weight out of range: " + quantized);
		}
		return (quantized + 0.5f) / 255.0f;
	}

	public static float decodeWeight(int quantized) {
		return quantized / (float) WEIGHT_SUM;
	}

	public static int packNormU8(float f) {
		float clamped = f < 0.0f ? 0.0f : (f > 1.0f ? 1.0f : f);
		return (int) (clamped * 255) & 0xFF;
	}

	private static void checkJoint(int joint) {
		if (joint < 0 || joint >= MAX_JOINTS) {
			throw new IllegalArgumentException(
					"joint index " + joint + " out of range [0, " + MAX_JOINTS + "). "
							+ "Split the mesh, or remap it onto a smaller joint set.");
		}
	}
}
