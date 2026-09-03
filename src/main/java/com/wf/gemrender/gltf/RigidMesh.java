package com.wf.gemrender.gltf;

import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RigidMesh implements Mesh {
	private final MeshGeometry geometry;
	private final IndexSequence indexSequence;

	public RigidMesh(MeshGeometry geometry) {
		this.geometry = geometry;
		this.indexSequence = (ptr, count) -> {
			for (int i = 0; i < count; i++) {
				MemoryUtil.memPutInt(ptr + (long) i * Integer.BYTES, geometry.index(i));
			}
		};
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

			vertexList.r(i, 1.0f);
			vertexList.g(i, 1.0f);
			vertexList.b(i, 1.0f);
			vertexList.a(i, 1.0f);

			vertexList.overlay(i, OverlayTexture.NO_OVERLAY);
			vertexList.light(i, 0);
		}
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
