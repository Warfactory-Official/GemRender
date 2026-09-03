package com.wf.gemrender.gltf;

import java.util.List;

import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.SpriteUv;
import com.wf.gemrender.vendor.jgltf.model.AccessorData;
import com.wf.gemrender.vendor.jgltf.model.AccessorByteData;
import com.wf.gemrender.vendor.jgltf.model.AccessorDatas;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorIntData;
import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.AccessorShortData;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;

public final class MeshGeometry {
	private final int vertexCount;
	private final float[] positions;
	private final float[] normals;
	private final float[] texCoords;
	private final int[] indices;

	private final int[] packedJoints;
	private final float[] weightChannels;

	private final int[] morphSets;

	private final Vector4fc boundingSphere;

	private MeshGeometry(int vertexCount, float[] positions, float[] normals, float[] texCoords,
			int[] indices, int[] packedJoints, float[] weightChannels, int[] morphSets) {
		this.vertexCount = vertexCount;
		this.positions = positions;
		this.normals = normals;
		this.texCoords = texCoords;
		this.indices = indices;
		this.packedJoints = packedJoints;
		this.weightChannels = weightChannels;
		this.morphSets = morphSets;
		this.boundingSphere = computeBoundingSphere(positions, vertexCount);
	}

	public static MeshGeometry of(MeshPrimitiveModel primitive, VertexSkinning skinning, SpriteUv uv,
			int morphSet) {
		AccessorModel positionAccessor = require(primitive, "POSITION");
		int vertexCount = positionAccessor.getCount();

		if (skinning.vertexCount() != vertexCount) {
			throw new IllegalArgumentException("skinning covers " + skinning.vertexCount()
					+ " vertices but the primitive has " + vertexCount);
		}

		int[] packedJoints = new int[vertexCount];
		float[] weightChannels = new float[vertexCount * BoneAttributeCodec.INFLUENCES];
		for (int v = 0; v < vertexCount; v++) {
			packedJoints[v] = skinning.packedJoints(v);
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				weightChannels[v * BoneAttributeCodec.INFLUENCES + influence] =
						skinning.weightChannel(v, influence);
			}
		}

		int[] morphSets = null;
		if (morphSet != 0) {
			morphSets = new int[vertexCount];
			java.util.Arrays.fill(morphSets, morphSet);
		}

		return new MeshGeometry(vertexCount,
				read(AccessorDatas.createFloat(positionAccessor), vertexCount, 3),
				readOptional(primitive, "NORMAL", vertexCount, 3),
				atlas(readOptional(primitive, "TEXCOORD_0", vertexCount, 2), vertexCount, uv),
				readIndices(primitive, vertexCount),
				packedJoints, weightChannels, morphSets);
	}

	public static MeshGeometry of(float[] positions, float[] normals, float[] texCoords, int[] indices,
			VertexSkinning skinning, SpriteUv uv, int morphSet) {
		int vertexCount = positions.length / 3;
		if (skinning.vertexCount() != vertexCount) {
			throw new IllegalArgumentException("skinning covers " + skinning.vertexCount()
					+ " vertices but the mesh has " + vertexCount);
		}

		int[] packedJoints = new int[vertexCount];
		float[] weightChannels = new float[vertexCount * BoneAttributeCodec.INFLUENCES];
		for (int v = 0; v < vertexCount; v++) {
			packedJoints[v] = skinning.packedJoints(v);
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				weightChannels[v * BoneAttributeCodec.INFLUENCES + influence] =
						skinning.weightChannel(v, influence);
			}
		}

		int[] morphSets = null;
		if (morphSet != 0) {
			morphSets = new int[vertexCount];
			java.util.Arrays.fill(morphSets, morphSet);
		}

		return new MeshGeometry(vertexCount, positions, normals, atlas(texCoords, vertexCount, uv), indices,
				packedJoints, weightChannels, morphSets);
	}

	public static MeshGeometry concat(List<MeshGeometry> parts) {
		if (parts.size() == 1) {
			return parts.get(0);
		}

		int vertices = 0;
		int indexCount = 0;
		boolean anyNormals = false;
		boolean anyMorphs = false;
		for (MeshGeometry part : parts) {
			vertices += part.vertexCount;
			indexCount += part.indices.length;
			anyNormals |= part.normals != null;
			anyMorphs |= part.morphSets != null;
		}

		float[] positions = new float[vertices * 3];
		float[] normals = anyNormals ? new float[vertices * 3] : null;
		float[] texCoords = new float[vertices * 2];
		int[] indices = new int[indexCount];
		int[] packedJoints = new int[vertices];
		float[] weightChannels = new float[vertices * BoneAttributeCodec.INFLUENCES];
		int[] morphSets = anyMorphs ? new int[vertices] : null;

		int vertexBase = 0;
		int indexBase = 0;
		for (MeshGeometry part : parts) {
			int count = part.vertexCount;

			System.arraycopy(part.positions, 0, positions, vertexBase * 3, count * 3);
			System.arraycopy(part.texCoords, 0, texCoords, vertexBase * 2, count * 2);
			System.arraycopy(part.packedJoints, 0, packedJoints, vertexBase, count);
			System.arraycopy(part.weightChannels, 0, weightChannels,
					vertexBase * BoneAttributeCodec.INFLUENCES, count * BoneAttributeCodec.INFLUENCES);

			if (normals != null) {
				if (part.normals != null) {
					System.arraycopy(part.normals, 0, normals, vertexBase * 3, count * 3);
				} else {
					for (int v = 0; v < count; v++) {
						normals[(vertexBase + v) * 3 + 1] = 1.0f;
					}
				}
			}

			if (morphSets != null && part.morphSets != null) {
				System.arraycopy(part.morphSets, 0, morphSets, vertexBase, count);
			}

			for (int i = 0; i < part.indices.length; i++) {
				indices[indexBase + i] = part.indices[i] + vertexBase;
			}

			vertexBase += count;
			indexBase += part.indices.length;
		}

		return new MeshGeometry(vertices, positions, normals, texCoords, indices, packedJoints,
				weightChannels, morphSets);
	}

	public int vertexCount() {
		return vertexCount;
	}

	public int indexCount() {
		return indices.length;
	}

	public int index(int i) {
		return indices[i];
	}

	public float position(int vertex, int axis) {
		return positions[vertex * 3 + axis];
	}

	public boolean hasNormals() {
		return normals != null;
	}

	public float normal(int vertex, int axis) {
		return normals == null ? (axis == 1 ? 1.0f : 0.0f) : normals[vertex * 3 + axis];
	}

	public float texCoord(int vertex, int axis) {
		return texCoords[vertex * 2 + axis];
	}

	public int packedJoints(int vertex) {
		return packedJoints[vertex];
	}

	public float weightChannel(int vertex, int influence) {
		return weightChannels[vertex * BoneAttributeCodec.INFLUENCES + influence];
	}

	public int morphSet(int vertex) {
		return morphSets == null ? 0 : morphSets[vertex];
	}

	public float[] positions() {
		return positions;
	}

	public Vector4fc boundingSphere() {
		return boundingSphere;
	}

	private static AccessorModel require(MeshPrimitiveModel primitive, String attribute) {
		AccessorModel accessor = primitive.getAttributes()
				.get(attribute);
		if (accessor == null) {
			throw new IllegalArgumentException("glTF primitive has no " + attribute + " attribute");
		}
		return accessor;
	}

	private static float[] read(AccessorFloatData data, int count, int components) {
		float[] out = new float[count * components];
		for (int i = 0; i < count; i++) {
			for (int c = 0; c < components; c++) {
				out[i * components + c] = data.get(i, c);
			}
		}
		return out;
	}

	private static float[] readOptional(MeshPrimitiveModel primitive, String attribute, int count, int components) {
		AccessorModel accessor = primitive.getAttributes()
				.get(attribute);
		if (accessor == null) {
			return null;
		}
		return read(AccessorDatas.createFloat(accessor), count, components);
	}

	private static float[] atlas(float[] texCoords, int vertexCount, SpriteUv uv) {
		if (texCoords == null) {
			float[] corner = new float[vertexCount * 2];
			for (int v = 0; v < vertexCount; v++) {
				corner[v * 2] = uv.u(0.0f);
				corner[v * 2 + 1] = uv.v(0.0f);
			}
			return corner;
		}

		if (uv.isIdentity()) {
			return texCoords;
		}

		for (int v = 0; v < vertexCount; v++) {
			texCoords[v * 2] = uv.u(texCoords[v * 2]);
			texCoords[v * 2 + 1] = uv.v(texCoords[v * 2 + 1]);
		}
		return texCoords;
	}

	private static int[] readIndices(MeshPrimitiveModel primitive, int vertexCount) {
		AccessorModel accessor = primitive.getIndices();
		if (accessor == null) {
			int[] sequential = new int[vertexCount];
			for (int i = 0; i < vertexCount; i++) {
				sequential[i] = i;
			}
			return sequential;
		}

		AccessorData data = accessor.getAccessorData();
		int count = accessor.getCount();
		int[] out = new int[count];

		if (data instanceof AccessorIntData intData) {
			for (int i = 0; i < count; i++) {
				out[i] = intData.get(i, 0);
			}
		} else if (data instanceof AccessorShortData shortData) {
			for (int i = 0; i < count; i++) {
				out[i] = shortData.get(i, 0) & 0xFFFF;
			}
		} else if (data instanceof AccessorByteData byteData) {
			for (int i = 0; i < count; i++) {
				out[i] = byteData.get(i, 0) & 0xFF;
			}
		} else {
			throw new IllegalArgumentException("Unsupported glTF index component type: " + data.getClass());
		}
		return out;
	}

	private static Vector4fc computeBoundingSphere(float[] positions, int vertexCount) {
		if (vertexCount == 0) {
			return new Vector4f(0, 0, 0, 0);
		}

		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int i = 0; i < vertexCount; i++) {
			minX = Math.min(minX, positions[i * 3]);
			minY = Math.min(minY, positions[i * 3 + 1]);
			minZ = Math.min(minZ, positions[i * 3 + 2]);
			maxX = Math.max(maxX, positions[i * 3]);
			maxY = Math.max(maxY, positions[i * 3 + 1]);
			maxZ = Math.max(maxZ, positions[i * 3 + 2]);
		}

		float cx = (minX + maxX) * 0.5f;
		float cy = (minY + maxY) * 0.5f;
		float cz = (minZ + maxZ) * 0.5f;

		float radiusSq = 0.0f;
		for (int i = 0; i < vertexCount; i++) {
			float dx = positions[i * 3] - cx;
			float dy = positions[i * 3 + 1] - cy;
			float dz = positions[i * 3 + 2] - cz;
			radiusSq = Math.max(radiusSq, dx * dx + dy * dy + dz * dz);
		}

		return new Vector4f(cx, cy, cz, (float) Math.sqrt(radiusSq));
	}
}
