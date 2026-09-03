package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.texture.SpriteUv;

class MeshGeometryTest {
	private static MeshGeometry column() {
		return MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
				RigFixture.columnSkinning(), SpriteUv.IDENTITY, 0);
	}

	private static MeshGeometry flag() {
		return MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_FLAG),
				RigFixture.flagSkinning(), SpriteUv.IDENTITY, 0);
	}

	@Test
	@DisplayName("a merge is exactly the two meshes, end to end")
	void concatKeepsEveryVertexAndIndex() {
		MeshGeometry column = column();
		MeshGeometry flag = flag();
		MeshGeometry merged = MeshGeometry.concat(List.of(column, flag));

		assertThat(merged.vertexCount()).isEqualTo(column.vertexCount() + flag.vertexCount());
		assertThat(merged.indexCount()).isEqualTo(column.indexCount() + flag.indexCount());

		for (int v = 0; v < column.vertexCount(); v++) {
			assertThat(merged.position(v, 1)).isEqualTo(column.position(v, 1));
			assertThat(merged.texCoord(v, 0)).isEqualTo(column.texCoord(v, 0));
		}
		for (int v = 0; v < flag.vertexCount(); v++) {
			int moved = column.vertexCount() + v;
			assertThat(merged.position(moved, 1)).isEqualTo(flag.position(v, 1));
			assertThat(merged.texCoord(moved, 0)).isEqualTo(flag.texCoord(v, 0));
		}
	}

	@Test
	@DisplayName("a three-way merge is exactly the three meshes, end to end")
	void concatOfThreeKeepsEveryPart() {

		MeshGeometry column = column();
		MeshGeometry flag = flag();
		MeshGeometry merged = MeshGeometry.concat(List.of(column, flag, column));

		assertThat(merged.vertexCount())
				.isEqualTo(2 * column.vertexCount() + flag.vertexCount());
		assertThat(merged.indexCount()).isEqualTo(2 * column.indexCount() + flag.indexCount());

		int third = column.vertexCount() + flag.vertexCount();
		for (int v = 0; v < column.vertexCount(); v++) {
			assertThat(merged.position(third + v, 1)).isEqualTo(column.position(v, 1));
			assertThat(merged.packedJoints(third + v)).isEqualTo(column.packedJoints(v));
		}

		int base = column.indexCount() + flag.indexCount();
		for (int i = 0; i < column.indexCount(); i++) {
			assertThat(merged.index(base + i)).isEqualTo(column.index(i) + third);
		}
	}

	@Test
	@DisplayName("the second mesh's triangles are rebased onto its own vertices")
	void concatRebasesIndices() {

		MeshGeometry column = column();
		MeshGeometry flag = flag();
		MeshGeometry merged = MeshGeometry.concat(List.of(column, flag));

		for (int i = 0; i < column.indexCount(); i++) {
			assertThat(merged.index(i)).isEqualTo(column.index(i));
		}
		for (int i = 0; i < flag.indexCount(); i++) {
			assertThat(merged.index(column.indexCount() + i))
					.isEqualTo(flag.index(i) + column.vertexCount());
		}

		for (int i = 0; i < merged.indexCount(); i++) {
			assertThat(merged.index(i)).isBetween(0, merged.vertexCount() - 1);
		}
		for (int i = column.indexCount(); i < merged.indexCount(); i++) {
			assertThat(merged.index(i)).isGreaterThanOrEqualTo(column.vertexCount());
		}
	}

	@Test
	@DisplayName("merged vertices keep the palette slots they had, so the parts still move apart")
	void concatPreservesTheBinding() {

		MeshGeometry column = column();
		MeshGeometry flag = flag();
		MeshGeometry merged = MeshGeometry.concat(List.of(column, flag));

		int flagSlot = RigFixture.layout()
				.nodeSlot(RigFixture.node(RigFixture.NODE_FLAG));

		for (int v = 0; v < column.vertexCount(); v++) {
			assertThat(merged.packedJoints(v)).isEqualTo(column.packedJoints(v));
			for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
				assertThat(merged.weightChannel(v, influence))
						.isEqualTo(column.weightChannel(v, influence));
			}
		}

		for (int v = 0; v < flag.vertexCount(); v++) {
			int moved = column.vertexCount() + v;
			assertThat(merged.packedJoints(moved)).isEqualTo(flag.packedJoints(v));
			assertThat(BoneAttributeCodec.unpackJoint(merged.packedJoints(moved), 0))
					.as("the flag's vertices must still name the flag's node")
					.isEqualTo(flagSlot);
		}

		assertThat(BoneAttributeCodec.unpackJoint(merged.packedJoints(0), 0))
				.as("the column is skinned, so it indexes the skin block rather than a node slot")
				.isGreaterThanOrEqualTo(RigFixture.SKIN_BASE);
	}

	@Test
	@DisplayName("merging one mesh returns it unchanged")
	void concatOfOneIsIdentity() {
		MeshGeometry column = column();
		assertThat(MeshGeometry.concat(List.of(column))).isSameAs(column);
	}

	@Test
	@DisplayName("a merged mesh's bounding sphere covers both parts")
	void concatRecomputesTheBoundingSphere() {
		MeshGeometry merged = MeshGeometry.concat(List.of(column(), flag()));

		for (int v = 0; v < merged.vertexCount(); v++) {
			float dx = merged.position(v, 0) - merged.boundingSphere()
					.x();
			float dy = merged.position(v, 1) - merged.boundingSphere()
					.y();
			float dz = merged.position(v, 2) - merged.boundingSphere()
					.z();
			assertThat(Math.sqrt(dx * dx + dy * dy + dz * dz))
					.isLessThanOrEqualTo(merged.boundingSphere()
							.w() + 1e-4);
		}
	}

	@Test
	@DisplayName("atlasing moves every coordinate onto the sprite and nowhere else")
	void atlasRemapsTexCoords() {
		SpriteUv sprite = new SpriteUv(0.25f, 0.5f, 0.25f, 0.125f);

		MeshGeometry plain = column();
		MeshGeometry atlased = MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
				RigFixture.columnSkinning(), sprite, 0);

		for (int v = 0; v < plain.vertexCount(); v++) {
			assertThat(atlased.texCoord(v, 0))
					.isCloseTo(sprite.u(plain.texCoord(v, 0)), within(1e-6f));
			assertThat(atlased.texCoord(v, 1))
					.isCloseTo(sprite.v(plain.texCoord(v, 1)), within(1e-6f));

			assertThat(atlased.texCoord(v, 0))
					.isBetween(sprite.uOffset() - 1e-5f, sprite.uOffset() + sprite.uScale() + 1e-5f);
			assertThat(atlased.texCoord(v, 1))
					.isBetween(sprite.vOffset() - 1e-5f, sprite.vOffset() + sprite.vScale() + 1e-5f);
		}
	}

	@Test
	@DisplayName("atlasing leaves nothing else about the mesh alone")
	void atlasTouchesOnlyTexCoords() {
		SpriteUv sprite = new SpriteUv(0.25f, 0.5f, 0.25f, 0.125f);

		MeshGeometry plain = column();
		MeshGeometry atlased = MeshGeometry.of(RigFixture.primitive(RigFixture.NODE_ARMATURE),
				RigFixture.columnSkinning(), sprite, 0);

		for (int v = 0; v < plain.vertexCount(); v++) {
			assertThat(atlased.position(v, 0)).isEqualTo(plain.position(v, 0));
			assertThat(atlased.normal(v, 1)).isEqualTo(plain.normal(v, 1));
			assertThat(atlased.packedJoints(v)).isEqualTo(plain.packedJoints(v));
		}
	}

	@Test
	@DisplayName("the identity sprite is a no-op")
	void identitySpriteChangesNothing() {
		assertThat(SpriteUv.IDENTITY.u(0.37f)).isEqualTo(0.37f);
		assertThat(SpriteUv.IDENTITY.v(0.37f)).isEqualTo(0.37f);
		assertThat(SpriteUv.IDENTITY.isIdentity()).isTrue();
	}
}
