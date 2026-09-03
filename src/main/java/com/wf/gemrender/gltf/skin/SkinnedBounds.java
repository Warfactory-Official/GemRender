package com.wf.gemrender.gltf.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class SkinnedBounds {
	private static final int FLOATS_PER_BOX = 6;

	private static final int CORNERS = 8;

	private final int[] slots;
	private final float[] boxes;

	private SkinnedBounds(int[] slots, float[] boxes) {
		this.slots = slots;
		this.boxes = boxes;
	}

	public int size() {
		return slots.length;
	}

	public int slot(int i) {
		return slots[i];
	}

	public void box(int i, float[] out) {
		System.arraycopy(boxes, i * FLOATS_PER_BOX, out, 0, FLOATS_PER_BOX);
	}

	public void evaluate(Matrix4fc[] palette, Vector4f out) {
		float[] posed = new float[3];

		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;

		for (int i = 0; i < slots.length; i++) {
			Matrix4fc bone = palette[slots[i]];
			for (int corner = 0; corner < CORNERS; corner++) {
				posedCorner(bone, i, corner, posed);
				minX = Math.min(minX, posed[0]);
				minY = Math.min(minY, posed[1]);
				minZ = Math.min(minZ, posed[2]);
				maxX = Math.max(maxX, posed[0]);
				maxY = Math.max(maxY, posed[1]);
				maxZ = Math.max(maxZ, posed[2]);
			}
		}

		float cx = (minX + maxX) * 0.5f;
		float cy = (minY + maxY) * 0.5f;
		float cz = (minZ + maxZ) * 0.5f;

		float radiusSq = 0.0f;
		for (int i = 0; i < slots.length; i++) {
			Matrix4fc bone = palette[slots[i]];
			for (int corner = 0; corner < CORNERS; corner++) {
				posedCorner(bone, i, corner, posed);
				float dx = posed[0] - cx;
				float dy = posed[1] - cy;
				float dz = posed[2] - cz;
				radiusSq = Math.max(radiusSq, dx * dx + dy * dy + dz * dz);
			}
		}

		out.set(cx, cy, cz, (float) Math.sqrt(radiusSq));
	}

	private void posedCorner(Matrix4fc bone, int i, int corner, float[] out) {
		int b = i * FLOATS_PER_BOX;

		float x = boxes[b + ((corner & 1) != 0 ? 3 : 0)];
		float y = boxes[b + 1 + ((corner & 2) != 0 ? 3 : 0)];
		float z = boxes[b + 2 + ((corner & 4) != 0 ? 3 : 0)];

		out[0] = bone.m00() * x + bone.m10() * y + bone.m20() * z + bone.m30();
		out[1] = bone.m01() * x + bone.m11() * y + bone.m21() * z + bone.m31();
		out[2] = bone.m02() * x + bone.m12() * y + bone.m22() * z + bone.m32();
	}

	public static final class Builder {
		private final Map<Integer, float[]> boxBySlot = new TreeMap<>();

		public Builder add(float[] positions, int vertexCount, VertexSkinning skinning) {
			return add(positions, vertexCount, skinning, null);
		}

		public Builder add(float[] positions, int vertexCount, VertexSkinning skinning, float[] morphExtent) {
			for (int v = 0; v < vertexCount; v++) {
				float x = positions[v * 3];
				float y = positions[v * 3 + 1];
				float z = positions[v * 3 + 2];
				float extent = morphExtent == null ? 0.0f : morphExtent[v];

				boolean any = false;
				for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
					if (skinning.blendWeight(v, influence) <= 0.0f) {
						continue;
					}
					grow(BoneAttributeCodec.unpackJoint(skinning.packedJoints(v), influence), x, y, z, extent);
					any = true;
				}

				if (!any) {
					grow(BoneAttributeCodec.unpackJoint(skinning.packedJoints(v), 0), x, y, z, extent);
				}
			}
			return this;
		}

		public Builder addBox(int slot, float minX, float minY, float minZ,
				float maxX, float maxY, float maxZ) {
			grow(slot, minX, minY, minZ, 0.0f);
			grow(slot, maxX, maxY, maxZ, 0.0f);
			return this;
		}

		public SkinnedBounds build() {
			if (boxBySlot.isEmpty()) {
				throw new IllegalStateException("No geometry was contributed, so nothing would ever draw: "
						+ "a model with no bounds is culled everywhere rather than nowhere.");
			}

			List<Integer> ordered = new ArrayList<>(boxBySlot.keySet());
			int[] slots = new int[ordered.size()];
			float[] boxes = new float[ordered.size() * FLOATS_PER_BOX];
			for (int i = 0; i < slots.length; i++) {
				slots[i] = ordered.get(i);
				System.arraycopy(boxBySlot.get(slots[i]), 0, boxes, i * FLOATS_PER_BOX, FLOATS_PER_BOX);
			}
			return new SkinnedBounds(slots, boxes);
		}

		private void grow(int slot, float x, float y, float z, float extent) {
			float[] box = boxBySlot.computeIfAbsent(slot, unused -> new float[] {
					Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
					Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY });

			box[0] = Math.min(box[0], x - extent);
			box[1] = Math.min(box[1], y - extent);
			box[2] = Math.min(box[2], z - extent);
			box[3] = Math.max(box[3], x + extent);
			box[4] = Math.max(box[4], y + extent);
			box[5] = Math.max(box[5], z + extent);
		}
	}
}
