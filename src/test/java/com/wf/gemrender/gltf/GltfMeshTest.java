package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.texture.SpriteUv;

import dev.engine_room.flywheel.api.model.IndexSequence;

class GltfMeshTest {
	private static GltfMesh mesh() {
		return new GltfMesh(MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
				RigFixture.columnSkinning(), SpriteUv.IDENTITY, 0));
	}

	@Test
	@DisplayName("a mesh hands out the same index sequence object every time")
	void indexSequenceIsStable() {
		GltfMesh mesh = mesh();

		assertThat(mesh.indexSequence()).isSameAs(mesh.indexSequence());
		assertThat(mesh().indexSequence()).isNotSameAs(mesh().indexSequence());
	}

	@Test
	@DisplayName("the index sequence writes the geometry's own indices")
	void indexSequenceWritesTheIndices() {
		GltfMesh mesh = mesh();
		int count = mesh.indexCount();
		long ptr = MemoryUtil.nmemAlloc((long) count * Integer.BYTES);
		try {
			IndexSequence sequence = mesh.indexSequence();
			sequence.fill(ptr, count);

			for (int i = 0; i < count; i++) {
				assertThat(MemoryUtil.memGetInt(ptr + (long) i * Integer.BYTES))
						.as("index %d", i)
						.isEqualTo(mesh.geometry()
								.index(i));
			}
		} finally {
			MemoryUtil.nmemFree(ptr);
		}
	}

	@Test
	@DisplayName("merged meshes keep their own index sequences")
	void mergedMeshesDoNotShareASequence() {
		GltfMesh one = mesh();
		GltfMesh two = new GltfMesh(MeshGeometry.concat(List.of(
				MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
						RigFixture.columnSkinning(), SpriteUv.IDENTITY, 0),
				MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_FLAG),
						RigFixture.flagSkinning(), SpriteUv.IDENTITY, 0))));

		assertThat(two.indexCount()).isGreaterThan(one.indexCount());
		assertThat(one.indexSequence()).isNotSameAs(two.indexSequence());
	}
}
