package com.wf.gemrender.gltf.morph;

import java.util.List;
import java.util.Map;

import com.wf.gemrender.vendor.jgltf.model.AccessorDatas;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;

public final class MorphTargets {
	public static final int FLOATS_WITH_NORMALS = 6;

	public static final int FLOATS_POSITION_ONLY = 3;

	private final int vertexCount;
	private final int targetCount;
	private final int floatsPerDelta;

	private final float[] deltas;

	private MorphTargets(int vertexCount, int targetCount, int floatsPerDelta, float[] deltas) {
		this.vertexCount = vertexCount;
		this.targetCount = targetCount;
		this.floatsPerDelta = floatsPerDelta;
		this.deltas = deltas;
	}

	public static MorphTargets of(MeshPrimitiveModel primitive, int vertexCount) {
		List<Map<String, AccessorModel>> targets = primitive.getTargets();
		if (targets == null || targets.isEmpty()) {
			return null;
		}

		boolean normals = true;
		for (Map<String, AccessorModel> target : targets) {
			normals &= target.containsKey("NORMAL");
		}

		int floatsPerDelta = normals ? FLOATS_WITH_NORMALS : FLOATS_POSITION_ONLY;
		int targetCount = targets.size();
		float[] deltas = new float[vertexCount * targetCount * floatsPerDelta];

		for (int t = 0; t < targetCount; t++) {
			readInto(targets.get(t)
					.get("POSITION"), deltas, vertexCount, targetCount, floatsPerDelta, t, 0);
			if (normals) {
				readInto(targets.get(t)
						.get("NORMAL"), deltas, vertexCount, targetCount, floatsPerDelta, t, 3);
			}
		}

		return new MorphTargets(vertexCount, targetCount, floatsPerDelta, deltas);
	}

	public int vertexCount() {
		return vertexCount;
	}

	public int targetCount() {
		return targetCount;
	}

	public int floatsPerDelta() {
		return floatsPerDelta;
	}

	public boolean hasNormals() {
		return floatsPerDelta == FLOATS_WITH_NORMALS;
	}

	public int floatCount() {
		return deltas.length;
	}

	public float[] deltas() {
		return deltas;
	}

	public float delta(int vertex, int target, int component) {
		return deltas[(vertex * targetCount + target) * floatsPerDelta + component];
	}

	public void applyPosition(float[] weights, int vertex, float[] out) {
		for (int t = 0; t < Math.min(targetCount, weights.length); t++) {
			float weight = weights[t];
			if (weight == 0.0f) {
				continue;
			}
			for (int c = 0; c < 3; c++) {
				out[c] += weight * delta(vertex, t, c);
			}
		}
	}

	public float maxDisplacement(int vertex) {
		float total = 0.0f;
		for (int t = 0; t < targetCount; t++) {
			float x = delta(vertex, t, 0);
			float y = delta(vertex, t, 1);
			float z = delta(vertex, t, 2);
			total += (float) Math.sqrt(x * x + y * y + z * z);
		}
		return total;
	}

	private static void readInto(AccessorModel accessor, float[] deltas, int vertexCount, int targetCount,
			int floatsPerDelta, int target, int componentOffset) {
		if (accessor == null) {
			return;
		}
		if (accessor.getCount() != vertexCount) {
			throw new IllegalArgumentException("morph target " + target + " covers " + accessor.getCount()
					+ " vertices but the primitive has " + vertexCount);
		}

		AccessorFloatData data = AccessorDatas.createFloat(accessor);
		for (int v = 0; v < vertexCount; v++) {
			for (int c = 0; c < 3; c++) {
				deltas[(v * targetCount + target) * floatsPerDelta + componentOffset + c] = data.get(v, c);
			}
		}
	}
}
