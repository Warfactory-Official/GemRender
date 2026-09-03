package com.wf.gemrender.gltf.skin;

import java.util.Map;

import com.wf.gemrender.vendor.jgltf.model.AccessorByteData;
import com.wf.gemrender.vendor.jgltf.model.AccessorData;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorIntData;
import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.AccessorShortData;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;

public final class VertexSkinning {
	private static final int WEIGHTS_PER_VERTEX = BoneAttributeCodec.INFLUENCES;

	private final int[] joints;
	private final float[] weights;

	private final int uniformJoints;
	private final float[] uniformWeights;

	private final int vertexCount;
	private final int maxInfluences;

	private VertexSkinning(int vertexCount, int[] joints, float[] weights,
			int uniformJoints, float[] uniformWeights, int maxInfluences) {
		this.vertexCount = vertexCount;
		this.joints = joints;
		this.weights = weights;
		this.uniformJoints = uniformJoints;
		this.uniformWeights = uniformWeights;
		this.maxInfluences = maxInfluences;
	}

	public static VertexSkinning rigid(int vertexCount, int slot) {
		int[] quantised = new int[WEIGHTS_PER_VERTEX];
		BoneAttributeCodec.quantizeWeights(new float[] { 1.0f, 0.0f, 0.0f, 0.0f }, 0, quantised);

		float[] channels = new float[WEIGHTS_PER_VERTEX];
		for (int i = 0; i < WEIGHTS_PER_VERTEX; i++) {
			channels[i] = BoneAttributeCodec.weightChannel(quantised[i]);
		}

		return new VertexSkinning(vertexCount, null, null,
				BoneAttributeCodec.packJoints(slot, slot, slot, slot), channels, 1);
	}

	public static VertexSkinning of(MeshPrimitiveModel primitive, int vertexCount, int[] jointSlots) {
		Map<String, AccessorModel> attributes = primitive.getAttributes();
		if (!attributes.containsKey("JOINTS_0")) {
			return null;
		}

		int sets = 0;
		while (attributes.containsKey("JOINTS_" + sets) && attributes.containsKey("WEIGHTS_" + sets)) {
			sets++;
		}

		int[][] setJoints = new int[sets][];
		float[][] setWeights = new float[sets][];
		for (int s = 0; s < sets; s++) {
			setJoints[s] = readJoints(attributes.get("JOINTS_" + s), vertexCount);
			setWeights[s] = readWeights(attributes.get("WEIGHTS_" + s), vertexCount);
		}

		int[] packed = new int[vertexCount];
		float[] channels = new float[vertexCount * WEIGHTS_PER_VERTEX];

		int[] chosenJoints = new int[WEIGHTS_PER_VERTEX];
		float[] chosenWeights = new float[WEIGHTS_PER_VERTEX];
		int[] quantised = new int[WEIGHTS_PER_VERTEX];
		int maxInfluences = 0;

		for (int v = 0; v < vertexCount; v++) {
			int influences = selectTopFour(setJoints, setWeights, v, jointSlots, chosenJoints, chosenWeights);
			maxInfluences = Math.max(maxInfluences, influences);

			BoneAttributeCodec.quantizeWeights(chosenWeights, 0, quantised);
			packed[v] = BoneAttributeCodec.packJoints(
					chosenJoints[0], chosenJoints[1], chosenJoints[2], chosenJoints[3]);
			for (int i = 0; i < WEIGHTS_PER_VERTEX; i++) {
				channels[v * WEIGHTS_PER_VERTEX + i] = BoneAttributeCodec.weightChannel(quantised[i]);
			}
		}

		return new VertexSkinning(vertexCount, packed, channels, 0, null, maxInfluences);
	}

	private static int selectTopFour(int[][] setJoints, float[][] setWeights, int vertex,
			int[] jointSlots, int[] outJoints, float[] outWeights) {
		int candidates = setJoints.length * WEIGHTS_PER_VERTEX;
		int chosen = 0;

		boolean[] taken = new boolean[candidates];

		for (int slot = 0; slot < WEIGHTS_PER_VERTEX; slot++) {
			int best = -1;
			float bestWeight = 0.0f;
			for (int c = 0; c < candidates; c++) {
				if (taken[c]) {
					continue;
				}
				float w = setWeights[c / WEIGHTS_PER_VERTEX][vertex * WEIGHTS_PER_VERTEX + c % WEIGHTS_PER_VERTEX];
				if (w > bestWeight) {
					bestWeight = w;
					best = c;
				}
			}

			if (best < 0) {
				outJoints[slot] = chosen > 0 ? outJoints[0] : jointSlots[0];
				outWeights[slot] = 0.0f;
				continue;
			}

			taken[best] = true;
			int jointIndex = setJoints[best / WEIGHTS_PER_VERTEX][vertex * WEIGHTS_PER_VERTEX
					+ best % WEIGHTS_PER_VERTEX];
			if (jointIndex < 0 || jointIndex >= jointSlots.length) {
				throw new IllegalArgumentException("glTF joint index " + jointIndex + " on vertex " + vertex
						+ " is outside the skin's " + jointSlots.length + " joints");
			}

			outJoints[slot] = jointSlots[jointIndex];
			outWeights[slot] = bestWeight;
			chosen++;
		}

		return chosen;
	}

	public int packedJoints(int vertex) {
		return joints == null ? uniformJoints : joints[vertex];
	}

	public float weightChannel(int vertex, int influence) {
		return weights == null
				? uniformWeights[influence]
				: weights[vertex * WEIGHTS_PER_VERTEX + influence];
	}

	public float blendWeight(int vertex, int influence) {
		return BoneAttributeCodec.decodeWeight(
				BoneAttributeCodec.packNormU8(weightChannel(vertex, influence)));
	}

	public int vertexCount() {
		return vertexCount;
	}

	public boolean uniform() {
		return joints == null;
	}

	public int maxInfluences() {
		return maxInfluences;
	}

	private static int[] readJoints(AccessorModel accessor, int vertexCount) {
		AccessorData data = accessor.getAccessorData();
		int[] out = new int[vertexCount * WEIGHTS_PER_VERTEX];

		for (int v = 0; v < vertexCount; v++) {
			for (int c = 0; c < WEIGHTS_PER_VERTEX; c++) {
				int value;
				if (data instanceof AccessorByteData byteData) {
					value = byteData.get(v, c) & 0xFF;
				} else if (data instanceof AccessorShortData shortData) {
					value = shortData.get(v, c) & 0xFFFF;
				} else if (data instanceof AccessorIntData intData) {
					value = intData.get(v, c);
				} else {
					throw new IllegalArgumentException(
							"Unsupported JOINTS component type: " + data.getClass().getSimpleName());
				}
				out[v * WEIGHTS_PER_VERTEX + c] = value;
			}
		}
		return out;
	}

	private static float[] readWeights(AccessorModel accessor, int vertexCount) {
		AccessorData data = accessor.getAccessorData();
		float[] out = new float[vertexCount * WEIGHTS_PER_VERTEX];

		for (int v = 0; v < vertexCount; v++) {
			for (int c = 0; c < WEIGHTS_PER_VERTEX; c++) {
				float value;
				if (data instanceof AccessorFloatData floatData) {
					value = floatData.get(v, c);
				} else if (data instanceof AccessorByteData byteData) {
					value = (byteData.get(v, c) & 0xFF) / 255.0f;
				} else if (data instanceof AccessorShortData shortData) {
					value = (shortData.get(v, c) & 0xFFFF) / 65535.0f;
				} else {
					throw new IllegalArgumentException(
							"Unsupported WEIGHTS component type: " + data.getClass().getSimpleName());
				}
				out[v * WEIGHTS_PER_VERTEX + c] = value;
			}
		}
		return out;
	}
}
