package com.wf.gemrender.gltf;

import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;

public final class GltfMesh implements Mesh {
	private final MeshGeometry geometry;

	private final IndexSequence indexSequence;

	public GltfMesh(MeshGeometry geometry) {
		this.geometry = geometry;
		this.indexSequence = (ptr, count) -> {
			for (int i = 0; i < count; i++) {
				MemoryUtil.memPutInt(ptr + (long) i * Integer.BYTES, geometry.index(i));
			}
		};
	}

	MeshGeometry geometry() {
		return geometry;
	}

	@Override
	public int vertexCount() {
		return geometry.vertexCount();
	}

	@Override
	public void write(MutableVertexList vertexList) {
		for (int i = 0; i < geometry.vertexCount(); i++) {
			vertexList.x(i, geometry.position(i, 0));
			vertexList.y(i, geometry.position(i, 1));
			vertexList.z(i, geometry.position(i, 2));

			vertexList.normalX(i, geometry.normal(i, 0));
			vertexList.normalY(i, geometry.normal(i, 1));
			vertexList.normalZ(i, geometry.normal(i, 2));

			vertexList.u(i, geometry.texCoord(i, 0));
			vertexList.v(i, geometry.texCoord(i, 1));

			vertexList.r(i, geometry.weightChannel(i, 0));
			vertexList.g(i, geometry.weightChannel(i, 1));
			vertexList.b(i, geometry.weightChannel(i, 2));
			vertexList.a(i, geometry.weightChannel(i, 3));
			vertexList.light(i, geometry.packedJoints(i));

			vertexList.overlay(i, geometry.morphSet(i));
		}

		assert geometry.vertexCount() == 0 || BoneAttributeCodec.INFLUENCES == 4
				: "the vertex colour carries exactly four weights";
	}

	@Override
	public IndexSequence indexSequence() {
		return indexSequence;
	}

	@Override
	public int indexCount() {
		return geometry.indexCount();
	}

	@Override
	public Vector4fc boundingSphere() {
		return geometry.boundingSphere();
	}
}
